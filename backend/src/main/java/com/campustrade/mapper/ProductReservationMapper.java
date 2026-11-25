
package com.campustrade.mapper;

import com.campustrade.model.ProductReservation;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProductReservationMapper {

    @Select("""
            SELECT *
            FROM product_reservations
            WHERE id = #{id}
            """)
    ProductReservation findById(
            @Param("id") Long id
    );

    @Select("""
            SELECT *
            FROM product_reservations
            WHERE round_id = #{roundId}
              AND buyer_id = #{buyerId}
            """)
    ProductReservation findByRoundAndBuyer(
            @Param("roundId") Long roundId,
            @Param("buyerId") Long buyerId
    );

    @Select("""
            SELECT *
            FROM product_reservations
            WHERE round_id = #{roundId}
              AND buyer_id = #{buyerId}
            FOR UPDATE
            """)
    ProductReservation findByRoundAndBuyerForUpdate(
            @Param("roundId") Long roundId,
            @Param("buyerId") Long buyerId
    );

    @Select("""
            SELECT *
            FROM product_reservations
            WHERE round_id = #{roundId}
            ORDER BY reservation_order ASC
            """)
    List<ProductReservation> findByRoundId(
            @Param("roundId") Long roundId
    );

    @Select("""
            SELECT *
            FROM product_reservations
            WHERE round_id = #{roundId}
              AND status = 'ACTIVE'
            ORDER BY reservation_order ASC
            FOR UPDATE
            """)
    List<ProductReservation> findActiveByRoundIdForUpdate(
            @Param("roundId") Long roundId
    );

    @Select("""
            SELECT COUNT(*)
            FROM product_reservations
            WHERE round_id = #{roundId}
              AND status IN ('ACTIVE', 'INVITED')
            """)
    int countActiveByRoundId(
            @Param("roundId") Long roundId
    );

    @Select("""
            SELECT COUNT(*)
            FROM product_reservations pr
            JOIN reservation_rounds rr
              ON pr.round_id = rr.id
            WHERE pr.buyer_id = #{buyerId}
              AND pr.status IN ('ACTIVE', 'INVITED')
              AND rr.status IN (
                  'OPEN',
                  'FROZEN',
                  'MATCHING'
              )
            """)
    int countActiveByBuyerId(
            @Param("buyerId") Long buyerId
    );

    @Select("""
            SELECT COALESCE(
                MAX(reservation_order),
                0
            )
            FROM product_reservations
            WHERE round_id = #{roundId}
            """)
    int findMaxReservationOrder(
            @Param("roundId") Long roundId
    );

    @Select("""
            SELECT MAX(pr.cancelled_at)
            FROM product_reservations pr
            JOIN reservation_rounds rr
              ON pr.round_id = rr.id
            WHERE rr.product_id = #{productId}
              AND pr.buyer_id = #{buyerId}
              AND pr.status = 'CANCELLED'
              AND pr.cancel_reason = 'BUYER_CANCELLED'
            """)
    LocalDateTime findLatestBuyerCancellation(
            @Param("productId") Long productId,
            @Param("buyerId") Long buyerId
    );

    @Insert("""
            INSERT INTO product_reservations (
                round_id,
                buyer_id,
                status,
                reservation_order,
                created_at
            )
            VALUES (
                #{roundId},
                #{buyerId},
                #{status},
                #{reservationOrder},
                #{createdAt}
            )
            """)
    @Options(
            useGeneratedKeys = true,
            keyProperty = "id"
    )
    int insert(ProductReservation reservation);

    @Update("""
            UPDATE product_reservations
            SET status = 'CANCELLED',
                cancelled_at = #{now},
                cancel_reason = 'BUYER_CANCELLED',
                queue_position = NULL,
                version = version + 1
            WHERE id = #{id}
              AND buyer_id = #{buyerId}
              AND status = 'ACTIVE'
              AND version = #{version}
            """)
    int cancelByBuyer(
            @Param("id") Long id,
            @Param("buyerId") Long buyerId,
            @Param("now") LocalDateTime now,
            @Param("version") Integer version
    );

    @Update("""
            UPDATE product_reservations
            SET status = 'CANCELLED',
                cancelled_at = #{now},
                cancel_reason = #{reason},
                queue_position = NULL,
                version = version + 1
            WHERE round_id = #{roundId}
              AND status IN ('ACTIVE', 'INVITED')
            """)
    int cancelAllActiveByRoundId(
            @Param("roundId") Long roundId,
            @Param("reason") String reason,
            @Param("now") LocalDateTime now
    );
}
