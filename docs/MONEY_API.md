# Money API

Authenticated own-user data only; all money amounts are decimal **strings** (maximum 14 integer digits / 6 decimals). Supported currencies KRW/USD/JPY; reports never add currencies together. Dates are user's explicitly chosen local calendar dates. No automatic bank integration.

`GET /api/v1/money?month=2026-11` defaults user's current month.
```json
{"month":"2026-11","accounts":[{"id":"uuid","name":"은행","type":"CHECKING","currency":"KRW","openingBalance":"100000","balance":"95000"}],"transactions":[{"id":"uuid","requestId":"uuid","type":"EXPENSE","accountId":"uuid","targetAccountId":null,"amount":"5000","currency":"KRW","category":"식비","transactionDate":"2026-11-08","memo":"점심","status":"POSTED","reversalOf":null}],"totals":[{"currency":"KRW","income":"0","expense":"5000","net":"-5000"}],"budgets":[{"id":"uuid","category":"식비","currency":"KRW","amount":"300000","spent":"5000","remaining":"295000","version":0}],"hasMore":false}
```
Accounts max100; budgets max100 per month. Latest 200 transactions in chosen month (including VOID/REVERSAL audit records), `hasMore` warns if truncated; totals/budgets use all records.

- `POST /money/accounts` →201 Account. `{name,type,currency,openingBalance}` types CASH/CHECKING/SAVINGS/CREDIT_CARD_LIABILITY/LOAN_LIABILITY/BROKER_CASH/OTHER_ASSET. Opening balance decimal string, can be negative; liabilities require <=0, assets >=0. Account edits/deletion excluded to preserve ledger.
- `POST /money/transactions` →201 Transaction. `{requestId,type,accountId,targetAccountId?,amount,category?,transactionDate,memo?}` type INCOME/EXPENSE/TRANSFER, amount positive decimal string. Income/expense require category (max60). Transfers require a different owned same-currency target and category omitted. requestId is client-generated UUID; retry identical payload returns same Transaction, changed payload409. Memo max1000. No pending/future planned transactions; transactionDate cannot be after user's today. Expense from liability makes balance more negative; payment to liability uses TRANSFER.
- `POST /money/transactions/{id}/reverse` →200 original Transaction now `status=VOID`. No body. Idempotent. Writes an immutable REVERSAL transaction with opposite postings, original date/category and `reversalOf=id`. Original and reversal remain visible; neither contributes monthly income/expense. Correct mistakes by reversing and creating a new transaction. Full cancellation/refund only; partial refunds deferred.
- `PUT /money/budgets` →200 Budget. `{month:"2026-11",category,currency,amount,version?}` amount positive decimal string; omit version for create, existing record requires matching version, stale409. Budget report includes amount/spent/remaining, expenses minus full reversals. Budget and bank balance are different values.

Validation400, unauthenticated401, CSRF403, foreign record404, changed idempotency payload/stale budget409. Ledger postings commit atomically and balance to zero per currency. Virtual income/expense postings are excluded from cash assets. No investment value is merged into cash.
