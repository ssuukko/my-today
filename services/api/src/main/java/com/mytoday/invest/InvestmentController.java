package com.mytoday.invest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/invest")
public class InvestmentController {
    private final JdbcClient db;
    private final Clock clock;
    public InvestmentController(JdbcClient db, Clock clock) { this.db=db; this.clock=clock; }
    record InstrumentRow(UUID id,String exchange,String symbol,String name,String currency,BigDecimal price,OffsetDateTime quotedAt) {}
    record TradeRow(UUID id,UUID instrumentId,UUID requestId,String side,BigDecimal quantity,BigDecimal price,
        BigDecimal fees,BigDecimal tax,OffsetDateTime tradedAt,boolean voided,long sequence) {}
    public record Instrument(UUID id,String exchange,String symbol,String name,String currency,String price,
        OffsetDateTime quotedAt,String quoteType,String quantity,String remainingCost,String realizedPnl,String marketValue,String unrealizedPnl) {}
    public record Trade(UUID id,UUID instrumentId,String side,String quantity,String price,String fees,String tax,
        OffsetDateTime tradedAt,boolean voided,String realizedPnl) {}
    public record Total(String currency,String remainingCost,String realizedPnl,String marketValue,String unrealizedPnl,int unpricedCount) {}
    public record Overview(List<Instrument> instruments,List<Trade> trades,List<Total> totals) {}
    public record InstrumentInput(@NotBlank @Size(max=20) String exchange,@NotBlank @Size(max=20) String symbol,
        @NotBlank @Size(max=100) String name,@NotNull @Pattern(regexp="KRW|USD|JPY") String currency) {}
    public record QuoteInput(@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=14,fraction=6) BigDecimal price,
        @NotNull OffsetDateTime quotedAt) {}
    public record TradeInput(@NotNull UUID requestId,@NotNull UUID instrumentId,@NotNull @Pattern(regexp="BUY|SELL") String side,
        @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=14,fraction=6) BigDecimal quantity,
        @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=14,fraction=6) BigDecimal price,
        @DecimalMin("0") @Digits(integer=14,fraction=6) BigDecimal fees,@DecimalMin("0") @Digits(integer=14,fraction=6) BigDecimal tax,
        @NotNull OffsetDateTime tradedAt) {}
    private static final String IC="id,exchange,symbol,name,currency,price,quoted_at";
    private static final String TC="id,instrument_id,request_id,side,quantity,price,fees,tax,traded_at,voided,sequence";

    @GetMapping @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    Overview overview(Authentication auth) {
        UUID owner=owner(auth);
        List<InstrumentRow> rows=db.sql("SELECT "+IC+" FROM investment_instrument WHERE user_id=:o ORDER BY created_at,id")
            .param("o",owner).query(InstrumentRow.class).list();
        List<TradeRow> trades=trades(owner);
        Map<UUID,Position> positions=positions(trades);
        List<Instrument> instruments=rows.stream().map(r->instrument(r,positions.getOrDefault(r.id(),new Position()))).toList();
        Map<String,List<Instrument>> byCurrency=new TreeMap<>();
        instruments.forEach(i->byCurrency.computeIfAbsent(i.currency(),k->new ArrayList<>()).add(i));
        List<Total> totals=new ArrayList<>();
        byCurrency.forEach((currency,items)->{
            BigDecimal cost=BigDecimal.ZERO,realized=BigDecimal.ZERO,value=BigDecimal.ZERO; int unpriced=0;
            for(Instrument i:items) {
                cost=cost.add(new BigDecimal(i.remainingCost())); realized=realized.add(new BigDecimal(i.realizedPnl()));
                if(i.marketValue()==null) unpriced++; else value=value.add(new BigDecimal(i.marketValue()));
            }
            totals.add(new Total(currency,s(cost),s(realized),unpriced>0?null:s(value),unpriced>0?null:s(value.subtract(cost)),unpriced));
        });
        Map<UUID,BigDecimal> realized=realizedByTrade(trades);
        List<Trade> recent=trades.stream().sorted(Comparator.comparingLong(TradeRow::sequence).reversed()).limit(200)
            .map(t->trade(t,realized.getOrDefault(t.id(),BigDecimal.ZERO))).toList();
        return new Overview(instruments,recent,totals);
    }

    @PostMapping("/instruments") @ResponseStatus(HttpStatus.CREATED) @Transactional
    Instrument createInstrument(Authentication auth,@Valid @RequestBody InstrumentInput b) {
        UUID o=owner(auth); lock(o);
        if(db.sql("SELECT count(*) FROM investment_instrument WHERE user_id=:o").param("o",o).query(Long.class).single()>=100)
            throw conflict("관심종목은 최대 100개까지 등록할 수 있습니다.");
        String exchange=b.exchange().trim().toUpperCase(Locale.ROOT),symbol=b.symbol().trim().toUpperCase(Locale.ROOT);
        if(!exchange.matches("[A-Z0-9._-]{1,20}")||!symbol.matches("[A-Z0-9._-]{1,20}")) throw bad("거래소와 종목 코드를 확인해 주세요.");
        if(db.sql("SELECT EXISTS(SELECT 1 FROM investment_instrument WHERE user_id=:o AND exchange=:e AND symbol=:s)")
            .param("o",o).param("e",exchange).param("s",symbol).query(Boolean.class).single()) throw conflict("이미 등록한 종목입니다.");
        UUID id=UUID.randomUUID();
        db.sql("INSERT INTO investment_instrument(id,user_id,exchange,symbol,name,currency) VALUES(:id,:o,:e,:s,:n,:c)")
            .param("id",id).param("o",o).param("e",exchange).param("s",symbol).param("n",b.name().trim()).param("c",b.currency()).update();
        return instrument(find(o,id),new Position());
    }

    @DeleteMapping("/instruments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    void deleteInstrument(Authentication auth,@PathVariable UUID id) {
        UUID o=owner(auth); lock(o);find(o,id);
        if(db.sql("SELECT EXISTS(SELECT 1 FROM investment_trade WHERE user_id=:o AND instrument_id=:id)").param("o",o).param("id",id).query(Boolean.class).single())
            throw conflict("거래 이력이 있는 종목은 삭제할 수 없습니다.");
        db.sql("DELETE FROM investment_instrument WHERE user_id=:o AND id=:id").param("o",o).param("id",id).update();
    }

    @PutMapping("/instruments/{id}/quote") @Transactional
    Instrument quote(Authentication auth,@PathVariable UUID id,@Valid @RequestBody QuoteInput b) {
        UUID o=owner(auth);lock(o); InstrumentRow current=find(o,id);validateTime(b.quotedAt());
        if(current.quotedAt()!=null && b.quotedAt().isBefore(current.quotedAt())) throw conflict("현재 가격보다 이전 시점의 가격입니다.");
        db.sql("UPDATE investment_instrument SET price=:p,quoted_at=:at WHERE user_id=:o AND id=:id")
            .param("p",b.price()).param("at",b.quotedAt()).param("o",o).param("id",id).update();
        return instrument(find(o,id),positions(trades(o)).getOrDefault(id,new Position()));
    }

    @PostMapping("/trades") @ResponseStatus(HttpStatus.CREATED) @Transactional
    Trade createTrade(Authentication auth,@Valid @RequestBody TradeInput b) {
        UUID o=owner(auth);lock(o);
        BigDecimal fees=zero(b.fees()),tax=zero(b.tax());
        List<TradeRow> history=trades(o);
        var previous=history.stream().filter(t->t.requestId().equals(b.requestId())).findFirst();
        if(previous.isPresent()) {
            TradeRow t=previous.get();
            if(!t.instrumentId().equals(b.instrumentId())||!t.side().equals(b.side())||t.quantity().compareTo(b.quantity())!=0
                ||t.price().compareTo(b.price())!=0||t.fees().compareTo(fees)!=0||t.tax().compareTo(tax)!=0||!t.tradedAt().isEqual(b.tradedAt()))
                throw conflict("같은 요청 번호에 다른 거래를 저장할 수 없습니다.");
            return trade(t,realizedByTrade(history).getOrDefault(t.id(),BigDecimal.ZERO));
        }
        find(o,b.instrumentId());validateTime(b.tradedAt());
        List<TradeRow> active=history.stream().filter(t->t.instrumentId().equals(b.instrumentId())&&!t.voided()).toList();
        if(!active.isEmpty() && b.tradedAt().isBefore(active.getLast().tradedAt())) throw conflict("마지막 거래 이후 시각으로 기록해 주세요.");
        Position position=positions(active).getOrDefault(b.instrumentId(),new Position());
        if(b.side().equals("SELL")) {
            if(position.quantity.compareTo(b.quantity())<0) throw conflict("보유 수량보다 많이 매도할 수 없습니다.");
            if(b.quantity().multiply(b.price()).subtract(fees).subtract(tax).signum()<0) throw bad("수수료와 세금이 매도 금액보다 큽니다.");
        }
        UUID id=UUID.randomUUID();
        db.sql("INSERT INTO investment_trade(id,user_id,instrument_id,request_id,side,quantity,price,fees,tax,traded_at) VALUES(:id,:o,:i,:r,:s,:q,:p,:f,:t,:at)")
            .param("id",id).param("o",o).param("i",b.instrumentId()).param("r",b.requestId()).param("s",b.side()).param("q",b.quantity())
            .param("p",b.price()).param("f",fees).param("t",tax).param("at",b.tradedAt()).update();
        TradeRow t=findTrade(o,id);return trade(t,position.apply(t));
    }

    @PostMapping("/trades/{id}/void") @Transactional
    Trade voidTrade(Authentication auth,@PathVariable UUID id) {
        UUID o=owner(auth);lock(o);TradeRow t=findTrade(o,id);
        if(!t.voided()) {
            long latest=db.sql("SELECT max(sequence) FROM investment_trade WHERE user_id=:o AND instrument_id=:i AND NOT voided")
                .param("o",o).param("i",t.instrumentId()).query(Long.class).single();
            if(latest!=t.sequence()) throw conflict("가장 최근 거래부터 취소해 주세요.");
            db.sql("UPDATE investment_trade SET voided=true WHERE user_id=:o AND id=:id").param("o",o).param("id",id).update();
        }
        return trade(findTrade(o,id),BigDecimal.ZERO);
    }

    private InstrumentRow find(UUID o,UUID id) { return db.sql("SELECT "+IC+" FROM investment_instrument WHERE user_id=:o AND id=:id")
        .param("o",o).param("id",id).query(InstrumentRow.class).optional().orElseThrow(InvestmentController::missing); }
    private TradeRow findTrade(UUID o,UUID id) { return db.sql("SELECT "+TC+" FROM investment_trade WHERE user_id=:o AND id=:id")
        .param("o",o).param("id",id).query(TradeRow.class).optional().orElseThrow(InvestmentController::missing); }
    // shortcut: recalculate from immutable trade history; add position snapshots when portfolios have large histories.
    private List<TradeRow> trades(UUID o) { return db.sql("SELECT "+TC+" FROM investment_trade WHERE user_id=:o ORDER BY sequence")
        .param("o",o).query(TradeRow.class).list(); }
    private void lock(UUID o) { db.sql("SELECT id FROM app_user WHERE id=:o FOR UPDATE").param("o",o).query(UUID.class).single(); }
    private void validateTime(OffsetDateTime at) {
        if(at.getYear()<1900 || at.isAfter(OffsetDateTime.now(clock).plusMinutes(5))) throw bad("기록 시각은 1900년 이후부터 현재까지여야 합니다.");
    }
    private static Map<UUID,Position> positions(List<TradeRow> trades) {
        Map<UUID,Position> result=new HashMap<>();
        trades.stream().filter(t->!t.voided()).forEach(t->result.computeIfAbsent(t.instrumentId(),i->new Position()).apply(t));return result;
    }
    private static Map<UUID,BigDecimal> realizedByTrade(List<TradeRow> trades) {
        Map<UUID,Position> positions=new HashMap<>();Map<UUID,BigDecimal> result=new HashMap<>();
        for(TradeRow t:trades) if(!t.voided()) result.put(t.id(),positions.computeIfAbsent(t.instrumentId(),i->new Position()).apply(t));
        return result;
    }
    private static class Position {
        BigDecimal quantity=BigDecimal.ZERO,cost=BigDecimal.ZERO,realized=BigDecimal.ZERO;
        BigDecimal apply(TradeRow t) {
            BigDecimal pnl=BigDecimal.ZERO;
            if(t.side().equals("BUY")) {quantity=quantity.add(t.quantity());cost=cost.add(t.quantity().multiply(t.price())).add(t.fees()).add(t.tax());}
            else {
                BigDecimal allocated=t.quantity().compareTo(quantity)==0?cost:cost.multiply(t.quantity()).divide(quantity,12,RoundingMode.HALF_EVEN);
                pnl=t.quantity().multiply(t.price()).subtract(t.fees()).subtract(t.tax()).subtract(allocated);
                quantity=quantity.subtract(t.quantity());cost=cost.subtract(allocated);realized=realized.add(pnl);
            }
            return pnl;
        }
    }
    private static Instrument instrument(InstrumentRow r,Position p) {
        BigDecimal value=p.quantity.signum()==0?BigDecimal.ZERO:r.price()==null?null:p.quantity.multiply(r.price());
        return new Instrument(r.id(),r.exchange(),r.symbol(),r.name(),r.currency(),r.price()==null?null:s(r.price()),r.quotedAt(),
            r.price()==null?"UNKNOWN":"MANUAL",s(p.quantity),s(p.cost),s(p.realized),value==null?null:s(value),value==null?null:s(value.subtract(p.cost)));
    }
    private static Trade trade(TradeRow t,BigDecimal realized) { return new Trade(t.id(),t.instrumentId(),t.side(),s(t.quantity()),s(t.price()),s(t.fees()),s(t.tax()),t.tradedAt(),t.voided(),s(realized)); }
    private static String s(BigDecimal n) { return n.stripTrailingZeros().toPlainString(); }
    private static BigDecimal zero(BigDecimal n) { return n==null?BigDecimal.ZERO:n; }
    private static UUID owner(Authentication a) { return UUID.fromString(a.getName()); }
    private static ResponseStatusException bad(String m) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    private static ResponseStatusException conflict(String m) {return new ResponseStatusException(HttpStatus.CONFLICT,m);}
    private static ResponseStatusException missing() {return new ResponseStatusException(HttpStatus.NOT_FOUND,"투자 기록을 찾을 수 없습니다.");}
}
