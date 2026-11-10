export interface Activity { id: string; title: string; category: string; activityDate: string; durationMinutes: number; quantity: string | null; unit: string | null; notes: string | null; version: number }
export interface Goal { id: string; title: string; category: string; startDate: string; endDate: string; targetMinutes: number; actualMinutes: number; version: number }
export interface Routine { id: string; title: string; weekdays: number[]; startDate: string; targetMinutes: number; active: boolean; version: number }
export interface Occurrence { routineId: string; title: string; scheduledDate: string; targetMinutes: number; status: 'PENDING' | 'DONE' | 'SKIPPED'; version: number }
export interface Reflection { date: string; well: string; missed: string; next: string; version: number }
export interface Growth { date: string; timezone: string; totalMinutes: number; activityCount: number; activities: Activity[]; nextActivityCursor: string | null; routines: Routine[]; occurrences: Occurrence[]; goals: Goal[]; reflection: Reflection | null }
export interface Account { id: string; name: string; type: string; currency: string; openingBalance: string; balance: string }
export interface MoneyTransaction { id: string; requestId: string; type: 'INCOME' | 'EXPENSE' | 'TRANSFER' | 'REVERSAL'; accountId: string; targetAccountId: string | null; amount: string; currency: string; category: string | null; transactionDate: string; memo: string | null; status: 'POSTED' | 'VOID'; reversalOf: string | null }
export interface Budget { id: string; category: string; currency: string; amount: string; spent: string; remaining: string; version: number }
export interface Money { month: string; accounts: Account[]; transactions: MoneyTransaction[]; budgets: Budget[]; totals: { currency: string; income: string; expense: string; net: string }[]; hasMore: boolean }
export interface Instrument { id: string; exchange: string; symbol: string; name: string; currency: string; price: string | null; quotedAt: string | null; quoteType: 'MANUAL' | 'UNKNOWN'; quantity: string; remainingCost: string; realizedPnl: string; marketValue: string | null; unrealizedPnl: string | null }
export interface Trade { id: string; instrumentId: string; side: 'BUY' | 'SELL'; quantity: string; price: string; fees: string; tax: string; tradedAt: string; voided: boolean; realizedPnl: string }
export interface Investment { instruments: Instrument[]; trades: Trade[]; totals: { currency: string; remainingCost: string; realizedPnl: string; marketValue: string | null; unrealizedPnl: string | null; unpricedCount: number }[] }
export const currencies = ['KRW', 'USD', 'JPY']
