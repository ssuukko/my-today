export interface Task {
  id: string
  title: string
  status: 'TODO' | 'IN_PROGRESS' | 'DONE' | 'CANCELED'
  priority: 'P0' | 'P1' | 'P2' | 'P3'
  dueDate: string | null
  dueAt: string | null
  estimatedMinutes: number | null
  completedAt: string | null
  version: number
}

export interface CalendarEvent {
  id: string
  title: string
  allDay: boolean
  startAt: string | null
  endAt: string | null
  startDate: string | null
  endDateExclusive: string | null
  timezone: string
  location: string | null
  notes: string | null
  version: number
}

export interface Page<T> { items: T[]; nextCursor: string | null }
export interface Dashboard {
  date: string
  timezone: string
  events: CalendarEvent[]
  tasks: Task[]
  eventCount: number
  taskCount: number
  completedTaskCount: number
}

export const taskStatuses = [
  { label: '할 일', value: 'TODO' }, { label: '진행 중', value: 'IN_PROGRESS' },
  { label: '완료', value: 'DONE' }, { label: '취소', value: 'CANCELED' },
]
export const taskPriorities = ['P0', 'P1', 'P2', 'P3']
