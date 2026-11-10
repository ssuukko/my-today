package com.mytoday.planning;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Types;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class PlanningController {
    private final JdbcClient db;
    private final Clock clock;
    private static final String TASK_COLUMNS = "id,title,status,priority,due_date,due_at,estimated_minutes,completed_at,version";
    private static final String EVENT_COLUMNS = "id,title,start_at,end_at,all_day,start_date,end_date_exclusive,timezone,location,notes,version";

    public PlanningController(JdbcClient db, Clock clock) { this.db = db; this.clock = clock; }

    public record Task(UUID id, String title, String status, String priority, LocalDate dueDate,
            OffsetDateTime dueAt, Integer estimatedMinutes, OffsetDateTime completedAt, long version) {}
    public record Event(UUID id, String title, OffsetDateTime startAt, OffsetDateTime endAt, boolean allDay,
            LocalDate startDate, LocalDate endDateExclusive, String timezone, String location, String notes, long version) {}
    public record TaskInput(@NotBlank @Size(max=200) String title,
            @Pattern(regexp="TODO|IN_PROGRESS|DONE|CANCELED") String status,
            @Pattern(regexp="P[0-3]") String priority, LocalDate dueDate, OffsetDateTime dueAt,
            @Min(1) @Max(10080) Integer estimatedMinutes, @PositiveOrZero Long version) {}
    public record EventInput(@NotBlank @Size(max=200) String title, @NotNull Boolean allDay,
            OffsetDateTime startAt, OffsetDateTime endAt, LocalDate startDate, LocalDate endDateExclusive,
            @NotBlank @Size(max=64) String timezone, @Size(max=300) String location,
            @Size(max=5000) String notes, @PositiveOrZero Long version) {}
    public record Version(@NotNull @PositiveOrZero Long version) {}
    public record Page<T>(List<T> items, String nextCursor) {}
    public record Dashboard(LocalDate date, String timezone, List<Event> events, List<Task> tasks,
            long taskCount, long completedTaskCount, long eventCount) {}

    @GetMapping("/tasks")
    Page<Task> tasks(Authentication auth, @RequestParam(required=false) String status,
            @RequestParam(required=false) String priority, @RequestParam(required=false) LocalDate date,
            @RequestParam(defaultValue="all") String view,
            @RequestParam(defaultValue="100") int size, @RequestParam(required=false) String cursor) {
        UUID owner = owner(auth);
        if (status != null && !Set.of("TODO","IN_PROGRESS","DONE","CANCELED").contains(status)) throw bad("상태를 확인해 주세요.");
        if (priority != null && !Set.of("P0","P1","P2","P3").contains(priority)) throw bad("우선순위를 확인해 주세요.");
        if (!Set.of("all","today","upcoming","overdue","completed").contains(view)) throw bad("목록 보기를 확인해 주세요.");
        int offset = offset(cursor, size);
        ZoneId zone = timezone(owner);
        LocalDate day = date==null ? LocalDate.now(clock.withZone(zone)) : date;
        String where = " WHERE user_id=:owner AND deleted_at IS NULL";
        if (status != null) where += " AND status=:status";
        if (priority != null) where += " AND priority=:priority";
        if (view.equals("today") || (view.equals("all") && date!=null)) where += " AND (due_date=:date OR (due_at>=:start AND due_at<:end))";
        if (view.equals("upcoming")) where += " AND status NOT IN ('DONE','CANCELED') AND (due_date>:date OR due_at>=:end)";
        if (view.equals("overdue")) where += " AND status NOT IN ('DONE','CANCELED') AND (due_date<:date OR due_at<:start)";
        if (view.equals("completed")) where += " AND status='DONE'";
        var query = db.sql("SELECT " + TASK_COLUMNS + " FROM task" + where + " ORDER BY created_at,id LIMIT :limit OFFSET :offset")
            .param("owner",owner).param("limit",size+1).param("offset",offset);
        if (status != null) query.param("status",status);
        if (priority != null) query.param("priority",priority);
        if (view.equals("today") || view.equals("upcoming") || view.equals("overdue") || (view.equals("all") && date!=null)) dayParams(query,day,zone);
        return page(query.query(Task.class).list(), size, offset);
    }

    @GetMapping("/tasks/{id}")
    Task task(Authentication auth, @PathVariable UUID id) { return findTask(owner(auth), id, false); }

    @PostMapping("/tasks") @ResponseStatus(HttpStatus.CREATED) @Transactional
    Task createTask(Authentication auth, @Valid @RequestBody TaskInput body) {
        UUID owner = owner(auth), id = UUID.randomUUID();
        validate(body);
        String status = body.status() == null ? "TODO" : body.status();
        taskParams(db.sql("INSERT INTO task(id,user_id,title,status,priority,due_date,due_at,estimated_minutes,completed_at) "
            + "VALUES(:id,:owner,:title,:status,:priority,:dueDate,:dueAt,:minutes,:completed)"), body, status,
            "DONE".equals(status) ? now() : null).param("id",id).param("owner",owner).update();
        return findTask(owner,id,false);
    }

    @PatchMapping("/tasks/{id}") @Transactional
    Task updateTask(Authentication auth, @PathVariable UUID id, @Valid @RequestBody TaskInput body) {
        UUID owner = owner(auth);
        Task current = findTask(owner,id,true);
        requireVersion(body.version(),current.version());
        validate(body);
        if (body.status() == null || body.priority() == null) throw bad("상태와 우선순위가 필요합니다.");
        OffsetDateTime completed = "DONE".equals(body.status()) ? (current.completedAt() == null ? now() : current.completedAt()) : null;
        taskParams(db.sql("UPDATE task SET title=:title,status=:status,priority=:priority,due_date=:dueDate,due_at=:dueAt,"
            + "estimated_minutes=:minutes,completed_at=:completed,status_changed_at=CASE WHEN status<>:status THEN CURRENT_TIMESTAMP ELSE status_changed_at END,"
            + "updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE user_id=:owner AND id=:id AND version=:version AND deleted_at IS NULL"),
            body,body.status(),completed).param("owner",owner).param("id",id).param("version",body.version()).update();
        return findTask(owner,id,false);
    }

    @PostMapping("/tasks/{id}/complete") @Transactional
    Task complete(Authentication auth, @PathVariable UUID id, @Valid @RequestBody Version body) {
        return changeStatus(owner(auth),id,body.version(),"DONE");
    }

    @PostMapping("/tasks/{id}/reopen") @Transactional
    Task reopen(Authentication auth, @PathVariable UUID id, @Valid @RequestBody Version body) {
        return changeStatus(owner(auth),id,body.version(),"TODO");
    }

    @DeleteMapping("/tasks/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    void deleteTask(Authentication auth, @PathVariable UUID id, @RequestParam long version) {
        UUID owner = owner(auth);
        requireVersion(version,findTask(owner,id,true).version());
        db.sql("UPDATE task SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE user_id=:owner AND id=:id")
            .param("owner",owner).param("id",id).update();
    }

    @GetMapping("/calendar/events")
    Page<Event> events(Authentication auth, @RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(defaultValue="100") int size, @RequestParam(required=false) String cursor) {
        UUID owner = owner(auth);
        range(from,to);
        int offset = offset(cursor,size);
        return page(eventQuery("SELECT " + EVENT_COLUMNS,owner,from,to,timezone(owner))
            .param("limit",size+1).param("offset",offset).query(Event.class).list(),size,offset);
    }

    @GetMapping("/calendar/events/{id}")
    Event event(Authentication auth, @PathVariable UUID id) { return findEvent(owner(auth),id,false); }

    @PostMapping("/calendar/events") @ResponseStatus(HttpStatus.CREATED) @Transactional
    Event createEvent(Authentication auth, @Valid @RequestBody EventInput body) {
        UUID owner = owner(auth), id = UUID.randomUUID();
        validate(body);
        eventParams(db.sql("INSERT INTO calendar_event(id,user_id,title,all_day,start_at,end_at,start_date,end_date_exclusive,timezone,location,notes) "
            + "VALUES(:id,:owner,:title,:allDay,:startAt,:endAt,:startDate,:endDate,:zone,:location,:notes)"),body)
            .param("id",id).param("owner",owner).update();
        return findEvent(owner,id,false);
    }

    @PatchMapping("/calendar/events/{id}") @Transactional
    Event updateEvent(Authentication auth, @PathVariable UUID id, @Valid @RequestBody EventInput body) {
        UUID owner = owner(auth);
        requireVersion(body.version(),findEvent(owner,id,true).version());
        validate(body);
        eventParams(db.sql("UPDATE calendar_event SET title=:title,all_day=:allDay,start_at=:startAt,end_at=:endAt,start_date=:startDate,"
            + "end_date_exclusive=:endDate,timezone=:zone,location=:location,notes=:notes,updated_at=CURRENT_TIMESTAMP,version=version+1 "
            + "WHERE user_id=:owner AND id=:id AND version=:version AND deleted_at IS NULL"),body)
            .param("owner",owner).param("id",id).param("version",body.version()).update();
        return findEvent(owner,id,false);
    }

    @DeleteMapping("/calendar/events/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    void deleteEvent(Authentication auth, @PathVariable UUID id, @RequestParam long version) {
        UUID owner = owner(auth);
        requireVersion(version,findEvent(owner,id,true).version());
        db.sql("UPDATE calendar_event SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE user_id=:owner AND id=:id")
            .param("owner",owner).param("id",id).update();
    }

    @GetMapping("/dashboard/today") @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    Dashboard dashboard(Authentication auth, @RequestParam(required=false) LocalDate date) {
        UUID owner = owner(auth);
        ZoneId zone = timezone(owner);
        LocalDate day = date == null ? LocalDate.now(clock.withZone(zone)) : date;
        String taskWhere = " FROM task WHERE user_id=:owner AND deleted_at IS NULL AND (due_date=:date OR (due_at>=:start AND due_at<:end))";
        List<Task> tasks = dayParams(db.sql("SELECT " + TASK_COLUMNS + taskWhere + " ORDER BY priority,created_at,id LIMIT 100")
            .param("owner",owner),day,zone).query(Task.class).list();
        long taskCount = dayParams(db.sql("SELECT count(*)"+taskWhere).param("owner",owner),day,zone).query(Long.class).single();
        long completedCount = dayParams(db.sql("SELECT count(*)"+taskWhere+" AND status='DONE'").param("owner",owner),day,zone).query(Long.class).single();
        List<Event> events = eventQuery("SELECT "+EVENT_COLUMNS,owner,day,day.plusDays(1),zone)
            .param("limit",100).param("offset",0).query(Event.class).list();
        long eventCount = eventQuery("SELECT count(*)",owner,day,day.plusDays(1),zone).query(Long.class).single();
        return new Dashboard(day,zone.getId(),events,tasks,taskCount,completedCount,eventCount);
    }

    private JdbcClient.StatementSpec eventQuery(String select, UUID owner, LocalDate from, LocalDate to, ZoneId zone) {
        String sql = select + " FROM calendar_event WHERE user_id=:owner AND deleted_at IS NULL AND "
            + "((all_day AND start_date<:toDate AND end_date_exclusive>:fromDate) OR (NOT all_day AND start_at<:end AND end_at>:start))";
        if (!select.equals("SELECT count(*)")) sql += " ORDER BY created_at,id LIMIT :limit OFFSET :offset";
        return db.sql(sql).param("owner",owner).param("fromDate",from).param("toDate",to)
            .param("start",from.atStartOfDay(zone).toOffsetDateTime()).param("end",to.atStartOfDay(zone).toOffsetDateTime());
    }

    private Task changeStatus(UUID owner, UUID id, long version, String status) {
        Task current = findTask(owner,id,true);
        if (current.status().equals(status) && (version == current.version() || version == current.version()-1)) return current;
        requireVersion(version,current.version());
        db.sql("UPDATE task SET status=:status,completed_at=:completed,status_changed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 "
            + "WHERE user_id=:owner AND id=:id AND version=:version AND deleted_at IS NULL")
            .param("status",status).param("completed","DONE".equals(status) ? now() : null,Types.TIMESTAMP_WITH_TIMEZONE)
            .param("owner",owner).param("id",id).param("version",version).update();
        return findTask(owner,id,false);
    }

    private Task findTask(UUID owner, UUID id, boolean lock) {
        return db.sql("SELECT "+TASK_COLUMNS+" FROM task WHERE user_id=:owner AND id=:id AND deleted_at IS NULL"+(lock?" FOR UPDATE":""))
            .param("owner",owner).param("id",id).query(Task.class).optional().orElseThrow(PlanningController::missing);
    }
    private Event findEvent(UUID owner, UUID id, boolean lock) {
        return db.sql("SELECT "+EVENT_COLUMNS+" FROM calendar_event WHERE user_id=:owner AND id=:id AND deleted_at IS NULL"+(lock?" FOR UPDATE":""))
            .param("owner",owner).param("id",id).query(Event.class).optional().orElseThrow(PlanningController::missing);
    }
    private JdbcClient.StatementSpec taskParams(JdbcClient.StatementSpec q, TaskInput b, String status, OffsetDateTime completed) {
        return q.param("title",b.title().trim()).param("status",status).param("priority",b.priority()==null?"P2":b.priority())
            .param("dueDate",b.dueDate(),Types.DATE).param("dueAt",b.dueAt(),Types.TIMESTAMP_WITH_TIMEZONE)
            .param("minutes",b.estimatedMinutes(),Types.INTEGER).param("completed",completed,Types.TIMESTAMP_WITH_TIMEZONE);
    }
    private JdbcClient.StatementSpec eventParams(JdbcClient.StatementSpec q, EventInput b) {
        return q.param("title",b.title().trim()).param("allDay",b.allDay())
            .param("startAt",b.startAt(),Types.TIMESTAMP_WITH_TIMEZONE).param("endAt",b.endAt(),Types.TIMESTAMP_WITH_TIMEZONE)
            .param("startDate",b.startDate(),Types.DATE).param("endDate",b.endDateExclusive(),Types.DATE)
            .param("zone",b.timezone()).param("location",b.location(),Types.VARCHAR).param("notes",b.notes(),Types.VARCHAR);
    }
    private JdbcClient.StatementSpec dayParams(JdbcClient.StatementSpec q, LocalDate date, ZoneId zone) {
        return q.param("date",date).param("start",date.atStartOfDay(zone).toOffsetDateTime())
            .param("end",date.plusDays(1).atStartOfDay(zone).toOffsetDateTime());
    }
    private void validate(TaskInput b) {
        if (b.dueDate()!=null && b.dueAt()!=null) throw bad("마감 날짜와 마감 시각 중 하나를 선택해 주세요.");
    }
    private void validate(EventInput b) {
        try { ZoneId.of(b.timezone()); }
        catch (DateTimeException ex) { throw bad("일정 시간대를 확인해 주세요."); }
        boolean valid = b.allDay()
            ? b.startDate()!=null && b.endDateExclusive()!=null && b.endDateExclusive().isAfter(b.startDate()) && b.startAt()==null && b.endAt()==null
            : b.startAt()!=null && b.endAt()!=null && b.endAt().isAfter(b.startAt()) && b.startDate()==null && b.endDateExclusive()==null;
        if (!valid) throw bad("일정 시작과 종료를 확인해 주세요. 종료는 시작보다 늦어야 합니다.");
    }
    private UUID owner(Authentication auth) { return UUID.fromString(auth.getName()); }
    private ZoneId timezone(UUID owner) {
        return ZoneId.of(db.sql("SELECT timezone FROM app_user WHERE id=:owner").param("owner",owner).query(String.class).single());
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private static void requireVersion(Long version, long current) {
        if (version==null || version<0) throw bad("수정 버전이 필요합니다.");
        if (version!=current) throw new ResponseStatusException(HttpStatus.CONFLICT,"다른 기기에서 변경되었습니다. 새로 불러온 뒤 다시 시도해 주세요.");
    }
    private static void range(LocalDate from, LocalDate to) {
        if (!to.isAfter(from) || ChronoUnit.DAYS.between(from,to)>366) throw bad("조회 기간은 1일부터 366일까지입니다.");
    }
    private static int offset(String cursor, int size) {
        if (size<1 || size>100) throw bad("한 번에 1~100건을 조회할 수 있습니다.");
        try {
            int result = cursor==null ? 0 : Integer.parseInt(cursor);
            if (result<0 || result>1_000_000) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException ex) { throw bad("목록 커서를 확인해 주세요."); }
    }
    private static <T> Page<T> page(List<T> rows, int size, int offset) {
        // shortcut: offset cursors can shift during concurrent insert/delete; use keyset cursors for long-lived pagination.
        return new Page<>(rows.stream().limit(size).toList(),rows.size()>size?Integer.toString(offset+size):null);
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"기록을 찾을 수 없습니다."); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
