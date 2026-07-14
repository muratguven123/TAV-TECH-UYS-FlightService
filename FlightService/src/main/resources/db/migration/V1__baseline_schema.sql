-- FIX: DEF-015 — FlightService baseline schema
-- Mevcut production schema'sının Flyway'e taşınması.
-- ddl-auto:update yerine Flyway yönetimi; Hibernate sadece validate eder.

CREATE TABLE IF NOT EXISTS flights (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    flight_number        VARCHAR(6)   NOT NULL,
    airline_code         VARCHAR(2)   NOT NULL,
    aircraft_tail_number VARCHAR(10)  NOT NULL,
    origin_station       VARCHAR(4)   NOT NULL,
    destination_station  VARCHAR(4)   NOT NULL,
    flight_type          VARCHAR(20)  NOT NULL,
    scheduled_departure  DATETIME(6)  NOT NULL,
    scheduled_arrival    DATETIME(6)  NOT NULL,
    flight_date          DATE         NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_flight_no_dep UNIQUE (flight_number, scheduled_departure),
    INDEX idx_flight_date (flight_date),
    INDEX idx_flight_number (flight_number)
);
