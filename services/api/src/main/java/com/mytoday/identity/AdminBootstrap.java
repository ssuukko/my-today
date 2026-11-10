package com.mytoday.identity;

import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final JdbcClient db;
    private final PasswordEncoder encoder;
    private final Validator validator;
    private final String email;
    private final String password;

    public AdminBootstrap(JdbcClient db, PasswordEncoder encoder, Validator validator,
            @Value("${bootstrap.admin.email}") String email,
            @Value("${bootstrap.admin.password}") String password) {
        this.db = db; this.encoder = encoder; this.validator = validator;
        this.email = email; this.password = password;
    }

    private record Input(@NotBlank @Email @Size(max=254) String email,
            @NotBlank @Size(min=12,max=72) String password) {}

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!validator.validate(new Input(normalized,password)).isEmpty() || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalStateException("관리자 초기화 이메일과 비밀번호 설정을 확인해 주세요.");
        db.sql("SELECT pg_advisory_xact_lock(:key)").param("key",0x4d59544f444159L).query(Object.class).single();
        if (db.sql("SELECT EXISTS(SELECT FROM app_user WHERE role='ADMIN')").query(Boolean.class).single()) {
            log.info("Administrator already configured; bootstrap made no changes.");
            return;
        }
        if (db.sql("SELECT EXISTS(SELECT FROM app_user WHERE email=:email)").param("email",normalized).query(Boolean.class).single())
            throw new IllegalStateException("관리자 초기화 이메일이 기존 계정에 사용 중입니다. 기존 계정은 변경하지 않았습니다.");
        db.sql("INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(:id,:email,:hash,'관리자','ADMIN')")
            .param("id",UUID.randomUUID()).param("email",normalized).param("hash",encoder.encode(password)).update();
        log.info("Initial administrator created; remove bootstrap credentials before normal operation.");
    }
}
