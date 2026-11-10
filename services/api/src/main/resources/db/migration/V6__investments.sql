CREATE TABLE investment_instrument (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES app_user(id),
 exchange VARCHAR(20) NOT NULL,
 symbol VARCHAR(20) NOT NULL,
 name VARCHAR(100) NOT NULL,
 currency VARCHAR(3) NOT NULL CHECK(currency IN ('KRW','USD','JPY')),
 price NUMERIC(20,6) CHECK(price > 0),
 quoted_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(user_id,exchange,symbol),
 UNIQUE(id,user_id),
 CHECK((price IS NULL) = (quoted_at IS NULL))
);
CREATE TABLE investment_trade (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES app_user(id),
 instrument_id UUID NOT NULL,
 request_id UUID NOT NULL,
 side VARCHAR(4) NOT NULL CHECK(side IN ('BUY','SELL')),
 quantity NUMERIC(20,6) NOT NULL CHECK(quantity>0),
 price NUMERIC(20,6) NOT NULL CHECK(price>0),
 fees NUMERIC(20,6) NOT NULL CHECK(fees>=0),
 tax NUMERIC(20,6) NOT NULL CHECK(tax>=0),
 traded_at TIMESTAMPTZ NOT NULL,
 sequence BIGINT GENERATED ALWAYS AS IDENTITY,
 voided BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(instrument_id,user_id) REFERENCES investment_instrument(id,user_id),
 UNIQUE(user_id,request_id)
);
CREATE INDEX investment_trade_owner_instrument ON investment_trade(user_id,instrument_id,sequence);
