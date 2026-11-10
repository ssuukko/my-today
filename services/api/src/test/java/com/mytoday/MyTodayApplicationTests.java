package com.mytoday;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
class MyTodayApplicationTests {
 @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.7-alpine");
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){
  r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
 }
 @Autowired MockMvc mvc;
 String signup(String email){return "{\"email\":\""+email+"\",\"password\":\"long-password-123\",\"passwordConfirmation\":\"long-password-123\",\"displayName\":\"테스트\",\"inviteToken\":\"test-invite-token\"}";}
 MockHttpSession login(String email)throws Exception{
  return (MockHttpSession)mvc.perform(post("/api/v1/auth/login").with(csrf().asHeader()).contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\"long-password-123\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value(email)).andReturn().getRequest().getSession();
 }
 @Test void realCsrfHandshake()throws Exception{
  var issued=mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
  String token=com.jayway.jsonpath.JsonPath.read(issued.getResponse().getContentAsString(),"$.token");
  var session=(MockHttpSession)issued.getRequest().getSession();
  mvc.perform(post("/api/v1/auth/signup").session(session).header("X-CSRF-TOKEN",token).contentType("application/json").content(signup("csrf@example.com"))).andExpect(status().isCreated());
  mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN","invalid").contentType("application/json").content("{\"email\":\"csrf@example.com\",\"password\":\"long-password-123\"}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN",token).contentType("application/json").content("{\"email\":\"csrf@example.com\",\"password\":\"long-password-123\"}")).andExpect(status().isOk());
  mvc.perform(patch("/api/v1/users/me/settings").session(session).header("X-CSRF-TOKEN",token).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
 }
 @Test void fullLifecycleAndIsolation()throws Exception{
  mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/money/accounts")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
  mvc.perform(post("/api/v1/auth/signup").contentType("application/json").content(signup("one@example.com"))).andExpect(status().isForbidden());
  for(String email:new String[]{"one@example.com","two@example.com"}) mvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType("application/json").content(signup(email))).andExpect(status().isCreated());
  mvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType("application/json").content(signup("one@example.com"))).andExpect(status().isConflict());
  var first=login("one@example.com");var second=login("two@example.com");
  mvc.perform(patch("/api/v1/users/me/settings").session(first).with(csrf()).contentType("application/json").content("{\"displayName\":\"첫 사용자\",\"timezone\":\"Asia/Seoul\",\"baseCurrency\":\"KRW\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.onboardingCompleted").value(true));
  mvc.perform(get("/api/v1/users/me").session(second)).andExpect(jsonPath("$.onboardingCompleted").value(false)).andExpect(jsonPath("$.user.email").value("two@example.com"));
  second.setAttribute("authenticatedAt",java.time.Instant.now().minus(java.time.Duration.ofDays(31)));
  mvc.perform(get("/api/v1/auth/session").session(second)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/auth/logout").session(first).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/auth/session")).andExpect(status().isUnauthorized());
 }
 @Test void validationInviteAndThrottle()throws Exception{
  mvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType("application/json").content(signup("bad"))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType("application/json").content(signup("no@example.com").replace("test-invite-token","wrong"))).andExpect(status().isForbidden());
  for(int i=0;i<10;i++)mvc.perform(post("/api/v1/auth/login").with(csrf()).with(r->{r.setRemoteAddr("10.0.0.9");return r;}).contentType("application/json").content("{\"email\":\"missing@example.com\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/auth/login").with(csrf()).with(r->{r.setRemoteAddr("10.0.0.9");return r;}).contentType("application/json").content("{\"email\":\"missing@example.com\",\"password\":\"wrong\"}")).andExpect(status().isTooManyRequests());
 }
}
