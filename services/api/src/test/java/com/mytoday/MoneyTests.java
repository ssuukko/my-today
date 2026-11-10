package com.mytoday;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
class MoneyTests {
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.7-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl); r.add("spring.datasource.username",postgres::getUsername); r.add("spring.datasource.password",postgres::getPassword);
    }
    @Autowired MockMvc mvc; @Autowired JdbcClient db;
    UUID owner,other;
    @BeforeEach void users() {
        owner=UUID.randomUUID(); other=UUID.randomUUID();
        for(UUID id:new UUID[]{owner,other}) db.sql("INSERT INTO app_user(id,email,password_hash,display_name) VALUES(:id,:email,'unused','원장 테스트')")
            .param("id",id).param("email",id+"@example.test").update();
    }
    MockHttpServletRequestBuilder own(MockHttpServletRequestBuilder req) { return req.with(user(owner.toString())).with(csrf()); }
    String create(String route,String body) throws Exception { return mvc.perform(own(post("/api/v1/money"+route)).contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(); }
    String account(String type,String currency,String opening) throws Exception { return JsonPath.read(create("/accounts","{\"name\":\"계좌\",\"type\":\""+type+"\",\"currency\":\""+currency+"\",\"openingBalance\":\""+opening+"\"}"),"$.id"); }
    String tx(UUID request,String type,String account,String target,String amount,String category) {
        return "{\"requestId\":\""+request+"\",\"type\":\""+type+"\",\"accountId\":\""+account+"\",\"amount\":\""+amount+"\",\"transactionDate\":\"2020-01-15\""+
            (target==null?"":",\"targetAccountId\":\""+target+"\"")+(category==null?"":",\"category\":\""+category+"\"")+"}";
    }
    String overview() throws Exception { return mvc.perform(own(get("/api/v1/money").param("month","2020-01"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(); }
    BigDecimal balance(String account) { return db.sql("SELECT a.opening_balance+COALESCE(sum(p.amount),0) FROM money_account a LEFT JOIN money_posting p ON a.id=p.account_id WHERE a.id=:id GROUP BY a.id")
        .param("id",UUID.fromString(account)).query(BigDecimal.class).single(); }

    @Test void preciseBalancedLedgerIdempotencyAndReversal() throws Exception {
        String account=account("CHECKING","USD","10.10"); UUID request=UUID.randomUUID();
        String body=tx(request,"EXPENSE",account,null,"0.100001","카페"); String created=create("/transactions",body); String id=JsonPath.read(created,"$.id");
        assertEquals(id,JsonPath.read(create("/transactions",body),"$.id"));
        assertEquals(0,new BigDecimal("9.999999").compareTo(balance(account)));
        mvc.perform(own(post("/api/v1/money/transactions")).contentType("application/json").content(body.replace("0.100001","0.2"))).andExpect(status().isConflict());
        assertEquals(2,db.sql("SELECT count(*) FROM money_posting WHERE transaction_id=:id").param("id",UUID.fromString(id)).query(Integer.class).single());
        assertEquals(0,db.sql("SELECT sum(amount) FROM money_posting WHERE transaction_id=:id").param("id",UUID.fromString(id)).query(BigDecimal.class).single().signum());
        String report=overview(); assertEquals("USD",JsonPath.read(report,"$.totals[0].currency")); assertEquals(0,new BigDecimal("0.100001").compareTo(new BigDecimal((String)JsonPath.read(report,"$.totals[0].expense"))));
        for(int i=0;i<2;i++)mvc.perform(own(post("/api/v1/money/transactions/"+id+"/reverse"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VOID"));
        assertEquals(0,new BigDecimal("10.10").compareTo(balance(account)));
        assertEquals(2,((java.util.List<?>)JsonPath.read(overview(),"$.transactions")).size());
        assertEquals(0,((java.util.List<?>)JsonPath.read(overview(),"$.totals")).size());
    }
    @Test void transferAndCardPaymentNeverDoubleCountExpenseAndCurrenciesRemainSeparate() throws Exception {
        String bank=account("CHECKING","KRW","100000"),card=account("CREDIT_CARD_LIABILITY","KRW","0"),usd=account("CASH","USD","0");
        create("/transactions",tx(UUID.randomUUID(),"EXPENSE",card,null,"12000","식비"));
        assertEquals(0,new BigDecimal("-12000").compareTo(balance(card)));
        create("/transactions",tx(UUID.randomUUID(),"TRANSFER",bank,card,"12000",null));
        create("/transactions",tx(UUID.randomUUID(),"INCOME",usd,null,"1.25","급여"));
        assertEquals(0,balance(card).signum()); assertEquals(0,new BigDecimal("88000").compareTo(balance(bank)));
        String report=overview(); assertEquals(2,((java.util.List<?>)JsonPath.read(report,"$.totals")).size());
        assertEquals(0,new BigDecimal("12000").compareTo(new BigDecimal((String)JsonPath.read(report,"$.totals[0].expense"))));
        String invalid=tx(UUID.randomUUID(),"TRANSFER",bank,usd,"100",null);
        mvc.perform(own(post("/api/v1/money/transactions")).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        assertEquals(3,db.sql("SELECT count(*) FROM money_transaction WHERE user_id=:owner").param("owner",owner).query(Integer.class).single());
    }
    @Test void ownershipCsrfAndValidationAreEnforced() throws Exception {
        mvc.perform(get("/api/v1/money")).andExpect(status().isUnauthorized());
        String account=account("CASH","KRW","100"); String body=tx(UUID.randomUUID(),"EXPENSE",account,null,"1","식비");
        mvc.perform(post("/api/v1/money/transactions").with(user(owner.toString())).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/money/transactions").with(user(other.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isNotFound());
        String id=JsonPath.read(create("/transactions",body),"$.id");
        mvc.perform(post("/api/v1/money/transactions/"+id+"/reverse").with(user(other.toString())).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/money").with(user(other.toString()))).andExpect(jsonPath("$.accounts.length()").value(0)).andExpect(jsonPath("$.transactions.length()").value(0));
        for(String amount:new String[]{"0","-1","0.0000001","100000000000000","NaN"})
            mvc.perform(own(post("/api/v1/money/transactions")).contentType("application/json").content(tx(UUID.randomUUID(),"EXPENSE",account,null,amount,"식비"))).andExpect(status().isBadRequest());
        mvc.perform(own(post("/api/v1/money/transactions")).contentType("application/json").content(body.replace("2020-01-15",LocalDate.now().plusDays(2).toString()).replace((String)JsonPath.read(body,"$.requestId"),UUID.randomUUID().toString()))).andExpect(status().isBadRequest());
        for(String month:new String[]{"2020-13","invalid","2020-1","0000-01"})mvc.perform(own(get("/api/v1/money").param("month",month))).andExpect(status().isBadRequest());
    }
    @Test void budgetUsesExactCategoryPostedExpensesAndRecomputesOnRefund() throws Exception {
        String account=account("CASH","KRW","500"); String body="{\"month\":\"2020-01\",\"category\":\"식비\",\"currency\":\"KRW\",\"amount\":\"100\"}";
        mvc.perform(own(put("/api/v1/money/budgets")).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        String expense=JsonPath.read(create("/transactions",tx(UUID.randomUUID(),"EXPENSE",account,null,"120","식비")),"$.id");
        create("/transactions",tx(UUID.randomUUID(),"INCOME",account,null,"1000","식비"));
        assertEquals(0,new BigDecimal("-20").compareTo(new BigDecimal((String)JsonPath.read(overview(),"$.budgets[0].remaining"))));
        mvc.perform(own(put("/api/v1/money/budgets")).contentType("application/json").content(body)).andExpect(status().isConflict());
        String edit=body.substring(0,body.length()-1)+",\"version\":0}";
        mvc.perform(own(put("/api/v1/money/budgets")).contentType("application/json").content(edit)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(own(put("/api/v1/money/budgets")).contentType("application/json").content(edit)).andExpect(status().isConflict());
        mvc.perform(own(post("/api/v1/money/transactions/"+expense+"/reverse"))).andExpect(status().isOk());
        assertEquals(0,new BigDecimal((String)JsonPath.read(overview(),"$.budgets[0].spent")).signum());
    }
}
