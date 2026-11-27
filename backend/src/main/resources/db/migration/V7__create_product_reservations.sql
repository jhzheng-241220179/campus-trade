
-- Day 25: 商品预约轮次与买家预约记录

CREATE TABLE reservation_rounds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',

    first_reserved_at DATETIME(3) NOT NULL,
    priority_ends_at DATETIME(3) NOT NULL,
    cooling_ends_at DATETIME(3) NOT NULL,
    seller_response_deadline DATETIME(3) NOT NULL,

    frozen_at DATETIME(3) NULL,
    seller_responded_at DATETIME(3) NULL,
    matching_deadline DATETIME(3) NULL,
    closed_at DATETIME(3) NULL,

    close_reason VARCHAR(40) NULL,

    draw_version INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,

    created_at DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3),

    updated_at DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    active_product_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN status IN (
                'OPEN',
                'FROZEN',
                'MATCHING'
            )
            THEN product_id
            ELSE NULL
        END
    ) STORED,

    PRIMARY KEY (id),

    UNIQUE KEY uk_round_active_product (
        active_product_id
    ),

    KEY idx_round_product (
        product_id,
        created_at
    ),

    KEY idx_round_cooling (
        status,
        cooling_ends_at
    ),

    KEY idx_round_seller_deadline (
        status,
        seller_response_deadline
    ),

    KEY idx_round_matching_deadline (
        status,
        matching_deadline
    ),

    CONSTRAINT fk_round_product
        FOREIGN KEY (product_id)
        REFERENCES products(id),

    CONSTRAINT chk_round_status
        CHECK (
            status IN (
                'OPEN',
                'FROZEN',
                'MATCHING',
                'COMPLETED',
                'CANCELLED'
            )
        ),

    CONSTRAINT chk_round_time_sequence
        CHECK (
            first_reserved_at < priority_ends_at
            AND priority_ends_at < cooling_ends_at
            AND cooling_ends_at < seller_response_deadline
        ),

    CONSTRAINT chk_round_versions
        CHECK (
            version >= 0
            AND draw_version >= 0
        )

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE product_reservations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    round_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    reservation_order INT NOT NULL,

    draw_weight INT NULL,
    queue_position INT NULL,

    invited_at DATETIME(3) NULL,
    invitation_deadline DATETIME(3) NULL,
    responded_at DATETIME(3) NULL,
    cancelled_at DATETIME(3) NULL,

    cancel_reason VARCHAR(40) NULL,

    version INT NOT NULL DEFAULT 0,

    created_at DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3),

    updated_at DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    invited_round_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN status = 'INVITED'
            THEN round_id
            ELSE NULL
        END
    ) STORED,

    PRIMARY KEY (id),

    UNIQUE KEY uk_reservation_round_buyer (
        round_id,
        buyer_id
    ),

    UNIQUE KEY uk_reservation_round_order (
        round_id,
        reservation_order
    ),

    UNIQUE KEY uk_reservation_queue_position (
        round_id,
        queue_position
    ),

    UNIQUE KEY uk_reservation_invited_round (
        invited_round_id
    ),

    KEY idx_reservation_buyer_status (
        buyer_id,
        status
    ),

    KEY idx_reservation_round_status (
        round_id,
        status
    ),

    KEY idx_reservation_invitation_deadline (
        status,
        invitation_deadline
    ),

    CONSTRAINT fk_reservation_round
        FOREIGN KEY (round_id)
        REFERENCES reservation_rounds(id),

    CONSTRAINT fk_reservation_buyer
        FOREIGN KEY (buyer_id)
        REFERENCES users(id),

    CONSTRAINT chk_reservation_status
        CHECK (
            status IN (
                'ACTIVE',
                'INVITED',
                'ACCEPTED',
                'DECLINED',
                'EXPIRED',
                'CANCELLED'
            )
        ),

    CONSTRAINT chk_reservation_order
        CHECK (reservation_order > 0),

    CONSTRAINT chk_reservation_weight
        CHECK (
            draw_weight IS NULL
            OR draw_weight > 0
        ),

    CONSTRAINT chk_reservation_queue
        CHECK (
            queue_position IS NULL
            OR queue_position > 0
        ),

    CONSTRAINT chk_reservation_version
        CHECK (version >= 0),

    CONSTRAINT chk_reservation_invitation_time
        CHECK (
            invitation_deadline IS NULL
            OR (
                invited_at IS NOT NULL
                AND invitation_deadline > invited_at
            )
        )

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
