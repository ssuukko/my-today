# Investment API

Manual records only. Every route requires a session and writes require CSRF. Every decimal in responses is a string, including zero; never combine currencies. All data is scoped to the authenticated user, including administrators.

`GET /api/v1/invest` returns `{instruments: Instrument[], trades: Trade[], totals: Total[]}` (up to 100 instruments and latest 200 trades).

- Instrument: `{id,exchange,symbol,name,currency,price:string|null,quotedAt:string|null,quoteType:'MANUAL'|'UNKNOWN',quantity:string,remainingCost:string,realizedPnl:string,marketValue:string|null,unrealizedPnl:string|null}`. Missing price means null valuation; manual prices are estimates. `quotedAt` ISO offset timestamp, never real-time provider data.
- Trade: `{id,instrumentId,side:'BUY'|'SELL',quantity:string,price:string,fees:string,tax:string,tradedAt:string,voided:boolean,realizedPnl:string}`.
- Total: `{currency,remainingCost:string,realizedPnl:string,marketValue:string|null,unrealizedPnl:string|null,unpricedCount:number}`. If any positive holding lacks a price, total marketValue/unrealizedPnl are null.

`POST /api/v1/invest/instruments` body `{exchange,symbol,name,currency}` returns Instrument (201). Exchange and symbol max20, name max100; KRW/USD/JPY. Exchange/symbol uppercase, unique per user. Maximum 100 instruments.

`DELETE /api/v1/invest/instruments/{id}` returns204; only instruments without any trades can be deleted.

`PUT /api/v1/invest/instruments/{id}/quote` body `{price:'123.45',quotedAt:'2026-11-08T12:00:00+09:00'}` returns Instrument. Price positive, at most 6 decimal places; timestamp must be 1900 or later and cannot be in future (>5min). Manual price metadata always displayed.

`POST /api/v1/invest/trades` body `{requestId:UUID,instrumentId:UUID,side:'BUY'|'SELL',quantity:'2.5',price:'100',fees:'1',tax:'0',tradedAt:ISOoffset}` returns Trade (201). All decimals max6places, integer part max14digits; quantity/price positive, fees/tax nonnegative. Max product 28integer digits. Fees/tax default '0'. Chronological append-only per instrument (equal timestamps allowed, insertion order preserved); timestamps before1900 or >5minutes into future rejected. Cannot sell more than held or have negative sale proceeds. Moving average cost includes buy fees/tax; sell realized profit = proceeds minus fees/tax minus allocated cost. Calculation uses 12 decimal internal precision and half-even rounding, final sale removes all remaining cost. Repeated requestId+same normalized payload returns same trade; different payload409. Transactions serialized per user to prevent concurrent duplicate/oversell.

`POST /api/v1/invest/trades/{id}/void` (no body) returns Trade. Only latest nonvoid trade for that instrument can be voided; balances recompute; repeated void is safe. Trade history retained. Corrections: void latest trade then record corrected trade with a fresh requestId.

Errors: 400 invalid input;401 no session;403 missingCSRF;404 other user's/missingrecord;409 duplicateinstrument, stale quote update, backdatedtrade, oversell, or nonlatestvoid. No money ledger link: investment cashflows are separate and are not added to household income or net assets.
