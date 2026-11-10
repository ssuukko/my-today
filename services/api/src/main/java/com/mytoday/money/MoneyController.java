package com.mytoday.money;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/money")
public class MoneyController {
    private final JdbcClient db;
    private final Clock clock;
    public MoneyController(JdbcClient db, Clock clock) { this.db=db; this.clock=clock; }
    public record Account(UUID id,String name,String type,String currency,String openingBalance,String balance) {}
    public record Transaction(UUID id,UUID requestId,String type,UUID accountId,UUID targetAccountId,String amount,String currency,
            String category,LocalDate transactionDate,String memo,String status,UUID reversalOf) {}
    public record Budget(UUID id,String category,String currency,String amount,String spent,String remaining,long version) {}
    public record Total(String currency,String income,String expense,String net) {}
    public record Overview(String month,List<Account> accounts,List<Transaction> transactions,List<Total> totals,List<Budget> budgets,boolean hasMore) {}
    public record AccountInput(@NotBlank @Size(max=100) String name,
            @NotNull @Pattern(regexp="CASH|CHECKING|SAVINGS|CREDIT_CARD_LIABILITY|LOAN_LIABILITY|BROKER_CASH|OTHER_ASSET") String type,
            @NotNull @Pattern(regexp="KRW|USD|JPY") String currency,@NotNull @Pattern(regexp="-?[0-9]{1,14}(\\.[0-9]{1,6})?") String openingBalance) {}
    public record TransactionInput(@NotNull UUID requestId,@NotNull @Pattern(regexp="INCOME|EXPENSE|TRANSFER") String type,
            @NotNull UUID accountId,UUID targetAccountId,@NotNull @Pattern(regexp="[0-9]{1,14}(\\.[0-9]{1,6})?") String amount,
            @Size(max=60) String category,@NotNull LocalDate transactionDate,@Size(max=1000) String memo) {}
    public record BudgetInput(@NotNull String month,@NotBlank @Size(max=60) String category,
            @NotNull @Pattern(regexp="KRW|USD|JPY") String currency,
            @NotNull @Pattern(regexp="[0-9]{1,14}(\\.[0-9]{1,6})?") String amount,@PositiveOrZero Long version) {}
    private static final String TX_COLUMNS="id,request_id,type,account_id,target_account_id,amount::text AS amount,currency,category,transaction_date,memo,status,reversal_of";

    @GetMapping @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    Overview overview(Authentication auth,@RequestParam(required=false) String month) {
        UUID owner=owner(auth); YearMonth period=month==null?YearMonth.from(today(owner)):month(month);
        var accounts=accounts(owner);
        var transactions=db.sql("SELECT "+TX_COLUMNS+" FROM money_transaction WHERE user_id=:owner AND transaction_date>=:start AND transaction_date<:end ORDER BY transaction_date DESC,created_at DESC,id LIMIT 201")
            .param("owner",owner).param("start",period.atDay(1)).param("end",period.plusMonths(1).atDay(1)).query(Transaction.class).list();
        var totals=db.sql("SELECT currency,COALESCE(sum(amount) FILTER(WHERE type='INCOME'),0)::text AS income,COALESCE(sum(amount) FILTER(WHERE type='EXPENSE'),0)::text AS expense,"
            +"(COALESCE(sum(amount) FILTER(WHERE type='INCOME'),0)-COALESCE(sum(amount) FILTER(WHERE type='EXPENSE'),0))::text AS net "
            +"FROM money_transaction WHERE user_id=:owner AND status='POSTED' AND type IN ('INCOME','EXPENSE') AND transaction_date>=:start AND transaction_date<:end GROUP BY currency ORDER BY currency")
            .param("owner",owner).param("start",period.atDay(1)).param("end",period.plusMonths(1).atDay(1)).query(Total.class).list();
        return new Overview(period.toString(),accounts,transactions.stream().limit(200).toList(),totals,budgets(owner,period),transactions.size()>200);
    }
    @PostMapping("/accounts") @ResponseStatus(HttpStatus.CREATED) @Transactional
    Account createAccount(Authentication auth,@Valid @RequestBody AccountInput b) {
        UUID owner=owner(auth); lockOwner(owner);
        if(db.sql("SELECT count(*) FROM money_account WHERE user_id=:owner").param("owner",owner).query(Long.class).single()>=100) throw bad("계좌는 최대 100개까지 만들 수 있습니다.");
        BigDecimal opening=new BigDecimal(b.openingBalance());
        if(b.type().endsWith("LIABILITY")?opening.signum()>0:opening.signum()<0) throw bad("자산의 시작 잔액은 0 이상, 부채는 0 이하로 입력해 주세요.");
        UUID id=UUID.randomUUID();
        db.sql("INSERT INTO money_account(id,user_id,name,type,currency,opening_balance) VALUES(:id,:owner,:name,:type,:currency,:opening)")
            .param("id",id).param("owner",owner).param("name",b.name().trim()).param("type",b.type()).param("currency",b.currency()).param("opening",opening).update();
        return accounts(owner).stream().filter(a->a.id().equals(id)).findFirst().orElseThrow();
    }
    @PostMapping("/transactions") @ResponseStatus(HttpStatus.CREATED) @Transactional
    Transaction createTransaction(Authentication auth,@Valid @RequestBody TransactionInput b) {
        UUID owner=owner(auth); lockOwner(owner);
        BigDecimal amount=new BigDecimal(b.amount());
        if(amount.signum()<=0) throw bad("금액은 0보다 커야 합니다.");
        boolean transfer="TRANSFER".equals(b.type());
        if(transfer?(b.targetAccountId()==null||b.accountId().equals(b.targetAccountId())||b.category()!=null):
                (b.targetAccountId()!=null||b.category()==null||b.category().isBlank())) throw bad("거래 계좌와 분류를 확인해 주세요.");
        String category=b.category()==null?null:b.category().trim();
        var prior=db.sql("SELECT "+TX_COLUMNS+" FROM money_transaction WHERE user_id=:owner AND request_id=:request")
            .param("owner",owner).param("request",b.requestId()).query(Transaction.class).optional();
        if(prior.isPresent()) {
            var p=prior.get();
            if(!p.type().equals(b.type())||!p.accountId().equals(b.accountId())||!Objects.equals(p.targetAccountId(),b.targetAccountId())||new BigDecimal(p.amount()).compareTo(amount)!=0
                ||!Objects.equals(p.category(),category)||!p.transactionDate().equals(b.transactionDate())||!Objects.equals(p.memo(),b.memo())) throw conflict("같은 요청 번호로 다른 거래를 저장할 수 없습니다.");
            return p;
        }
        if(b.transactionDate().getYear()<1 || b.transactionDate().getYear()>9999) throw bad("거래 날짜를 확인해 주세요.");
        if(b.transactionDate().isAfter(today(owner))) throw bad("실제 거래 날짜는 오늘 이후일 수 없습니다.");
        var locked=lockAccounts(owner,b.accountId(),b.targetAccountId());
        String currency=locked.get(b.accountId());
        if(transfer&&!currency.equals(locked.get(b.targetAccountId()))) throw bad("다른 통화의 계좌 간 이체는 지원하지 않습니다.");
        UUID id=UUID.randomUUID();
        db.sql("INSERT INTO money_transaction(id,user_id,request_id,type,account_id,target_account_id,amount,currency,category,transaction_date,memo) VALUES(:id,:owner,:request,:type,:account,:target,:amount,:currency,:category,:date,:memo)")
            .param("id",id).param("owner",owner).param("request",b.requestId()).param("type",b.type()).param("account",b.accountId())
            .param("target",b.targetAccountId(),Types.OTHER).param("amount",amount).param("currency",currency).param("category",category,Types.VARCHAR)
            .param("date",b.transactionDate()).param("memo",b.memo(),Types.VARCHAR).update();
        BigDecimal delta="INCOME".equals(b.type())?amount:amount.negate();
        posting(id,b.accountId(),currency,delta); posting(id,transfer?b.targetAccountId():null,currency,delta.negate());
        checkBalanced(id);
        return transaction(owner,id);
    }
    @PostMapping("/transactions/{id}/reverse") @Transactional
    Transaction reverse(Authentication auth,@PathVariable UUID id) {
        UUID owner=owner(auth); lockOwner(owner); Transaction original=transaction(owner,id);
        if(original.type().equals("REVERSAL")) throw bad("취소 기록은 다시 취소할 수 없습니다.");
        if(original.status().equals("VOID")) return original;
        lockAccounts(owner,original.accountId(),original.targetAccountId());
        UUID reversal=UUID.randomUUID();
        db.sql("INSERT INTO money_transaction(id,user_id,request_id,type,account_id,target_account_id,amount,currency,category,transaction_date,memo,reversal_of) SELECT :reversal,user_id,:request,'REVERSAL',account_id,target_account_id,amount,currency,category,transaction_date,'전체 취소 / 환불',id FROM money_transaction WHERE id=:id AND user_id=:owner")
            .param("reversal",reversal).param("request",UUID.randomUUID()).param("id",id).param("owner",owner).update();
        db.sql("INSERT INTO money_posting(id,transaction_id,account_id,currency,amount) SELECT gen_random_uuid(),:reversal,account_id,currency,-amount FROM money_posting WHERE transaction_id=:id")
            .param("reversal",reversal).param("id",id).update();
        checkBalanced(reversal);
        db.sql("UPDATE money_transaction SET status='VOID' WHERE id=:id AND user_id=:owner").param("id",id).param("owner",owner).update();
        return transaction(owner,id);
    }
    @PutMapping("/budgets") @Transactional
    Budget saveBudget(Authentication auth,@Valid @RequestBody BudgetInput b) {
        UUID owner=owner(auth); lockOwner(owner); YearMonth period=month(b.month()); BigDecimal amount=new BigDecimal(b.amount());
        if(amount.signum()<=0) throw bad("예산은 0보다 커야 합니다.");
        String category=b.category().trim();
        var prior=db.sql("SELECT id,version FROM money_budget WHERE user_id=:owner AND month=:month AND category=:category AND currency=:currency")
            .param("owner",owner).param("month",period.atDay(1)).param("category",category).param("currency",b.currency()).query((rs,n)->Map.entry(rs.getObject("id",UUID.class),rs.getLong("version"))).optional();
        UUID id;
        if(prior.isEmpty()) {
            if(b.version()!=null) throw conflict("예산이 변경되었습니다. 새로 불러와 주세요.");
            if(db.sql("SELECT count(*) FROM money_budget WHERE user_id=:owner AND month=:month").param("owner",owner).param("month",period.atDay(1)).query(Long.class).single()>=100) throw bad("월별 예산은 최대 100개까지 만들 수 있습니다.");
            id=UUID.randomUUID();
            db.sql("INSERT INTO money_budget(id,user_id,month,category,currency,amount) VALUES(:id,:owner,:month,:category,:currency,:amount)")
                .param("id",id).param("owner",owner).param("month",period.atDay(1)).param("category",category).param("currency",b.currency()).param("amount",amount).update();
        } else {
            id=prior.get().getKey();
            if(!Objects.equals(b.version(),prior.get().getValue())) throw conflict("예산이 변경되었습니다. 새로 불러와 주세요.");
            db.sql("UPDATE money_budget SET amount=:amount,version=version+1 WHERE id=:id AND user_id=:owner").param("amount",amount).param("id",id).param("owner",owner).update();
        }
        UUID saved=id; return budgets(owner,period).stream().filter(x->x.id().equals(saved)).findFirst().orElseThrow();
    }
    private List<Account> accounts(UUID owner) {
        return db.sql("SELECT a.id,a.name,a.type,a.currency,a.opening_balance::text AS opening_balance,(a.opening_balance+COALESCE(sum(p.amount),0))::text AS balance FROM money_account a LEFT JOIN money_posting p ON p.account_id=a.id WHERE a.user_id=:owner GROUP BY a.id ORDER BY a.created_at,a.id LIMIT 100")
            .param("owner",owner).query(Account.class).list();
    }
    private List<Budget> budgets(UUID owner,YearMonth period) {
        return db.sql("SELECT b.id,b.category,b.currency,b.amount::text AS amount,COALESCE(sum(t.amount),0)::text AS spent,(b.amount-COALESCE(sum(t.amount),0))::text AS remaining,b.version FROM money_budget b LEFT JOIN money_transaction t ON t.user_id=b.user_id AND t.category=b.category AND t.currency=b.currency AND t.type='EXPENSE' AND t.status='POSTED' AND t.transaction_date>=:start AND t.transaction_date<:end WHERE b.user_id=:owner AND b.month=:start GROUP BY b.id ORDER BY b.category,b.currency")
            .param("owner",owner).param("start",period.atDay(1)).param("end",period.plusMonths(1).atDay(1)).query(Budget.class).list();
    }
    private Transaction transaction(UUID owner,UUID id) { return db.sql("SELECT "+TX_COLUMNS+" FROM money_transaction WHERE id=:id AND user_id=:owner")
        .param("id",id).param("owner",owner).query(Transaction.class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"거래를 찾을 수 없습니다.")); }
    private void lockOwner(UUID owner) { db.sql("SELECT id FROM app_user WHERE id=:owner FOR UPDATE").param("owner",owner).query(UUID.class).single(); }
    private Map<UUID,String> lockAccounts(UUID owner,UUID first,UUID second) {
        var ids=new ArrayList<UUID>(); ids.add(first); if(second!=null)ids.add(second); ids.sort(Comparator.naturalOrder());
        Map<UUID,String> result=new HashMap<>();
        for(UUID id:ids) result.put(id,db.sql("SELECT currency FROM money_account WHERE id=:id AND user_id=:owner FOR UPDATE").param("id",id).param("owner",owner).query(String.class).optional()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"계좌를 찾을 수 없습니다.")));
        return result;
    }
    private void posting(UUID tx,UUID account,String currency,BigDecimal amount) { db.sql("INSERT INTO money_posting(id,transaction_id,account_id,currency,amount) VALUES(:id,:tx,:account,:currency,:amount)")
        .param("id",UUID.randomUUID()).param("tx",tx).param("account",account,Types.OTHER).param("currency",currency).param("amount",amount).update(); }
    private void checkBalanced(UUID id) {
        if(db.sql("SELECT count(*) FROM (SELECT currency FROM money_posting WHERE transaction_id=:id GROUP BY currency HAVING sum(amount)<>0) p").param("id",id).query(Long.class).single()!=0) throw new IllegalStateException("Unbalanced ledger transaction");
    }
    private UUID owner(Authentication auth) { return UUID.fromString(auth.getName()); }
    private LocalDate today(UUID owner) { String zone=db.sql("SELECT timezone FROM app_user WHERE id=:owner").param("owner",owner).query(String.class).single(); return LocalDate.now(clock.withZone(ZoneId.of(zone))); }
    private static YearMonth month(String value) { try { if(!value.matches("[0-9]{4}-[0-9]{2}")) throw new DateTimeException("format"); YearMonth result=YearMonth.parse(value); if(result.getYear()<1) throw new DateTimeException("year"); return result; } catch(DateTimeException e) { throw bad("조회 월을 확인해 주세요."); } }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
