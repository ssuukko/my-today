package com.mytoday.identity;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;
@Configuration @EnableScheduling
public class SecurityConfig {
 @Bean Clock clock(){return Clock.systemUTC();}
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
 @Bean SecurityFilterChain security(HttpSecurity http,JdbcClient db)throws Exception{
  return http.authorizeHttpRequests(a->a.requestMatchers("/api/v1/auth/csrf","/api/v1/auth/login","/api/v1/auth/signup","/api/v1/auth/guest","/actuator/health","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll()
   .requestMatchers("/api/v1/admin/**").access((authentication,context)->new AuthorizationDecision(isAdmin(authentication.get(),db)))
   .anyRequest().access((authentication,context)->new AuthorizationDecision(active(authentication.get(),db))))
   .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->problem(res,401,"UNAUTHORIZED","로그인이 필요합니다."))
   .accessDeniedHandler((req,res,ex)->problem(res,403,"FORBIDDEN","요청 권한 또는 CSRF 토큰을 확인해 주세요.")))
   .logout(l->l.logoutUrl("/api/v1/auth/logout").invalidateHttpSession(true).clearAuthentication(true).deleteCookies("MT_SESSION").logoutSuccessHandler((req,res,auth)->res.setStatus(204))).build();
 }
 private static boolean isAdmin(Authentication authentication,JdbcClient db){
  if(authentication==null||!authentication.isAuthenticated()||authentication instanceof AnonymousAuthenticationToken)return false;
  UUID id;
  try{id=UUID.fromString(authentication.getName());}catch(IllegalArgumentException ex){return false;}
  return db.sql("SELECT EXISTS(SELECT FROM app_user WHERE id=:id AND role='ADMIN' AND guest_expires_at IS NULL)").param("id",id).query(Boolean.class).single();
 }
 private static boolean active(Authentication authentication,JdbcClient db){
  if(authentication==null||!authentication.isAuthenticated()||authentication instanceof AnonymousAuthenticationToken)return false;
  UUID id;
  try{id=UUID.fromString(authentication.getName());}catch(IllegalArgumentException ex){return false;}
  boolean allowed=db.sql("SELECT EXISTS(SELECT FROM app_user WHERE id=:id AND (guest_expires_at IS NULL OR guest_expires_at>CURRENT_TIMESTAMP))")
   .param("id",id).query(Boolean.class).single();
  if(!allowed)throw new InsufficientAuthenticationException("Session account expired");
  return true;
 }
 static void problem(HttpServletResponse res,int status,String code,String message)throws IOException{
  res.setStatus(status);res.setContentType("application/problem+json");res.setCharacterEncoding("UTF-8");
  res.getWriter().write("{\"type\":\"about:blank\",\"title\":\""+code+"\",\"status\":"+status+",\"code\":\""+code+"\",\"message\":\""+message+"\"}");
 }
 @Bean FilterRegistrationBean<OncePerRequestFilter> requestContext(){var filter = new OncePerRequestFilter(){
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
   String id=UUID.randomUUID().toString();MDC.put("requestId",id);res.setHeader("X-Request-ID",id);res.setHeader("Cache-Control","no-store");
   HttpSession session=req.getSession(false);
   if(session!=null&&session.getAttribute("authenticatedAt") instanceof Instant start&&start.plus(Duration.ofDays(30)).isBefore(Instant.now()))session.invalidate();
   try{chain.doFilter(req,res);}finally{MDC.remove("requestId");}
  }
 };
 var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
 registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
 return registration;
 }
}
