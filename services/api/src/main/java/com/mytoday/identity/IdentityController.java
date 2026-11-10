package com.mytoday.identity;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class IdentityController {
    private final JdbcClient db;
    private final PasswordEncoder encoder;
    private final String inviteToken;
    private final LoginLimiter limiter;
    private final String dummyHash;

    public IdentityController(JdbcClient db, PasswordEncoder encoder,
            @Value("${registration.invite-token}") String inviteToken, LoginLimiter limiter) {
        this.db = db; this.encoder = encoder; this.inviteToken = inviteToken; this.limiter = limiter;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }
    public record Signup(@NotBlank @Email @Size(max=254) String email,
            @NotNull @Size(min=12,max=72) String password,
            @NotNull String passwordConfirmation, @Size(max=80) String displayName,
            @NotNull @Size(max=256) String inviteToken) {}
    public record Login(@NotBlank @Email @Size(max=254) String email,
            @NotBlank @Size(max=72) String password) {}
    public record Settings(@NotBlank @Size(max=80) String displayName,
            @NotBlank @Size(max=64) String timezone, @NotBlank @Size(min=3,max=3) String baseCurrency) {}
    public record User(UUID id, String email, String displayName, String timezone, String baseCurrency, String role) {}
    public record Session(User user, boolean onboardingCompleted) {}
    private record Account(UUID id, String email, String passwordHash, String displayName,
            String timezone, String baseCurrency, boolean onboardingCompleted, String role) {}

    @GetMapping("/auth/csrf")
    Map<String,String> csrf(CsrfToken token) { return Map.of("token", token.getToken(), "headerName", token.getHeaderName()); }

    @PostMapping("/auth/signup") @ResponseStatus(HttpStatus.CREATED)
    Map<String,UUID> signup(@Valid @RequestBody Signup body, HttpServletRequest request) {
        limiter.check("signup:" + request.getRemoteAddr());
        if (inviteToken.isBlank() || !MessageDigest.isEqual(inviteToken.getBytes(StandardCharsets.UTF_8), body.inviteToken().getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "초대 코드를 확인해 주세요.");
        if (!body.password().equals(body.passwordConfirmation()) || body.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호 확인과 길이를 확인해 주세요.");
        UUID id = UUID.randomUUID();
        try {
            db.sql("INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(:id,:email,:hash,:name,'USER')")
                .param("id",id).param("email",normalize(body.email())).param("hash",encoder.encode(body.password()))
                .param("name",body.displayName()==null || body.displayName().isBlank()?"사용자":body.displayName().trim()).update();
        } catch (DuplicateKeyException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT, "계정을 생성할 수 없습니다. 입력 정보를 확인해 주세요."); }
        return Map.of("id",id);
    }
    @PostMapping("/auth/login")
    Session login(@Valid @RequestBody Login body, HttpServletRequest request, HttpServletResponse response) {
        limiter.check("ip:"+request.getRemoteAddr());
        limiter.check("email:"+normalize(body.email()));
        if (body.password().getBytes(StandardCharsets.UTF_8).length > 72) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"이메일 또는 비밀번호를 확인해 주세요.");
        var account = db.sql("SELECT * FROM app_user WHERE email=:email").param("email",normalize(body.email())).query(Account.class).optional();
        boolean matches = encoder.matches(body.password(), account.map(Account::passwordHash).orElse(dummyHash));
        if (account.isEmpty() || !matches) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"이메일 또는 비밀번호를 확인해 주세요.");
        request.getSession(); request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(account.get().id().toString(), null, List.of()));
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context,request,response);
        new org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository().saveToken(null,request,response);
        request.getSession().setAttribute("authenticatedAt",Instant.now());
        return session(account.get());
    }
    @GetMapping({"/auth/session","/users/me"})
    Session me(Authentication authentication) { return session(owned(authentication)); }
    @PatchMapping("/users/me/settings")
    Session settings(@Valid @RequestBody Settings body, Authentication authentication) {
        Account account = owned(authentication);
        try { ZoneId.of(body.timezone()); Currency.getInstance(body.baseCurrency()); }
        catch (IllegalArgumentException | DateTimeException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"시간대 또는 통화를 확인해 주세요."); }
        db.sql("UPDATE app_user SET display_name=:name,timezone=:zone,base_currency=:currency,onboarding_completed=true WHERE id=:id")
            .param("name",body.displayName().trim()).param("zone",body.timezone()).param("currency",body.baseCurrency()).param("id",account.id()).update();
        return me(authentication);
    }
    private Account owned(Authentication auth) {
        if (auth==null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다.");
        return db.sql("SELECT * FROM app_user WHERE id=:id").param("id",UUID.fromString(auth.getName())).query(Account.class).optional()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다."));
    }
    private Session session(Account a) { return new Session(new User(a.id(),a.email(),a.displayName(),a.timezone(),a.baseCurrency(),a.role()),a.onboardingCompleted()); }
    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
