
package com.campustrade.mapper;

import com.campustrade.model.ReservationRound;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ReservationRoundMapper {

    @Select("""
            SELECT *
            FROM reservation_rounds
            WHERE id = #{id}
            """)
    ReservationRound findById(
            @Param("id") Long id
    );

    @Select("""
            SELECT *
            FROM reservation_rounds
            WHERE id = #{id}
            FOR UPDATE
            """)
    ReservationRound findByIdForUpdate(
            @Param("id") Long id
    );

    @Select("""
            SELECT *
            FROM reservation_rounds
            WHERE active_product_id = #{productId}
            """)
    ReservationRound findActiveByProductId(
            @Param("productId") Long productId
    );

    @Select("""
            SELECT *
            FROM reservation_rounds
            WHERE active_product_id = #{productId}
            FOR UPDATE
            """)
    ReservationRound findActiveByProductIdForUpdate(
            @Param("productId") Long productId
    );

    @Insert("""
            INSERT INTO reservation_rounds (
                product_id,
                status,
                first_reserved_at,
                priority_ends_at,
                cooling_ends_at,
                seller_response_deadline
            )
            VALUES (
                #{productId},
                #{status},
                #{firstReservedAt},
                #{priorityEndsAt},
                #{coolingEndsAt},
                #{sellerResponseDeadline}
            )
            """)
    @Options(
            useGeneratedKeys = true,
            keyProperty = "id"
    )
    int insert(ReservationRound round);

    @Update("""
            UPDATE reservation_rounds
            SET status = 'FROZEN',
                frozen_at = #{now},
                version = version + 1
            WHERE id = #{id}
              AND status = 'OPEN'
              AND cooling_ends_at <= #{now}
              AND version = #{version}
            """)
    int freeze(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("version") Integer version
    );

    @Update("""
            UPDATE reservation_rounds
            SET status = 'CANCELLED',
                close_reason = #{reason},
                closed_at = #{now},
                version = version + 1
            WHERE id = #{id}
              AND status IN ('OPEN', 'FROZEN')
              AND version = #{version}
            """)
    int cancel(
            @Param("id") Long id,
            @Param("reason") String reason,
            @Param("now") LocalDateTime now,
            @Param("version") Integer version
    );
}
