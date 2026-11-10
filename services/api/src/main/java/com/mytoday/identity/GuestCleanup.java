package com.mytoday.identity;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GuestCleanup {
    private final JdbcClient db;
    public GuestCleanup(JdbcClient db) { this.db=db; }

    @Scheduled(fixedDelayString="PT1H") @Transactional
    public void purge() {
        var expired=db.sql("SELECT id FROM app_user WHERE guest_expires_at<=CURRENT_TIMESTAMP ORDER BY guest_expires_at LIMIT 100 FOR UPDATE SKIP LOCKED")
            .query(UUID.class).list();
        for(UUID id:expired) {
            db.sql("DELETE FROM money_posting WHERE transaction_id IN (SELECT id FROM money_transaction WHERE user_id=:id)").param("id",id).update();
            db.sql("DELETE FROM money_transaction WHERE user_id=:id").param("id",id).update();
            for(String table:new String[]{"money_budget","money_account","investment_trade","investment_instrument",
                    "growth_routine_occurrence","growth_routine_revision","growth_routine","growth_reflection",
                    "growth_goal","activity_log","task","calendar_event"})
                db.sql("DELETE FROM "+table+" WHERE user_id=:id").param("id",id).update();
            db.sql("DELETE FROM app_user WHERE id=:id AND guest_expires_at<=CURRENT_TIMESTAMP").param("id",id).update();
        }
    }
}
