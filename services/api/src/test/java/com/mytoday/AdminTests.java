package com.mytoday;

import com.mytoday.identity.AdminBootstrap;
import jakarta.validation.Validator;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
class AdminTests {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.7-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);
        r.add("spring.datasource.username",postgres::getUsername);
        r.add("spring.datasource.password",postgres::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcClient db;
    @Autowired PasswordEncoder encoder;
    @Autowired Validator validator;
    @Autowired PlatformTransactionManager transactions;
    private static final String PASSWORD = "admin-tests-password";

    @BeforeEach void clearFixtures() { db.sql("TRUNCATE calendar_event,task,app_user CASCADE").update(); }

    UUID seed(String email,String role) {
        UUID id=UUID.randomUUID();
        db.sql("INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(:id,:email,:hash,'테스트',:role)")
            .param("id",id).param("email",email).param("hash",encoder.encode(PASSWORD)).param("role",role).update();
        return id;
    }
    MockHttpSession login(String email,String role)throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
            .content("{\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value(role))
            .andReturn().getRequest().getSession();
    }
    void bootstrap(String email,String password) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> new AdminBootstrap(db,encoder,validator,email,password).run(null));
    }

    @Test void signupCannotChooseAdminAndRoleConstraintIsEnforced()throws Exception {
        mvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType("application/json")
            .content("{\"email\":\"signup@example.test\",\"password\":\""+PASSWORD+"\",\"passwordConfirmation\":\""+PASSWORD+"\",\"displayName\":\"신규\",\"inviteToken\":\"test-invite-token\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isCreated());
        var session=login("signup@example.test","USER");
        mvc.perform(patch("/api/v1/users/me/settings").session(session).with(csrf()).contentType("application/json")
            .content("{\"displayName\":\"신규\",\"timezone\":\"Asia/Seoul\",\"baseCurrency\":\"KRW\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("USER"));
        assertEquals("USER",db.sql("SELECT role FROM app_user WHERE email='signup@example.test'").query(String.class).single());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.sql("UPDATE app_user SET role='OWNER'").update());
    }

    @Test void adminBoundaryChecksCurrentDatabaseRoleAndHidesSecrets()throws Exception {
        UUID admin=seed("admin@example.test","ADMIN");
        seed("user@example.test","USER");
        var adminSession=login("admin@example.test","ADMIN");
        var userSession=login("user@example.test","USER");
        for(String path:new String[]{"/api/v1/admin/users","/api/v1/admin/invitation"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(authentication(new AnonymousAuthenticationToken("fixture",admin.toString(),AuthorityUtils.createAuthorityList("ROLE_ADMIN")))))
                .andExpect(status().isUnauthorized());
            mvc.perform(get(path).session(userSession)).andExpect(status().isForbidden());
            mvc.perform(get(path).with(user(UUID.randomUUID().toString()).roles("ADMIN"))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/admin/users").session(adminSession).param("size","1"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.nextCursor").value("1"))
            .andExpect(content().string(not(containsString("password"))));
        mvc.perform(get("/api/v1/admin/users").session(adminSession).param("size","1").param("cursor","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].email").value("user@example.test"))
            .andExpect(jsonPath("$.nextCursor").isEmpty());
        mvc.perform(get("/api/v1/admin/invitation").session(adminSession))
            .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.inviteToken").value("test-invite-token"));
        for(String size:new String[]{"0","101","bad"})
            mvc.perform(get("/api/v1/admin/users").session(adminSession).param("size",size)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/users").session(adminSession).param("cursor","-1")).andExpect(status().isBadRequest());
        db.sql("UPDATE app_user SET role='USER' WHERE id=:id").param("id",admin).update();
        mvc.perform(get("/api/v1/admin/users").session(adminSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/session").session(adminSession)).andExpect(jsonPath("$.user.role").value("USER"));
        db.sql("DELETE FROM app_user WHERE id=:id").param("id",admin).update();
        mvc.perform(get("/api/v1/admin/invitation").session(adminSession)).andExpect(status().isForbidden());
    }

    @Test void adminCannotReadAnotherUsersPlanningRecords()throws Exception {
        UUID admin=seed("admin@example.test","ADMIN"), owner=seed("owner@example.test","USER"), task=UUID.randomUUID();
        db.sql("INSERT INTO task(id,user_id,title) VALUES(:id,:owner,'개인 기록')").param("id",task).param("owner",owner).update();
        var session=login("admin@example.test","ADMIN");
        mvc.perform(get("/api/v1/tasks").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/tasks/"+task).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/users").session(session)).andExpect(status().isOk()).andExpect(content().string(not(containsString("개인 기록"))));
        assertNotEquals(admin,owner);
    }

    @Test void bootstrapIsOptInValidatedAndNeverPromotesExistingUser() {
        bootstrap("","");
        assertEquals(0,db.sql("SELECT count(*) FROM app_user").query(Integer.class).single());
        for(String[] input:new String[][]{{"admin@example.test",""},{"",PASSWORD},{"not-email",PASSWORD},{"admin@example.test","short"},{"admin@example.test","가".repeat(25)}})
            assertThrows(IllegalStateException.class,()->bootstrap(input[0],input[1]));
        UUID id=seed("taken@example.test","USER");
        assertThrows(IllegalStateException.class,()->bootstrap("taken@example.test",PASSWORD));
        assertEquals("USER",db.sql("SELECT role FROM app_user WHERE id=:id").param("id",id).query(String.class).single());
    }

    @Test void bootstrapCreatesOnceWithoutResettingAnyCredentials() {
        bootstrap(" Admin@Example.Test ",PASSWORD);
        String hash=db.sql("SELECT password_hash FROM app_user WHERE role='ADMIN'").query(String.class).single();
        assertTrue(encoder.matches(PASSWORD,hash));
        assertNotEquals(PASSWORD,hash);
        assertEquals("admin@example.test",db.sql("SELECT email FROM app_user WHERE role='ADMIN'").query(String.class).single());
        assertFalse(db.sql("SELECT onboarding_completed FROM app_user WHERE role='ADMIN'").query(Boolean.class).single());
        bootstrap("admin@example.test","a-different-password");
        bootstrap("another@example.test","a-different-password");
        assertEquals(1,db.sql("SELECT count(*) FROM app_user").query(Integer.class).single());
        assertEquals(hash,db.sql("SELECT password_hash FROM app_user WHERE role='ADMIN'").query(String.class).single());
    }

    @Test void concurrentBootstrapCreatesOnlyOneAdmin()throws Exception {
        var pool=Executors.newFixedThreadPool(2);
        var start=new CountDownLatch(1);
        try {
            Future<?> first=pool.submit(()->{await(start);bootstrap("one-admin@example.test",PASSWORD);});
            Future<?> second=pool.submit(()->{await(start);bootstrap("two-admin@example.test",PASSWORD);});
            start.countDown();
            first.get(20,TimeUnit.SECONDS);second.get(20,TimeUnit.SECONDS);
            assertEquals(1,db.sql("SELECT count(*) FROM app_user WHERE role='ADMIN'").query(Integer.class).single());
        } finally {pool.shutdownNow();}
    }
    private static void await(CountDownLatch start) {
        try {start.await();}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}
    }
}
