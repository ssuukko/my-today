package com.mytoday.identity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final JdbcClient db;
    private final String inviteToken;

    public AdminController(JdbcClient db, @Value("${registration.invite-token}") String inviteToken) {
        this.db = db;
        this.inviteToken = inviteToken;
    }

    public record User(UUID id, String email, String displayName, String role,
            boolean onboardingCompleted, OffsetDateTime createdAt) {}
    public record Users(List<User> items, String nextCursor, long total) {}
    public record Invitation(boolean enabled, String inviteToken) {}

    @GetMapping("/users")
    Users users(@RequestParam(defaultValue="100") int size, @RequestParam(required=false) String cursor) {
        int offset;
        try {
            offset = cursor == null ? 0 : Integer.parseInt(cursor);
            if (size < 1 || size > 100 || offset < 0 || offset > 1_000_000) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"목록 크기 또는 커서를 확인해 주세요.");
        }
        var rows = db.sql("SELECT id,email,display_name,role,onboarding_completed,created_at FROM app_user ORDER BY created_at,id LIMIT :limit OFFSET :offset")
            .param("limit",size+1).param("offset",offset).query(User.class).list();
        long total = db.sql("SELECT count(*) FROM app_user").query(Long.class).single();
        // shortcut: concurrent deletion can shift offset pages; use keyset cursors for long-lived browsing.
        return new Users(rows.stream().limit(size).toList(),rows.size()>size?Integer.toString(offset+size):null,total);
    }

    @GetMapping("/invitation")
    Invitation invitation() { return new Invitation(!inviteToken.isBlank(),inviteToken.isBlank()?null:inviteToken); }
}
