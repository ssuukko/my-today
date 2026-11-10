package com.mytoday;

import com.mytoday.identity.GuestCleanup;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
class GuestTests {
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.7-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);
        r.add("spring.datasource.username",postgres::getUsername);
        r.add("spring.datasource.password",postgres::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcClient db;
    @Autowired GuestCleanup cleanup;

    @Test void normalAccountsStayNormalAndGuestCreationIsRateLimited() throws Exception {
        UUID normal=UUID.randomUUID();
        db.sql("INSERT INTO app_user(id,email,password_hash,display_name,role,onboarding_completed) VALUES(:id,:email,'unused','회원','USER',true)")
            .param("id",normal).param("email",normal+"@example.test").update();
        mvc.perform(get("/api/v1/auth/session").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(normal.toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.guest").value(false));
        for(int i=0;i<10;i++) mvc.perform(post("/api/v1/auth/guest").with(csrf()).with(r->{r.setRemoteAddr("198.51.100.77");return r;}))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("USER"));
        mvc.perform(post("/api/v1/auth/guest").with(csrf()).with(r->{r.setRemoteAddr("198.51.100.77");return r;}))
            .andExpect(status().isTooManyRequests());
        assertEquals(1,db.sql("SELECT count(*) FROM app_user WHERE id=:id AND guest_expires_at IS NULL")
            .param("id",normal).query(Long.class).single());
    }

    @Test void guestHasPrivateUsableAccountAndExpiresAcrossAllApis() throws Exception {
        mvc.perform(post("/api/v1/auth/guest")).andExpect(status().isForbidden());
        var first=mvc.perform(post("/api/v1/auth/guest").with(csrf())).andExpect(status().isOk())
            .andExpect(jsonPath("$.user.role").value("USER")).andExpect(jsonPath("$.user.guest").value(true))
            .andExpect(jsonPath("$.onboardingCompleted").value(true)).andReturn();
        var second=mvc.perform(post("/api/v1/auth/guest").with(csrf())).andExpect(status().isOk()).andReturn();
        UUID a=UUID.fromString(com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(),"$.user.id"));
        UUID b=UUID.fromString(com.jayway.jsonpath.JsonPath.read(second.getResponse().getContentAsString(),"$.user.id"));
        assertNotEquals(a,b);
        MockHttpSession session=(MockHttpSession)first.getRequest().getSession();
        mvc.perform(get("/api/v1/growth").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/money").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/invest").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/tasks").session(session).with(csrf()).contentType("application/json")
            .content("{\"title\":\"게스트 기록\"}")).andExpect(status().isCreated());
        assertEquals(0,db.sql("SELECT count(*) FROM task WHERE user_id=:id").param("id",b).query(Long.class).single());
        db.sql("UPDATE app_user SET guest_expires_at=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id=:id").param("id",a).update();
        for(String path:new String[]{"/api/v1/auth/session","/api/v1/tasks","/api/v1/growth","/api/v1/money","/api/v1/invest"})
            mvc.perform(get(path).session(session)).andExpect(status().isUnauthorized());
        cleanup.purge();
        assertEquals(0,db.sql("SELECT count(*) FROM app_user WHERE id=:id").param("id",a).query(Long.class).single());
        assertEquals(0,db.sql("SELECT count(*) FROM task WHERE user_id=:id").param("id",a).query(Long.class).single());
        assertEquals(1,db.sql("SELECT count(*) FROM app_user WHERE id=:id").param("id",b).query(Long.class).single());
    }
}
