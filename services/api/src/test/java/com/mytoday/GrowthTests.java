package com.mytoday;

import com.jayway.jsonpath.JsonPath;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
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
@Import(GrowthTests.FixedClock.class)
class GrowthTests {
    @TestConfiguration static class FixedClock {
        @Bean @Primary Clock growthClock() { return Clock.fixed(Instant.parse("2026-11-08T00:30:00Z"),ZoneOffset.UTC); }
    }
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.7-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);
        r.add("spring.datasource.username",postgres::getUsername);
        r.add("spring.datasource.password",postgres::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcClient db;
    UUID owner,other;
    @BeforeEach void accounts() {
        owner=UUID.randomUUID(); other=UUID.randomUUID();
        for(UUID id:new UUID[]{owner,other}) db.sql("INSERT INTO app_user(id,email,password_hash,display_name) VALUES(:id,:email,'unused','성장 테스트')")
            .param("id",id).param("email",id+"@example.test").update();
    }
    MockHttpServletRequestBuilder asOwner(MockHttpServletRequestBuilder request) { return request.with(user(owner.toString())).with(csrf()); }
    String create(String route,String body) throws Exception {
        return mvc.perform(asOwner(post("/api/v1/growth"+route)).contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }
    String id(String json) { return JsonPath.read(json,"$.id"); }
    String activity(int minutes,String day) {
        return "{\"title\":\"공부\",\"category\":\"개발\",\"activityDate\":\""+day+"\",\"durationMinutes\":"+minutes+"}";
    }
    String routine(int minutes) {
        return "{\"title\":\"매일 공부\",\"weekdays\":[1,2,3,4,5,6,7],\"startDate\":\"2026-11-01\",\"targetMinutes\":"+minutes+",\"active\":true}";
    }
    MockHttpServletRequestBuilder dashboard(String day) { return asOwner(get("/api/v1/growth").param("date",day)); }

    @Test void actualMinutesOnlyRecomputeAfterEditDeleteAndNeverAddPlannedTime() throws Exception {
        String goal=id(create("/goals","{\"title\":\"공부 한 시간\",\"category\":\"개발\",\"startDate\":\"2026-11-01\",\"endDate\":\"2026-11-30\",\"targetMinutes\":60}"));
        db.sql("INSERT INTO task(id,user_id,title,status,estimated_minutes,completed_at) VALUES(:id,:owner,'예정 공부','DONE',60,CURRENT_TIMESTAMP)")
            .param("id",UUID.randomUUID()).param("owner",owner).update();
        String activity=id(create("/activities",activity(40,"2026-11-08")));
        create("/activities",activity(30,"2026-12-01"));
        create("/activities",activity(10,"2026-11-08").replace("개발","운동"));
        mvc.perform(dashboard("2026-11-08")).andExpect(jsonPath("$.totalMinutes").value(50))
            .andExpect(jsonPath("$.goals[0].actualMinutes").value(40));
        String edit=activity(20,"2026-11-08").replace("}",",\"version\":0}");
        mvc.perform(asOwner(patch("/api/v1/growth/activities/"+activity)).contentType("application/json").content(edit))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(asOwner(patch("/api/v1/growth/activities/"+activity)).contentType("application/json").content(edit)).andExpect(status().isConflict());
        mvc.perform(dashboard("2026-11-08")).andExpect(jsonPath("$.goals[0].actualMinutes").value(20));
        mvc.perform(asOwner(delete("/api/v1/growth/activities/"+activity).param("version","0"))).andExpect(status().isConflict());
        mvc.perform(asOwner(delete("/api/v1/growth/activities/"+activity).param("version","1"))).andExpect(status().isNoContent());
        mvc.perform(dashboard("2026-11-08")).andExpect(jsonPath("$.totalMinutes").value(10)).andExpect(jsonPath("$.goals[0].actualMinutes").value(0));
        assertTrue(db.sql("SELECT deleted_at IS NOT NULL FROM activity_log WHERE id=:id").param("id",UUID.fromString(activity)).query(Boolean.class).single());
        String goalEdit="{\"title\":\"운동\",\"category\":\"운동\",\"startDate\":\"2026-11-01\",\"endDate\":\"2026-11-30\",\"targetMinutes\":30,\"version\":0}";
        mvc.perform(asOwner(patch("/api/v1/growth/goals/"+goal)).contentType("application/json").content(goalEdit))
            .andExpect(status().isOk()).andExpect(jsonPath("$.actualMinutes").value(10));
        mvc.perform(asOwner(delete("/api/v1/growth/goals/"+goal).param("version","1"))).andExpect(status().isNoContent());
        mvc.perform(dashboard("2026-11-08")).andExpect(jsonPath("$.goals.length()").value(0));
    }

    @Test void authenticationCsrfAndOwnerIsolationApplyToEveryModule() throws Exception {
        mvc.perform(get("/api/v1/growth")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/growth/activities").with(user(owner.toString())).contentType("application/json").content(activity(40,"2026-11-08")))
            .andExpect(status().isForbidden());
        String activity=id(create("/activities",activity(40,"2026-11-08")));
        String goal=id(create("/goals","{\"title\":\"목표\",\"category\":\"개발\",\"startDate\":\"2026-11-01\",\"endDate\":\"2026-11-30\",\"targetMinutes\":60}"));
        String routine=id(create("/routines",routine(20)));
        mvc.perform(asOwner(put("/api/v1/growth/reflections/2026-11-08")).contentType("application/json").content("{\"well\":\"비밀\",\"missed\":\"\",\"next\":\"\"}"))
            .andExpect(status().isOk());
        db.sql("UPDATE app_user SET role='ADMIN' WHERE id=:id").param("id",other).update();
        mvc.perform(get("/api/v1/growth").param("date","2026-11-08").with(user(other.toString()).roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.activities.length()").value(0)).andExpect(jsonPath("$.routines.length()").value(0))
            .andExpect(jsonPath("$.goals.length()").value(0)).andExpect(jsonPath("$.reflection").isEmpty());
        for(String path:new String[]{"activities/"+activity,"goals/"+goal,"routines/"+routine})
            mvc.perform(delete("/api/v1/growth/"+path).param("version","0").with(user(other.toString()).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/growth/routines/"+routine+"/occurrences/2026-11-08").with(user(other.toString())).with(csrf())
            .contentType("application/json").content("{\"status\":\"DONE\",\"version\":0}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/growth/activities").param("from","2026-11-01").param("to","2026-11-30").with(user(other.toString())))
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test void routineSnapshotsPreserveMaterializedAndUnopenedPastAndNeverFabricateActivities() throws Exception {
        String routine=id(create("/routines",routine(20)));
        mvc.perform(dashboard("2026-11-07")).andExpect(jsonPath("$.occurrences[0].targetMinutes").value(20));
        mvc.perform(dashboard("2026-11-08")).andExpect(jsonPath("$.occurrences[0].targetMinutes").value(20));
        mvc.perform(dashboard("2026-11-09")).andExpect(jsonPath("$.occurrences[0].targetMinutes").value(20));
        mvc.perform(asOwner(patch("/api/v1/growth/routines/"+routine)).contentType("application/json").content(routine(40).replace("}",",\"version\":0}")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.targetMinutes").value(40));
        for(String date:new String[]{"2026-11-06","2026-11-07","2026-11-08"})
            mvc.perform(dashboard(date)).andExpect(jsonPath("$.occurrences[0].targetMinutes").value(20));
        mvc.perform(dashboard("2026-11-09")).andExpect(jsonPath("$.occurrences[0].targetMinutes").value(40));
        mvc.perform(asOwner(put("/api/v1/growth/routines/"+routine+"/occurrences/2026-11-09")).contentType("application/json")
            .content("{\"status\":\"DONE\",\"version\":0}")).andExpect(status().isBadRequest());
        mvc.perform(asOwner(put("/api/v1/growth/routines/"+routine+"/occurrences/2026-11-08")).contentType("application/json")
            .content("{\"status\":\"DONE\",\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(asOwner(put("/api/v1/growth/routines/"+routine+"/occurrences/2026-11-08")).contentType("application/json")
            .content("{\"status\":\"SKIPPED\",\"version\":0}")).andExpect(status().isConflict());
        mvc.perform(dashboard("2026-11-08")).andExpect(jsonPath("$.totalMinutes").value(0))
            .andExpect(jsonPath("$.activityCount").value(0)).andExpect(jsonPath("$.occurrences.length()").value(1))
            .andExpect(jsonPath("$.occurrences[0].status").value("DONE"));
        mvc.perform(asOwner(delete("/api/v1/growth/routines/"+routine).param("version","1"))).andExpect(status().isNoContent());
        mvc.perform(dashboard("2026-11-09")).andExpect(jsonPath("$.occurrences.length()").value(0));
        mvc.perform(dashboard("2026-11-05")).andExpect(jsonPath("$.occurrences[0].targetMinutes").value(20));
    }

    @Test void timezoneDateDefaultsReflectionsAndQuantityUnitsAreIndependent() throws Exception {
        db.sql("UPDATE app_user SET timezone='America/Los_Angeles' WHERE id=:id").param("id",owner).update();
        create("/activities",activity(25,"2026-11-07"));
        create("/activities","{\"title\":\"독서\",\"category\":\"독서\",\"activityDate\":\"2026-11-07\",\"durationMinutes\":0,\"quantity\":\"30.125\",\"unit\":\"페이지\"}");
        mvc.perform(asOwner(get("/api/v1/growth"))).andExpect(jsonPath("$.date").value("2026-11-07"))
            .andExpect(jsonPath("$.timezone").value("America/Los_Angeles")).andExpect(jsonPath("$.totalMinutes").value(25))
            .andExpect(jsonPath("$.activities[1].quantity").isString()).andExpect(jsonPath("$.activities[1].unit").value("페이지"));
        String body="{\"well\":\"읽기\",\"missed\":\"운동\",\"next\":\"걷기\"}";
        mvc.perform(asOwner(put("/api/v1/growth/reflections/2026-11-07")).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        mvc.perform(asOwner(put("/api/v1/growth/reflections/2026-11-07")).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(asOwner(put("/api/v1/growth/reflections/2026-11-07")).contentType("application/json").content(body.replace("}",",\"version\":0}")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(asOwner(put("/api/v1/growth/reflections/2026-11-07")).contentType("application/json").content(body.replace("}",",\"version\":0}")))
            .andExpect(status().isConflict());
    }

    @Test void validationPaginationAndRangeLimits() throws Exception {
        create("/activities",activity(40,"2026-11-08"));
        create("/activities",activity(30,"2026-11-08"));
        mvc.perform(asOwner(get("/api/v1/growth/activities").param("from","2026-11-08").param("to","2026-11-08").param("size","1")))
            .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.nextCursor").value("1"));
        mvc.perform(asOwner(get("/api/v1/growth/activities").param("from","2026-11-08").param("to","2026-11-08").param("size","1").param("cursor","1")))
            .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.nextCursor").isEmpty());
        for(String body:new String[]{activity(0,"2026-11-08"),activity(-1,"2026-11-08"),activity(1441,"2026-11-08"),
            activity(1,"2026-11-08").replace("개발"," "),activity(0,"2026-11-08").replace("}",",\"quantity\":1}"),
            activity(0,"2026-11-08").replace("}",",\"quantity\":\"1.0000001\",\"unit\":\"회\"}")})
            mvc.perform(asOwner(post("/api/v1/growth/activities")).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        for(String body:new String[]{routine(0),routine(20).replace("[1,2,3,4,5,6,7]","[1,1]"),routine(20).replace("[1,2,3,4,5,6,7]","[8]"),routine(20).replace("[1,2,3,4,5,6,7]","[]")})
            mvc.perform(asOwner(post("/api/v1/growth/routines")).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(asOwner(get("/api/v1/growth/activities").param("from","2026-01-01").param("to","2028-01-01"))).andExpect(status().isBadRequest());
        mvc.perform(asOwner(get("/api/v1/growth/activities").param("from","2026-11-08").param("to","2026-11-07"))).andExpect(status().isBadRequest());
        mvc.perform(asOwner(get("/api/v1/growth/activities").param("from","2026-11-08").param("to","2026-11-08").param("cursor","-1"))).andExpect(status().isBadRequest());
    }
}
