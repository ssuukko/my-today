CREATE TABLE money_account (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id), name VARCHAR(100) NOT NULL,
 type VARCHAR(30) NOT NULL CHECK(type IN ('CASH','CHECKING','SAVINGS','CREDIT_CARD_LIABILITY','LOAN_LIABILITY','BROKER_CASH','OTHER_ASSET')),
 currency VARCHAR(3) NOT NULL CHECK(currency IN ('KRW','USD','JPY')), opening_balance NUMERIC(20,6) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(id,user_id), UNIQUE(id,currency)
);
CREATE INDEX money_account_owner ON money_account(user_id);
CREATE TABLE money_transaction (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id), request_id UUID NOT NULL,
 type VARCHAR(10) NOT NULL CHECK(type IN ('INCOME','EXPENSE','TRANSFER','REVERSAL')),
 account_id UUID NOT NULL, target_account_id UUID,
 amount NUMERIC(20,6) NOT NULL CHECK(amount>0), currency VARCHAR(3) NOT NULL,
 category VARCHAR(60), transaction_date DATE NOT NULL, memo VARCHAR(1000),
 status VARCHAR(10) NOT NULL DEFAULT 'POSTED' CHECK(status IN ('POSTED','VOID')),
 reversal_of UUID UNIQUE REFERENCES money_transaction(id), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(user_id,request_id),
 FOREIGN KEY(account_id,user_id) REFERENCES money_account(id,user_id),
 FOREIGN KEY(target_account_id,user_id) REFERENCES money_account(id,user_id),
 FOREIGN KEY(account_id,currency) REFERENCES money_account(id,currency),
 FOREIGN KEY(target_account_id,currency) REFERENCES money_account(id,currency),
 CHECK((type='TRANSFER' AND target_account_id IS NOT NULL AND target_account_id<>account_id AND category IS NULL AND reversal_of IS NULL)
 OR (type IN ('INCOME','EXPENSE') AND target_account_id IS NULL AND category IS NOT NULL AND reversal_of IS NULL)
 OR (type='REVERSAL' AND reversal_of IS NOT NULL))
);
CREATE INDEX money_transaction_owner_date ON money_transaction(user_id,transaction_date,id);
CREATE TABLE money_posting (
 id UUID PRIMARY KEY, transaction_id UUID NOT NULL REFERENCES money_transaction(id),
 account_id UUID REFERENCES money_account(id), currency VARCHAR(3) NOT NULL CHECK(currency IN ('KRW','USD','JPY')),
 amount NUMERIC(20,6) NOT NULL CHECK(amount<>0),
 FOREIGN KEY(account_id,currency) REFERENCES money_account(id,currency)
);
CREATE INDEX money_posting_account ON money_posting(account_id);
CREATE TABLE money_budget (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id), month DATE NOT NULL CHECK(EXTRACT(DAY FROM month)=1),
 category VARCHAR(60) NOT NULL, currency VARCHAR(3) NOT NULL CHECK(currency IN ('KRW','USD','JPY')),
 amount NUMERIC(20,6) NOT NULL CHECK(amount>0), version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(user_id,month,category,currency)
);
