-- ============================================================
--  QueueCare Database Schema
--  Database: queuecare_db
-- ============================================================

USE queuecare_db;

-- ---------------------------------------------------------------
-- citizens: persists every citizen that has ever been registered
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS citizens (
    id            BIGINT          AUTO_INCREMENT PRIMARY KEY,
    token         VARCHAR(20)     NOT NULL UNIQUE,
    name          VARCHAR(60)     NOT NULL,
    service_type  ENUM('GENERAL_SERVICES','DOCUMENT_SERVICES','ACCESSIBILITY_SUPPORT') NOT NULL,
    category      ENUM('PRIORITY_ASSISTANCE','STANDARD') NOT NULL,
    status        ENUM('WAITING','CALLED','SERVED','SKIPPED') NOT NULL DEFAULT 'WAITING',
    arrival_order INT             NOT NULL,
    wait_minutes  INT             NOT NULL DEFAULT 0,
    enqueued_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    served_at     DATETIME        NULL,
    session_date  DATE            NOT NULL DEFAULT (CURDATE()),
    INDEX idx_status       (status),
    INDEX idx_session_date (session_date),
    INDEX idx_token        (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------
-- queue_sessions: daily aggregate log
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS queue_sessions (
    id               BIGINT   AUTO_INCREMENT PRIMARY KEY,
    session_date     DATE     NOT NULL,
    total_registered INT      NOT NULL DEFAULT 0,
    total_served     INT      NOT NULL DEFAULT 0,
    total_skipped    INT      NOT NULL DEFAULT 0,
    avg_wait_minutes DOUBLE   NOT NULL DEFAULT 0,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_session_date (session_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
