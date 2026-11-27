
-- Day 26: 预约抽签历史与结果快照

-- 为跨表复合外键提供唯一索引。
ALTER TABLE product_reservations
    ADD UNIQUE KEY uk_reservation_round_id (
        round_id,
        id
    );


CREATE TABLE reservation_draws (
    id BIGINT NOT NULL AUTO_INCREMENT,

    round_id BIGINT NOT NULL,

    draw_version INT NOT NULL,

    trigger_type VARCHAR(40) NOT NULL,

    trigger_reservation_id BIGINT NULL,

    participant_count INT NOT NULL,

    total_weight BIGINT NOT NULL,

    created_at DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),

    UNIQUE KEY uk_draw_round_version (
        round_id,
        draw_version
    ),

    UNIQUE KEY uk_draw_round_id (
        round_id,
        id
    ),

    KEY idx_draw_round_created (
        round_id,
        created_at
    ),

    CONSTRAINT fk_draw_round
        FOREIGN KEY (round_id)
        REFERENCES reservation_rounds(id),

    CONSTRAINT fk_draw_trigger_reservation
        FOREIGN KEY (
            round_id,
            trigger_reservation_id
        )
        REFERENCES product_reservations (
            round_id,
            id
        ),

    CONSTRAINT chk_draw_version
        CHECK (draw_version > 0),

    CONSTRAINT chk_draw_trigger_type
        CHECK (
            trigger_type IN (
                'PRIORITY_ENDED',
                'NEW_RESERVATION'
            )
        ),

    CONSTRAINT chk_draw_trigger_reservation
        CHECK (
            (
                trigger_type = 'PRIORITY_ENDED'
                AND trigger_reservation_id IS NULL
            )
            OR
            (
                trigger_type = 'NEW_RESERVATION'
                AND trigger_reservation_id IS NOT NULL
            )
        ),

    CONSTRAINT chk_draw_participant_count
        CHECK (participant_count > 0),

    CONSTRAINT chk_draw_total_weight
        CHECK (
            total_weight >= participant_count
        )

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE reservation_draw_entries (
    draw_id BIGINT NOT NULL,

    round_id BIGINT NOT NULL,

    reservation_id BIGINT NOT NULL,

    reservation_order INT NOT NULL,

    draw_weight INT NOT NULL,

    queue_position INT NOT NULL,

    PRIMARY KEY (
        draw_id,
        reservation_id
    ),

    UNIQUE KEY uk_draw_entry_queue (
        draw_id,
        queue_position
    ),

    UNIQUE KEY uk_draw_entry_order (
        draw_id,
        reservation_order
    ),

    KEY idx_draw_entry_reservation (
        reservation_id,
        draw_id
    ),

    CONSTRAINT fk_draw_entry_draw
        FOREIGN KEY (
            round_id,
            draw_id
        )
        REFERENCES reservation_draws (
            round_id,
            id
        ),

    CONSTRAINT fk_draw_entry_reservation
        FOREIGN KEY (
            round_id,
            reservation_id
        )
        REFERENCES product_reservations (
            round_id,
            id
        ),

    CONSTRAINT chk_draw_entry_order
        CHECK (reservation_order > 0),

    CONSTRAINT chk_draw_entry_weight
        CHECK (draw_weight > 0),

    CONSTRAINT chk_draw_entry_queue
        CHECK (queue_position > 0)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
