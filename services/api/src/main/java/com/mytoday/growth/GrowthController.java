package com.mytoday.growth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/growth")
public class GrowthController {
    private final JdbcClient db;
    private final Clock clock;
    private static final String ACTIVITY = "id,title,category,activity_date,duration_minutes,quantity::text AS quantity,unit,notes,version";
    private static final String GOAL = "g.id,g.title,g.category,g.start_date,g.end_date,g.target_minutes,g.version,"
        + "(SELECT COALESCE(sum(a.duration_minutes),0) FROM activity_log a WHERE a.user_id=g.user_id AND a.deleted_at IS NULL "
        + "AND a.category=g.category AND a.activity_date BETWEEN g.start_date AND g.end_date) AS actual_minutes";

    public GrowthController(JdbcClient db, Clock clock) { this.db=db; this.clock=clock; }

    public record Activity(UUID id, String title, String category, LocalDate activityDate, int durationMinutes,
            String quantity, String unit, String notes, long version) {}
    public record ActivityInput(@NotBlank @Size(max=200) String title, @NotBlank @Size(max=60) String category,
            @NotNull LocalDate activityDate, @NotNull @Min(0) @Max(1440) Integer durationMinutes,
            @DecimalMin(value="0",inclusive=false) @Digits(integer=14,fraction=6) BigDecimal quantity,
            @Size(max=30) String unit, @Size(max=5000) String notes, @PositiveOrZero Long version) {}
    public record Goal(UUID id, String title, String category, LocalDate startDate, LocalDate endDate,
            int targetMinutes, long actualMinutes, long version) {}
    public record GoalInput(@NotBlank @Size(max=200) String title, @NotBlank @Size(max=60) String category,
            @NotNull LocalDate startDate, @NotNull LocalDate endDate,
            @NotNull @Min(1) @Max(525600) Integer targetMinutes, @PositiveOrZero Long version) {}
    public record Routine(UUID id, String title, List<Integer> weekdays, LocalDate startDate,
            int targetMinutes, boolean active, long version) {}
    public record RoutineInput(@NotBlank @Size(max=200) String title,
            @NotEmpty @Size(max=7) List<@NotNull @Min(1) @Max(7) Integer> weekdays,
            @NotNull LocalDate startDate, @NotNull @Min(1) @Max(1440) Integer targetMinutes,
            @NotNull Boolean active, @PositiveOrZero Long version) {}
    public record Occurrence(UUID routineId, String title, LocalDate scheduledDate, int targetMinutes, String status, long version) {}
    public record OccurrenceInput(@NotNull @Pattern(regexp="PENDING|DONE|SKIPPED") String status,
            @NotNull @PositiveOrZero Long version) {}
    public record Reflection(LocalDate date, String well, String missed, String next, long version) {}
    public record ReflectionInput(@NotNull @Size(max=2000) String well, @NotNull @Size(max=2000) String missed,
            @NotNull @Size(max=2000) String next, @PositiveOrZero Long version) {}
    public record Page<T>(List<T> items, String nextCursor) {}
    public record Dashboard(LocalDate date, String timezone, long totalMinutes, long activityCount,
            List<Activity> activities, String nextActivityCursor, List<Routine> routines,
            List<Occurrence> occurrences, List<Goal> goals, Reflection reflection) {}

    @GetMapping @Transactional
    Dashboard dashboard(Authentication auth, @RequestParam(required=false) LocalDate date) {
        UUID owner=owner(auth);
        ZoneId zone=timezone(owner);
        LocalDate day=date==null?today(zone):date;
        materialize(owner,day);
        Page<Activity> activities=activities(owner,day,day,100,null);
        long total=db.sql("SELECT COALESCE(sum(duration_minutes),0) FROM activity_log WHERE user_id=:owner AND activity_date=:day AND deleted_at IS NULL")
            .param("owner",owner).param("day",day).query(Long.class).single();
        long count=db.sql("SELECT count(*) FROM activity_log WHERE user_id=:owner AND activity_date=:day AND deleted_at IS NULL")
            .param("owner",owner).param("day",day).query(Long.class).single();
        var routines=db.sql("SELECT * FROM growth_routine WHERE user_id=:owner AND archived_date IS NULL ORDER BY created_at,id LIMIT 100")
            .param("owner",owner).query(this::routineRow).list();
        var occurrences=db.sql("SELECT routine_id,title,scheduled_date,target_minutes,status,version FROM growth_routine_occurrence "
            + "WHERE user_id=:owner AND scheduled_date=:day ORDER BY title,routine_id LIMIT 100")
            .param("owner",owner).param("day",day).query(Occurrence.class).list();
        var goals=db.sql("SELECT "+GOAL+" FROM growth_goal g WHERE g.user_id=:owner AND g.deleted_at IS NULL ORDER BY g.created_at,g.id LIMIT 100")
            .param("owner",owner).query(Goal.class).list();
        Reflection reflection=db.sql("SELECT date,well,missed,next,version FROM growth_reflection WHERE user_id=:owner AND date=:day")
            .param("owner",owner).param("day",day).query(Reflection.class).optional().orElse(null);
        return new Dashboard(day,zone.getId(),total,count,activities.items(),activities.nextCursor(),routines,occurrences,goals,reflection);
    }

    @GetMapping("/activities")
    Page<Activity> listActivities(Authentication auth, @RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(defaultValue="100") int size, @RequestParam(required=false) String cursor) {
        return activities(owner(auth),from,to,size,cursor);
    }

    @PostMapping("/activities") @ResponseStatus(HttpStatus.CREATED) @Transactional
    Activity createActivity(Authentication auth, @Valid @RequestBody ActivityInput b) {
        validate(b);
        UUID owner=owner(auth), id=UUID.randomUUID();
        activityParams(db.sql("INSERT INTO activity_log(id,user_id,title,category,activity_date,duration_minutes,quantity,unit,notes) "
            + "VALUES(:id,:owner,:title,:category,:day,:minutes,:quantity,:unit,:notes)"),b)
            .param("id",id).param("owner",owner).update();
        return activity(owner,id,false);
    }

    @PatchMapping("/activities/{id}") @Transactional
    Activity updateActivity(Authentication auth, @PathVariable UUID id, @Valid @RequestBody ActivityInput b) {
        UUID owner=owner(auth);
        requireVersion(b.version(),activity(owner,id,true).version());
        validate(b);
        activityParams(db.sql("UPDATE activity_log SET title=:title,category=:category,activity_date=:day,duration_minutes=:minutes,"
            + "quantity=:quantity,unit=:unit,notes=:notes,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE user_id=:owner AND id=:id"),b)
            .param("owner",owner).param("id",id).update();
        return activity(owner,id,false);
    }

    @DeleteMapping("/activities/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    void deleteActivity(Authentication auth, @PathVariable UUID id, @RequestParam long version) {
        UUID owner=owner(auth);
        requireVersion(version,activity(owner,id,true).version());
        db.sql("UPDATE activity_log SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE user_id=:owner AND id=:id")
            .param("owner",owner).param("id",id).update();
    }

    @PostMapping("/goals") @ResponseStatus(HttpStatus.CREATED) @Transactional
    Goal createGoal(Authentication auth, @Valid @RequestBody GoalInput b) {
        range(b.startDate(),b.endDate());
        UUID owner=owner(auth), id=UUID.randomUUID();
        lockOwner(owner);
        if (db.sql("SELECT count(*) FROM growth_goal WHERE user_id=:owner AND deleted_at IS NULL").param("owner",owner).query(Long.class).single()>=100)
            throw bad("목표는 최대 100개까지 등록할 수 있습니다.");
        goalParams(db.sql("INSERT INTO growth_goal(id,user_id,title,category,start_date,end_date,target_minutes) "
            + "VALUES(:id,:owner,:title,:category,:start,:end,:target)"),b).param("id",id).param("owner",owner).update();
        return goal(owner,id,false);
    }

    @PatchMapping("/goals/{id}") @Transactional
    Goal updateGoal(Authentication auth, @PathVariable UUID id, @Valid @RequestBody GoalInput b) {
        UUID owner=owner(auth);
        requireVersion(b.version(),goal(owner,id,true).version());
        range(b.startDate(),b.endDate());
        goalParams(db.sql("UPDATE growth_goal SET title=:title,category=:category,start_date=:start,end_date=:end,target_minutes=:target,version=version+1 "
            + "WHERE user_id=:owner AND id=:id"),b).param("owner",owner).param("id",id).update();
        return goal(owner,id,false);
    }

    @DeleteMapping("/goals/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    void deleteGoal(Authentication auth, @PathVariable UUID id, @RequestParam long version) {
        UUID owner=owner(auth);
        requireVersion(version,goal(owner,id,true).version());
        db.sql("UPDATE growth_goal SET deleted_at=CURRENT_TIMESTAMP,version=version+1 WHERE user_id=:owner AND id=:id")
            .param("owner",owner).param("id",id).update();
    }

    @PostMapping("/routines") @ResponseStatus(HttpStatus.CREATED) @Transactional
    Routine createRoutine(Authentication auth, @Valid @RequestBody RoutineInput b) {
        validate(b);
        UUID owner=owner(auth), id=UUID.randomUUID();
        lockOwner(owner);
        if (db.sql("SELECT count(*) FROM growth_routine WHERE user_id=:owner").param("owner",owner).query(Long.class).single()>=100)
            throw bad("루틴은 보관된 항목을 포함해 최대 100개까지 등록할 수 있습니다.");
        routineParams(db.sql("INSERT INTO growth_routine(id,user_id,title,weekdays,start_date,target_minutes,active) "
            + "VALUES(:id,:owner,:title,:weekdays,:start,:target,:active)"),b).param("id",id).param("owner",owner).update();
        revision(owner,id,0,b.startDate(),b);
        return routine(owner,id,false);
    }

    @PatchMapping("/routines/{id}") @Transactional
    Routine updateRoutine(Authentication auth, @PathVariable UUID id, @Valid @RequestBody RoutineInput b) {
        UUID owner=owner(auth);
        Routine current=routine(owner,id,true);
        requireVersion(b.version(),current.version());
        validate(b);
        if (!current.startDate().equals(b.startDate())) throw bad("루틴 시작일은 변경할 수 없습니다.");
        routineParams(db.sql("UPDATE growth_routine SET title=:title,weekdays=:weekdays,target_minutes=:target,active=:active,"
            + "start_date=:start,version=version+1 WHERE user_id=:owner AND id=:id"),b).param("owner",owner).param("id",id).update();
        LocalDate day=today(timezone(owner));
        revision(owner,id,current.version()+1,day.isBefore(b.startDate())?b.startDate():day,b);
        clearFutureOccurrences(owner,id,day);
        return routine(owner,id,false);
    }

    @DeleteMapping("/routines/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    void deleteRoutine(Authentication auth, @PathVariable UUID id, @RequestParam long version) {
        UUID owner=owner(auth);
        requireVersion(version,routine(owner,id,true).version());
        db.sql("UPDATE growth_routine SET archived_date=:day,version=version+1 WHERE user_id=:owner AND id=:id")
            .param("day",today(timezone(owner))).param("owner",owner).param("id",id).update();
        clearFutureOccurrences(owner,id,today(timezone(owner)));
    }

    @PutMapping("/routines/{id}/occurrences/{date}") @Transactional
    Occurrence updateOccurrence(Authentication auth, @PathVariable UUID id, @PathVariable LocalDate date,
            @Valid @RequestBody OccurrenceInput b) {
        UUID owner=owner(auth);
        // Serialize materialization and revision changes on the same owner-owned definition.
        db.sql("SELECT id FROM growth_routine WHERE user_id=:owner AND id=:id FOR UPDATE")
            .param("owner",owner).param("id",id).query(UUID.class).optional().orElseThrow(GrowthController::missing);
        materialize(owner,date);
        Occurrence current=occurrence(owner,id,date,true);
        requireVersion(b.version(),current.version());
        if (date.isAfter(today(timezone(owner))) && !b.status().equals("PENDING")) throw bad("미래 루틴은 완료하거나 건너뛸 수 없습니다.");
        db.sql("UPDATE growth_routine_occurrence SET status=:status,version=version+1 WHERE user_id=:owner AND routine_id=:id AND scheduled_date=:day")
            .param("status",b.status()).param("owner",owner).param("id",id).param("day",date).update();
        return occurrence(owner,id,date,false);
    }

    @PutMapping("/reflections/{date}") @Transactional
    Reflection saveReflection(Authentication auth, @PathVariable LocalDate date, @Valid @RequestBody ReflectionInput b) {
        UUID owner=owner(auth);
        lockOwner(owner);
        var current=db.sql("SELECT date,well,missed,next,version FROM growth_reflection WHERE user_id=:owner AND date=:day FOR UPDATE")
            .param("owner",owner).param("day",date).query(Reflection.class).optional();
        if (current.isPresent()) requireVersion(b.version(),current.get().version());
        else if (b.version()!=null) throw conflict();
        db.sql("INSERT INTO growth_reflection(user_id,date,well,missed,next) VALUES(:owner,:day,:well,:missed,:next) "
            + "ON CONFLICT(user_id,date) DO UPDATE SET well=EXCLUDED.well,missed=EXCLUDED.missed,next=EXCLUDED.next,version=growth_reflection.version+1")
            .param("owner",owner).param("day",date).param("well",b.well()).param("missed",b.missed()).param("next",b.next()).update();
        return db.sql("SELECT date,well,missed,next,version FROM growth_reflection WHERE user_id=:owner AND date=:day")
            .param("owner",owner).param("day",date).query(Reflection.class).single();
    }

    private Page<Activity> activities(UUID owner, LocalDate from, LocalDate to, int size, String cursor) {
        range(from,to);
        if (size<1 || size>100) throw bad("한 번에 1~100건을 조회할 수 있습니다.");
        int offset;
        try { offset=cursor==null?0:Integer.parseInt(cursor); if (offset<0 || offset>1_000_000) throw new NumberFormatException(); }
        catch (NumberFormatException ex) { throw bad("목록 커서를 확인해 주세요."); }
        var rows=db.sql("SELECT "+ACTIVITY+" FROM activity_log WHERE user_id=:owner AND deleted_at IS NULL "
            + "AND activity_date BETWEEN :from AND :to ORDER BY activity_date,created_at,id LIMIT :limit OFFSET :offset")
            .param("owner",owner).param("from",from).param("to",to).param("limit",size+1).param("offset",offset).query(Activity.class).list();
        // shortcut: offset pages can shift during concurrent edits; use keyset cursors when long-lived lists are needed.
        return new Page<>(rows.stream().limit(size).toList(),rows.size()>size?Integer.toString(offset+size):null);
    }

    private Activity activity(UUID owner, UUID id, boolean lock) {
        return db.sql("SELECT "+ACTIVITY+" FROM activity_log WHERE user_id=:owner AND id=:id AND deleted_at IS NULL"+(lock?" FOR UPDATE":""))
            .param("owner",owner).param("id",id).query(Activity.class).optional().orElseThrow(GrowthController::missing);
    }
    private Goal goal(UUID owner, UUID id, boolean lock) {
        return db.sql("SELECT "+GOAL+" FROM growth_goal g WHERE g.user_id=:owner AND g.id=:id AND g.deleted_at IS NULL"+(lock?" FOR UPDATE OF g":""))
            .param("owner",owner).param("id",id).query(Goal.class).optional().orElseThrow(GrowthController::missing);
    }
    private Routine routine(UUID owner, UUID id, boolean lock) {
        return db.sql("SELECT * FROM growth_routine WHERE user_id=:owner AND id=:id AND archived_date IS NULL"+(lock?" FOR UPDATE":""))
            .param("owner",owner).param("id",id).query(this::routineRow).optional().orElseThrow(GrowthController::missing);
    }
    private Routine routineRow(ResultSet rs, int row) throws SQLException {
        return new Routine(rs.getObject("id",UUID.class),rs.getString("title"),
            Arrays.stream(rs.getString("weekdays").split(",")).map(Integer::valueOf).toList(),
            rs.getObject("start_date",LocalDate.class),rs.getInt("target_minutes"),rs.getBoolean("active"),rs.getLong("version"));
    }
    private Occurrence occurrence(UUID owner, UUID id, LocalDate date, boolean lock) {
        return db.sql("SELECT routine_id,title,scheduled_date,target_minutes,status,version FROM growth_routine_occurrence "
            + "WHERE user_id=:owner AND routine_id=:id AND scheduled_date=:day"+(lock?" FOR UPDATE":""))
            .param("owner",owner).param("id",id).param("day",date).query(Occurrence.class).optional().orElseThrow(GrowthController::missing);
    }
    private void materialize(UUID owner, LocalDate day) {
        // Revisions also preserve targets for historical dates first opened after a definition was edited.
        db.sql("INSERT INTO growth_routine_occurrence(routine_id,user_id,scheduled_date,title,target_minutes) "
            + "SELECT r.id,r.user_id,:day,v.title,v.target_minutes FROM growth_routine r "
            + "JOIN LATERAL (SELECT title,weekdays,target_minutes,active FROM growth_routine_revision v "
            + "WHERE v.routine_id=r.id AND v.user_id=:owner AND v.effective_from<=:day ORDER BY v.effective_from DESC,v.version DESC LIMIT 1) v ON true "
            + "WHERE r.user_id=:owner AND r.start_date<=:day AND (r.archived_date IS NULL OR r.archived_date>:day) "
            + "AND v.active AND :weekday=ANY(string_to_array(v.weekdays,',')::int[]) "
            + "ON CONFLICT(routine_id,scheduled_date) DO NOTHING")
            .param("owner",owner).param("day",day).param("weekday",day.getDayOfWeek().getValue()).update();
    }
    private void revision(UUID owner, UUID id, long version, LocalDate effective, RoutineInput b) {
        routineParams(db.sql("INSERT INTO growth_routine_revision(routine_id,user_id,version,effective_from,title,weekdays,target_minutes,active) "
            + "VALUES(:id,:owner,:version,:effective,:title,:weekdays,:target,:active)"),b)
            .param("id",id).param("owner",owner).param("version",version).param("effective",effective).update();
    }
    private void clearFutureOccurrences(UUID owner, UUID id, LocalDate today) {
        db.sql("DELETE FROM growth_routine_occurrence WHERE user_id=:owner AND routine_id=:id AND scheduled_date>:day")
            .param("owner",owner).param("id",id).param("day",today).update();
    }
    private JdbcClient.StatementSpec activityParams(JdbcClient.StatementSpec q, ActivityInput b) {
        return q.param("title",b.title().trim()).param("category",b.category().trim()).param("day",b.activityDate())
            .param("minutes",b.durationMinutes()).param("quantity",b.quantity(),Types.NUMERIC)
            .param("unit",b.unit()==null?null:b.unit().trim(),Types.VARCHAR).param("notes",b.notes(),Types.VARCHAR);
    }
    private JdbcClient.StatementSpec goalParams(JdbcClient.StatementSpec q, GoalInput b) {
        return q.param("title",b.title().trim()).param("category",b.category().trim()).param("start",b.startDate())
            .param("end",b.endDate()).param("target",b.targetMinutes());
    }
    private JdbcClient.StatementSpec routineParams(JdbcClient.StatementSpec q, RoutineInput b) {
        return q.param("title",b.title().trim()).param("weekdays",b.weekdays().stream().sorted().map(String::valueOf).collect(Collectors.joining(",")))
            .param("start",b.startDate()).param("target",b.targetMinutes()).param("active",b.active());
    }
    private void validate(ActivityInput b) {
        if ((b.quantity()==null)!=(b.unit()==null) || (b.unit()!=null && b.unit().isBlank())) throw bad("수량과 단위를 함께 입력해 주세요.");
        if (b.durationMinutes()==0 && b.quantity()==null) throw bad("실제 시간 또는 수량을 입력해 주세요.");
    }
    private void validate(RoutineInput b) {
        if (new HashSet<>(b.weekdays()).size()!=b.weekdays().size()) throw bad("루틴 요일은 중복 없이 선택해 주세요.");
    }
    private void lockOwner(UUID owner) {
        db.sql("SELECT id FROM app_user WHERE id=:owner FOR UPDATE").param("owner",owner).query(UUID.class).optional().orElseThrow(GrowthController::missing);
    }
    private UUID owner(Authentication auth) { return UUID.fromString(auth.getName()); }
    private ZoneId timezone(UUID owner) {
        return ZoneId.of(db.sql("SELECT timezone FROM app_user WHERE id=:owner").param("owner",owner).query(String.class).single());
    }
    private LocalDate today(ZoneId zone) { return LocalDate.now(clock.withZone(zone)); }
    private static void range(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from,to)>=366) throw bad("조회 기간은 1일부터 366일까지입니다.");
    }
    private static void requireVersion(Long version, long current) {
        if (version==null || version<0) throw bad("수정 버전이 필요합니다.");
        if (version!=current) throw conflict();
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"기록을 찾을 수 없습니다."); }
    private static ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT,"다른 기기에서 변경되었습니다. 새로 불러온 뒤 다시 시도해 주세요."); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
