package com.mytoday;

import com.jayway.jsonpath.JsonPath;
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
class InvestmentTests {
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.7-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
    }
    @Autowired MockMvc mvc; @Autowired JdbcClient db;
    UUID owner,other;
    @BeforeEach void accounts() {
        owner=UUID.randomUUID();other=UUID.randomUUID();
        for(UUID id:new UUID[]{owner,other})db.sql("INSERT INTO app_user(id,email,password_hash,display_name) VALUES(:id,:email,'unused','투자 테스트')")
            .param("id",id).param("email",id+"@example.test").update();
    }
    MockHttpServletRequestBuilder asOwner(MockHttpServletRequestBuilder r){return r.with(user(owner.toString())).with(csrf());}
    String create(String route,String body)throws Exception{return mvc.perform(asOwner(post("/api/v1/invest"+route)).contentType("application/json").content(body))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();}
    String instrument(String symbol,String currency)throws Exception{return JsonPath.read(create("/instruments","{\"exchange\":\"NASDAQ\",\"symbol\":\""+symbol+"\",\"name\":\"종목\",\"currency\":\""+currency+"\"}"),"$.id");}
    String tradeBody(String request,String id,String side,String quantity,String price,String fees,String tax,String time){return "{\"requestId\":\""+request+"\",\"instrumentId\":\""+id+"\",\"side\":\""+side+"\",\"quantity\":\""+quantity+"\",\"price\":\""+price+"\",\"fees\":\""+fees+"\",\"tax\":\""+tax+"\",\"tradedAt\":\""+time+"\"}";}
    String buy(String id,String q,String p,String fees,String time)throws Exception{return create("/trades",tradeBody(UUID.randomUUID().toString(),id,"BUY",q,p,fees,"0",time));}
    String overview()throws Exception{return mvc.perform(asOwner(get("/api/v1/invest"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();}
    void value(String json,String path,String expected){assertEquals(expected,JsonPath.read(json,path));}

    @Test void averageCostFeesTaxesQuotesAndCurrencyTotals()throws Exception{
        String id=instrument("AAPL","USD");
        buy(id,"2","100","2","2026-01-01T10:00:00Z");
        buy(id,"2","200","2","2026-01-02T10:00:00Z");
        String missing=overview();value(missing,"$.instruments[0].quantity","4");value(missing,"$.instruments[0].remainingCost","604");
        assertNull(JsonPath.read(missing,"$.instruments[0].marketValue"));assertNull(JsonPath.read(missing,"$.totals[0].marketValue"));
        String sale=create("/trades",tradeBody(UUID.randomUUID().toString(),id,"SELL","1","180","1","2","2026-01-03T10:00:00Z"));
        value(sale,"$.realizedPnl","26");
        mvc.perform(asOwner(put("/api/v1/invest/instruments/"+id+"/quote")).contentType("application/json")
            .content("{\"price\":\"170\",\"quotedAt\":\"2026-01-04T10:00:00Z\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.quoteType").value("MANUAL")).andExpect(jsonPath("$.marketValue").value("510"))
            .andExpect(jsonPath("$.unrealizedPnl").value("57")).andExpect(jsonPath("$.remainingCost").value("453"));
        String yen=instrument("JPTEST","JPY");buy(yen,"1","1000","0","2026-01-01T10:00:00Z");
        String result=overview();assertEquals(2,(int)JsonPath.read(result,"$.totals.length()"));
        value(result,"$.totals[0].currency","JPY");assertNull(JsonPath.read(result,"$.totals[0].marketValue"));
        value(result,"$.totals[1].currency","USD");value(result,"$.totals[1].marketValue","510");value(result,"$.totals[1].realizedPnl","26");
    }

    @Test void tradeIdempotencyOversellOrderAndLatestOnlyVoid()throws Exception{
        String id=instrument("ONE","KRW"),request=UUID.randomUUID().toString();
        String body=tradeBody(request,id,"BUY","3","1","0","0","2026-01-01T10:00:00Z");
        String first=create("/trades",body),repeat=create("/trades",body);value(repeat,"$.id",JsonPath.read(first,"$.id"));
        mvc.perform(asOwner(post("/api/v1/invest/trades")).contentType("application/json").content(body.replace("\"quantity\":\"3\"","\"quantity\":\"4\"")))
            .andExpect(status().isConflict());
        for(String invalid:new String[]{tradeBody(UUID.randomUUID().toString(),id,"SELL","4","1","0","0","2026-01-02T10:00:00Z"),
            tradeBody(UUID.randomUUID().toString(),id,"BUY","1","1","0","0","2025-01-02T10:00:00Z")})
            mvc.perform(asOwner(post("/api/v1/invest/trades")).contentType("application/json").content(invalid)).andExpect(status().isConflict());
        String last=create("/trades",tradeBody(UUID.randomUUID().toString(),id,"SELL","1","2","0","0","2026-01-02T10:00:00Z"));
        mvc.perform(asOwner(post("/api/v1/invest/trades/"+JsonPath.read(first,"$.id")+"/void"))).andExpect(status().isConflict());
        for(int n=0;n<2;n++)mvc.perform(asOwner(post("/api/v1/invest/trades/"+JsonPath.read(last,"$.id")+"/void"))).andExpect(status().isOk()).andExpect(jsonPath("$.voided").value(true));
        value(overview(),"$.instruments[0].quantity","3");value(overview(),"$.instruments[0].realizedPnl","0");
        mvc.perform(asOwner(delete("/api/v1/invest/instruments/"+id))).andExpect(status().isConflict());
        String empty=instrument("EMPTY","USD");mvc.perform(asOwner(delete("/api/v1/invest/instruments/"+empty))).andExpect(status().isNoContent());
    }

    @Test void ownershipCsrfAndValidation()throws Exception{
        mvc.perform(get("/api/v1/invest")).andExpect(status().isUnauthorized());
        String id=instrument("PRIVATE","USD");String trade=buy(id,"1","10","0","2026-01-01T10:00:00Z");
        mvc.perform(get("/api/v1/invest").with(user(other.toString()).roles("ADMIN"))).andExpect(jsonPath("$.instruments.length()").value(0));
        for(MockHttpServletRequestBuilder r:new MockHttpServletRequestBuilder[]{delete("/api/v1/invest/instruments/"+id),post("/api/v1/invest/trades/"+JsonPath.read(trade,"$.id")+"/void"),
            put("/api/v1/invest/instruments/"+id+"/quote").contentType("application/json").content("{\"price\":\"10\",\"quotedAt\":\"2026-01-01T10:00:00Z\"}")})
            mvc.perform(r.with(user(other.toString())).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/invest/trades").with(user(owner.toString())).contentType("application/json")
            .content(tradeBody(UUID.randomUUID().toString(),id,"BUY","1","10","0","0","2026-01-02T10:00:00Z"))).andExpect(status().isForbidden());
        for(String quantity:new String[]{"0","-1","0.0000001","100000000000000"})mvc.perform(asOwner(post("/api/v1/invest/trades")).contentType("application/json")
            .content(tradeBody(UUID.randomUUID().toString(),id,"BUY",quantity,"1","0","0","2026-01-02T10:00:00Z"))).andExpect(status().isBadRequest());
        mvc.perform(asOwner(post("/api/v1/invest/trades")).contentType("application/json")
            .content(tradeBody(UUID.randomUUID().toString(),id,"SELL","1","1","2","0","2026-01-02T10:00:00Z"))).andExpect(status().isBadRequest());
        mvc.perform(asOwner(post("/api/v1/invest/instruments")).contentType("application/json")
            .content("{\"exchange\":\"NASDAQ\",\"symbol\":\"PRIVATE\",\"name\":\"중복\",\"currency\":\"USD\"}")).andExpect(status().isConflict());
    }

    @Test void fractionPrecisionAndFinalSaleRemoveAllCost()throws Exception{
        String id=instrument("FRACTION","USD");buy(id,"3","0.000001","0.000001","2026-01-01T00:00:00Z");
        create("/trades",tradeBody(UUID.randomUUID().toString(),id,"SELL","1","0.000002","0","0","2026-01-02T00:00:00Z"));
        create("/trades",tradeBody(UUID.randomUUID().toString(),id,"SELL","2","0.000002","0","0","2026-01-03T00:00:00Z"));
        String json=overview();value(json,"$.instruments[0].quantity","0");value(json,"$.instruments[0].remainingCost","0");
        value(json,"$.instruments[0].realizedPnl","0.000002");value(json,"$.instruments[0].marketValue","0");
        assertEquals(3L,db.sql("SELECT count(*) FROM investment_trade WHERE user_id=:o").param("o",owner).query(Long.class).single());
    }

    @Test void quoteCannotBeFutureOrOlderAndUnpricedEmptyHasZeroValue()throws Exception{
        String id=instrument("PRICE","JPY");value(overview(),"$.instruments[0].marketValue","0");
        mvc.perform(asOwner(put("/api/v1/invest/instruments/"+id+"/quote")).contentType("application/json")
            .content("{\"price\":\"10.123456\",\"quotedAt\":\"2026-01-01T10:00:00Z\"}")).andExpect(status().isOk());
        mvc.perform(asOwner(put("/api/v1/invest/instruments/"+id+"/quote")).contentType("application/json")
            .content("{\"price\":\"9\",\"quotedAt\":\"2025-01-01T10:00:00Z\"}")).andExpect(status().isConflict());
        mvc.perform(asOwner(put("/api/v1/invest/instruments/"+id+"/quote")).contentType("application/json")
            .content("{\"price\":\"9\",\"quotedAt\":\"2099-01-01T10:00:00Z\"}")).andExpect(status().isBadRequest());
    }
    @Test void concurrentSalesCannotOversell()throws Exception{
        String id=instrument("CONCURRENT","USD");buy(id,"1","10","0","2026-01-01T00:00:00Z");
        var gate=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.List<java.util.concurrent.Future<Integer>> results=new java.util.ArrayList<>();
            for(int n=0;n<2;n++)results.add(pool.submit(()->{
                String body=tradeBody(UUID.randomUUID().toString(),id,"SELL","1","11","0","0","2026-01-02T00:00:00Z");
                gate.await();
                return mvc.perform(asOwner(post("/api/v1/invest/trades")).contentType("application/json").content(body)).andReturn().getResponse().getStatus();
            }));
            gate.countDown();java.util.List<Integer> statuses=new java.util.ArrayList<>();
            for(var result:results)statuses.add(result.get(15,java.util.concurrent.TimeUnit.SECONDS));
            statuses.sort(Integer::compareTo);assertEquals(java.util.List.of(201,409),statuses);
        }
        value(overview(),"$.instruments[0].quantity","0");value(overview(),"$.instruments[0].realizedPnl","1");
    }

}
