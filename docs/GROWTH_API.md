# Growth API

All routes use `/api/v1/growth`, session authentication and CSRF for writes. ADMIN has the same owner-only access as USER. Dates are `YYYY-MM-DD`; omitted dashboard date uses the user's timezone. Activity and goal date ranges are inclusive. Amounts representing quantity are decimal strings.

## Dashboard

`GET /growth?date=2026-11-08` returns:

```ts
type Activity = { id:string; title:string; category:string; activityDate:string; durationMinutes:number; quantity:string|null; unit:string|null; notes:string|null; version:number }
type Goal = { id:string; title:string; category:string; startDate:string; endDate:string; targetMinutes:number; actualMinutes:number; version:number }
type Routine = { id:string; title:string; weekdays:number[]; startDate:string; targetMinutes:number; active:boolean; version:number }
type Occurrence = { routineId:string; title:string; scheduledDate:string; targetMinutes:number; status:'PENDING'|'DONE'|'SKIPPED'; version:number }
type Reflection = { date:string; well:string; missed:string; next:string; version:number }
type Growth = { date:string; timezone:string; totalMinutes:number; activityCount:number; activities:Activity[]; nextActivityCursor:string|null; routines:Routine[]; occurrences:Occurrence[]; goals:Goal[]; reflection:Reflection|null }
```

Dashboard includes the first 100 activities, all active routine definitions and undeleted goals, scheduled occurrences for the chosen date. Each user can have at most 100 undeleted goals and 100 routines including archived definitions. Goal progress sums only undeleted actual activities matching category and inclusive date range. Routine checks never fabricate activities or minutes.

## Activities

- `GET /growth/activities?from=2026-11-01&to=2026-11-30&size=100&cursor=100` → `{items:Activity[],nextCursor:string|null}`. Inclusive range, max366 days, size1–100, cursor numeric offset.
- `POST /growth/activities` →201 Activity. Body `{title,category,activityDate,durationMinutes,quantity?,unit?,notes?}`; title1–200, category1–60, minutes0–1440, positive quantity up to6 decimals paired with unit1–30. At least positive minutes or quantity is required.
- `PATCH /growth/activities/{id}` →Activity. Full creation body plus `version`.
- `DELETE /growth/activities/{id}?version=0` →204 (soft delete).

## Goals

- `POST /growth/goals` →201 Goal. `{title,category,startDate,endDate,targetMinutes}`; inclusive range1–366 days, positive target up to525600.
- `PATCH /growth/goals/{id}` →Goal. Same body plus `version`.
- `DELETE /growth/goals/{id}?version=0` →204.

## Routines

- `POST /growth/routines` →201 Routine. `{title,weekdays:[1,2,3,4,5],startDate,targetMinutes,active:true}`; weekdays ISO Monday1–Sunday7, unique nonempty, target1–1440.
- `PATCH /growth/routines/{id}` →Routine. Same body plus `version`. Edits apply from today in the user's timezone, retain past definitions and already materialized past/current target snapshots. Future pending occurrences are rematerialized from the new definition. `startDate` is fixed after creation.
- `DELETE /growth/routines/{id}?version=0` →204; archives from today, historical and already materialized current occurrences retained, future occurrences removed.
- `PUT /growth/routines/{id}/occurrences/{date}` →Occurrence. `{status:'PENDING'|'DONE'|'SKIPPED',version}`. Dashboard materializes scheduled occurrences idempotently. Future dates cannot be marked done/skipped.

## Reflection

- `PUT /growth/reflections/{date}` →Reflection. `{well:string,missed:string,next:string,version?:number}` (each max2000). New record omits version; updates require existing version. Blank strings allowed.

All updates reject stale versions with409; records owned by another user return404. Invalid fields/dates return400. No external integrations or automatic activity creation.
