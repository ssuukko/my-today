import { describe, expect, it } from 'vitest'
import { addDays, calendarRange, dateInZone, localDateTime, wallTimeToInstant } from '../src/planningDates'

describe('planning date boundaries', () => {
  it('keeps Seoul late night in its local day independent of browser timezone', () => {
    expect(dateInZone('2026-11-07T14:50:00Z', 'Asia/Seoul')).toBe('2026-11-07')
    expect(localDateTime('2026-11-07T15:00:00Z', 'Asia/Seoul')).toBe('2026-11-08T00:00:00')
    expect(wallTimeToInstant('2026-11-07T23:50', 'Asia/Seoul')).toBe('2026-11-07T14:50:00.000Z')
  })
  it('rejects nonexistent DST times and chooses the earlier repeated instant', () => {
    expect(() => wallTimeToInstant('2026-03-08T02:30', 'America/New_York')).toThrow('존재하지 않는')
    expect(wallTimeToInstant('2026-11-01T01:30', 'America/New_York')).toBe('2026-11-01T05:30:00.000Z')
    expect(wallTimeToInstant('2026-11-01T02:30', 'America/New_York')).toBe('2026-11-01T07:30:00.000Z')
  })
  it('does date-only arithmetic across leap day, month and year boundaries', () => {
    expect(addDays('2024-02-28', 1)).toBe('2024-02-29')
    expect(addDays('2024-02-29', 1)).toBe('2024-03-01')
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(addDays('2026-11-01', -1)).toBe('2026-10-31')
  })
  it('includes spillover dates and uses Monday-start weeks even when month starts Sunday', () => {
    expect(calendarRange('2026-11-07', 'month')).toEqual({ from: '2026-10-26', to: '2026-12-07' })
    expect(calendarRange('2026-11-08', 'week')).toEqual({ from: '2026-11-02', to: '2026-11-09' })
    expect(calendarRange('2026-11-07', 'day')).toEqual({ from: '2026-11-07', to: '2026-11-08' })
    expect(calendarRange('2024-02-29', 'month')).toEqual({ from: '2024-01-29', to: '2024-03-11' })
  })
})
