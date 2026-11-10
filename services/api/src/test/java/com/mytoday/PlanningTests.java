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
class PlanningTests {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.7-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);
        r.add("spring.datasource.username",postgres::getUsername);
        r.add("spring.datasource.password",postgres::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcClient db;
    UUID owner, other;
    @BeforeEach void accounts() {
        owner=UUID.randomUUID(); other=UUID.randomUUID();
        for (UUID id : new UUID[]{owner,other}) db.sql("INSERT INTO app_user(id,email,password_hash,display_name) VALUES(:id,:email,'unused','계획 테스트')")
            .param("id",id).param("email",id+"@example.test").update();
    }
    MockHttpServletRequestBuilder asOwner(MockHttpServletRequestBuilder request) { return request.with(user(owner.toString())).with(csrf()); }
    String create(String path, String body) throws Exception {
        return mvc.perform(asOwner(post("/api/v1"+path)).contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }
    String task(String title, String due) { return "{\"title\":\""+title+"\",\"priority\":\"P1\",\"dueDate\":\""+due+"\"}"; }
    String timed(String title, String start, String end) {
        return "{\"title\":\""+title+"\",\"allDay\":false,\"timezone\":\"Asia/Seoul\",\"startAt\":\""+start+"\",\"endAt\":\""+end+"\"}";
    }
    String id(String json) { return JsonPath.read(json,"$.id"); }

    @Test void taskLifecycleIsolatedVersionedAndSoftDeleted() throws Exception {
        mvc.perform(get("/api/v1/tasks")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/tasks").with(user(owner.toString())).contentType("application/json").content(task("공부","2026-11-07")))
            .andExpect(status().isForbidden());
        String id=id(create("/tasks",task("공부","2026-11-07")));
        mvc.perform(get("/api/v1/tasks").with(user(other.toString()))).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/tasks/"+id).with(user(other.toString()))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/tasks/"+id).with(user(other.toString())).with(csrf()).contentType("application/json")
            .content("{\"title\":\"침범\",\"status\":\"TODO\",\"priority\":\"P1\",\"version\":0}"))
            .andExpect(status().isNotFound());
        for (int i=0;i<2;i++) mvc.perform(asOwner(post("/api/v1/tasks/"+id+"/complete")).contentType("application/json").content("{\"version\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"))
            .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.completedAt").isNotEmpty());
        mvc.perform(asOwner(patch("/api/v1/tasks/"+id)).contentType("application/json")
            .content("{\"title\":\"옛 수정\",\"status\":\"TODO\",\"priority\":\"P2\",\"version\":0}"))
            .andExpect(status().isConflict());
        mvc.perform(asOwner(post("/api/v1/tasks/"+id+"/reopen")).contentType("application/json").content("{\"version\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("TODO"))
            .andExpect(jsonPath("$.completedAt").isEmpty()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(asOwner(patch("/api/v1/tasks/"+id)).contentType("application/json")
            .content("{\"title\":\"내일 공부\",\"status\":\"IN_PROGRESS\",\"priority\":\"P0\",\"dueDate\":\"2026-11-08\",\"estimatedMinutes\":60,\"version\":2}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
        mvc.perform(asOwner(delete("/api/v1/tasks/"+id).param("version","2"))).andExpect(status().isConflict());
        mvc.perform(asOwner(delete("/api/v1/tasks/"+id).param("version","3"))).andExpect(status().isNoContent());
        mvc.perform(asOwner(get("/api/v1/tasks/"+id))).andExpect(status().isNotFound());
        assertTrue(db.sql("SELECT deleted_at IS NOT NULL FROM task WHERE id=:id").param("id",UUID.fromString(id)).query(Boolean.class).single());
    }

    @Test void taskFiltersPaginationAndValidation() throws Exception {
        String first=id(create("/tasks",task("오늘","2026-11-07")));
        String second=id(create("/tasks",task("내일","2026-11-08")));
        create("/tasks",task("밀린 일","2026-11-06"));
        mvc.perform(asOwner(get("/api/v1/tasks").param("size","1")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(first)).andExpect(jsonPath("$.nextCursor").value("1"));
        mvc.perform(asOwner(get("/api/v1/tasks").param("size","1").param("cursor","1")))
            .andExpect(jsonPath("$.items[0].id").value(second));
        for (String view : new String[]{"today","upcoming","overdue"})
            mvc.perform(asOwner(get("/api/v1/tasks").param("view",view).param("date","2026-11-07")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(asOwner(get("/api/v1/tasks").param("date","2026-11-07").param("priority","P0")))
            .andExpect(jsonPath("$.items.length()").value(0));
        for (String body : new String[]{"{\"title\":\" \"}","{\"title\":\"A\",\"priority\":\"P5\"}",
                "{\"title\":\"A\",\"estimatedMinutes\":-1}",
                "{\"title\":\"A\",\"dueDate\":\"2026-11-07\",\"dueAt\":\"2026-11-07T00:00:00Z\"}"})
            mvc.perform(asOwner(post("/api/v1/tasks")).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        for (String size : new String[]{"0","101","bad"})
            mvc.perform(asOwner(get("/api/v1/tasks").param("size",size))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(asOwner(get("/api/v1/tasks").param("cursor","-1"))).andExpect(status().isBadRequest());
        mvc.perform(asOwner(get("/api/v1/tasks/not-a-uuid"))).andExpect(status().isBadRequest());
    }

    @Test void eventsLifecycleOwnershipConflictAndValidation() throws Exception {
        String body=timed("자정 전 일정","2026-11-07T23:50:00+09:00","2026-11-08T00:10:00+09:00");
        String id=id(create("/calendar/events",body));
        mvc.perform(asOwner(get("/api/v1/calendar/events").param("from","2026-11-07").param("to","2026-11-08")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(id));
        mvc.perform(get("/api/v1/calendar/events/"+id).with(user(other.toString()))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/calendar/events/"+id).param("version","0").with(user(other.toString())).with(csrf())).andExpect(status().isNotFound());
        String edit=body.substring(0,body.length()-1)+",\"version\":0,\"location\":\"집\",\"notes\":\"메모\"}";
        mvc.perform(asOwner(patch("/api/v1/calendar/events/"+id)).contentType("application/json").content(edit))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.location").value("집"));
        mvc.perform(asOwner(patch("/api/v1/calendar/events/"+id)).contentType("application/json").content(edit)).andExpect(status().isConflict());
        mvc.perform(asOwner(delete("/api/v1/calendar/events/"+id).param("version","1"))).andExpect(status().isNoContent());
        mvc.perform(asOwner(get("/api/v1/calendar/events/"+id))).andExpect(status().isNotFound());
        for (String invalid : new String[]{timed("역전","2026-11-07T10:00:00Z","2026-11-07T09:00:00Z"),
                body.replace("Asia/Seoul","Unknown/Zone"),
                "{\"title\":\"종일\",\"allDay\":true,\"timezone\":\"Asia/Seoul\",\"startDate\":\"2026-11-07\"}",
                "{\"title\":\"종일\",\"allDay\":true,\"timezone\":\"Asia/Seoul\",\"startDate\":\"2026-11-07\",\"endDateExclusive\":\"2026-11-07\"}"})
            mvc.perform(asOwner(post("/api/v1/calendar/events")).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(asOwner(get("/api/v1/calendar/events").param("from","2026-11-08").param("to","2026-11-07"))).andExpect(status().isBadRequest());
        mvc.perform(asOwner(get("/api/v1/calendar/events").param("from","2026-01-01").param("to","2028-01-01"))).andExpect(status().isBadRequest());
    }

    @Test void dashboardUsesProfileTimezoneDstAndExclusiveAllDayEnd() throws Exception {
        db.sql("UPDATE app_user SET timezone='America/New_York' WHERE id=:id").param("id",owner).update();
        create("/calendar/events",timed("하루 끝","2026-03-09T03:30:00Z","2026-03-09T03:45:00Z"));
        create("/calendar/events",timed("다음날 경계","2026-03-09T04:00:00Z","2026-03-09T05:00:00Z"));
        create("/calendar/events",timed("전날부터 겹침","2026-03-08T04:30:00Z","2026-03-08T05:30:00Z"));
        create("/calendar/events","{\"title\":\"종일\",\"allDay\":true,\"timezone\":\"America/New_York\",\"startDate\":\"2026-03-07\",\"endDateExclusive\":\"2026-03-09\"}");
        create("/tasks","{\"title\":\"하루 끝 마감\",\"dueAt\":\"2026-03-09T03:30:00Z\"}");
        create("/tasks","{\"title\":\"다음날 마감\",\"dueAt\":\"2026-03-09T04:00:00Z\"}");
        create("/tasks","{\"title\":\"날짜 마감\",\"dueDate\":\"2026-03-08\",\"status\":\"DONE\"}");
        mvc.perform(asOwner(get("/api/v1/dashboard/today").param("date","2026-03-08")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.timezone").value("America/New_York"))
            .andExpect(jsonPath("$.date").value("2026-03-08")).andExpect(jsonPath("$.eventCount").value(3))
            .andExpect(jsonPath("$.taskCount").value(2)).andExpect(jsonPath("$.completedTaskCount").value(1));
        mvc.perform(asOwner(get("/api/v1/calendar/events").param("from","2026-03-09").param("to","2026-03-10")))
            .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(asOwner(get("/api/v1/tasks").param("date","2026-03-08")))
            .andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/api/v1/dashboard/today").param("date","2026-03-08").with(user(other.toString())))
            .andExpect(jsonPath("$.eventCount").value(0)).andExpect(jsonPath("$.taskCount").value(0));
        mvc.perform(asOwner(get("/api/v1/dashboard/today"))).andExpect(status().isOk()).andExpect(jsonPath("$.date").isNotEmpty());
    }
}
