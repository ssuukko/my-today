export function localDateTime(value: string | Date, timezone: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date(value))
  const part = (type: string) => parts.find(p => p.type === type)!.value
  return `${part('year')}-${part('month')}-${part('day')}T${part('hour')}:${part('minute')}:${part('second')}`
}

export function dateInZone(value: string | Date, timezone: string): string {
  return localDateTime(value, timezone).slice(0, 10)
}

export function addDays(date: string, days: number): string {
  const value = new Date(`${date}T12:00:00Z`)
  value.setUTCDate(value.getUTCDate() + days)
  return value.toISOString().slice(0, 10)
}

export function wallTimeToInstant(value: string, timezone: string): string {
  const wall = new Date(`${value}:00Z`).getTime()
  if (!Number.isFinite(wall)) throw new Error('날짜와 시각을 확인해 주세요.')
  const offsets = new Set<number>()
  for (let hours = -36; hours <= 36; hours += 6) {
    const sample = wall + hours * 3_600_000
    offsets.add(new Date(`${localDateTime(new Date(sample), timezone)}Z`).getTime() - sample)
  }
  const matches = [...offsets].map(offset => wall - offset)
    .filter(instant => localDateTime(new Date(instant), timezone).slice(0, 16) === value).sort((a, b) => a - b)
  if (!matches.length) throw new Error('이 시간대에 존재하지 않는 시각입니다. 다른 시각을 선택해 주세요.')
  // DST 종료로 두 번 발생하는 시각은 먼저 발생한 시각으로 저장한다.
  return new Date(matches[0]!).toISOString()
}

export function calendarRange(date: string, view: 'month' | 'week' | 'day') {
  if (view === 'day') return { from: date, to: addDays(date, 1) }
  if (view === 'week') {
    const day = new Date(`${date}T12:00:00Z`).getUTCDay()
    const from = addDays(date, -(day + 6) % 7)
    return { from, to: addDays(from, 7) }
  }
  const first = `${date.slice(0, 7)}-01`
  const from = addDays(first, -(new Date(`${first}T12:00:00Z`).getUTCDay() + 6) % 7)
  return { from, to: addDays(from, 42) }
}
