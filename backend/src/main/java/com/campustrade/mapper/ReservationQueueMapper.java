
package com.campustrade.mapper;

import com.campustrade.model.ProductReservation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReservationQueueMapper {

    // 按当前候补顺序查询并锁定有效预约。
    @Select("""
            SELECT *
            FROM product_reservations
            WHERE round_id = #{roundId}
              AND status = 'ACTIVE'
            ORDER BY
                queue_position IS NULL ASC,
                queue_position ASC,
                reservation_order ASC
            FOR UPDATE
            """)
    List<ProductReservation> findActiveQueueForUpdate(
            @Param("roundId") Long roundId
    );

    // 清空旧排名，避免重新排序时触发唯一约束冲突。
    @Update("""
            UPDATE product_reservations
            SET queue_position = NULL,
                version = version + 1
            WHERE round_id = #{roundId}
              AND status = 'ACTIVE'
              AND queue_position IS NOT NULL
            """)
    int clearActiveQueuePositions(
            @Param("roundId") Long roundId
    );

    // 随机抽签完成后，同时更新权重和排名。
    @Update("""
            UPDATE product_reservations
            SET draw_weight = #{drawWeight},
                queue_position = #{queuePosition},
                version = version + 1
            WHERE id = #{reservationId}
              AND round_id = #{roundId}
              AND status = 'ACTIVE'
            """)
    int updateDrawResult(
            @Param("roundId") Long roundId,
            @Param("reservationId") Long reservationId,
            @Param("drawWeight") Integer drawWeight,
            @Param("queuePosition") Integer queuePosition
    );

    // 非随机抽签操作，只修改候补排名。
    @Update("""
            UPDATE product_reservations
            SET queue_position = #{queuePosition},
                version = version + 1
            WHERE id = #{reservationId}
              AND round_id = #{roundId}
              AND status = 'ACTIVE'
            """)
    int updateQueuePosition(
            @Param("roundId") Long roundId,
            @Param("reservationId") Long reservationId,
            @Param("queuePosition") Integer queuePosition
    );

    // 完成真正的随机抽签后，增加两个版本号。
    @Update("""
            UPDATE reservation_rounds
            SET draw_version = draw_version + 1,
                version = version + 1
            WHERE id = #{roundId}
              AND status = 'OPEN'
              AND draw_version = #{expectedDrawVersion}
              AND version = #{expectedVersion}
            """)
    int advanceDrawVersion(
            @Param("roundId") Long roundId,
            @Param("expectedDrawVersion")
            Integer expectedDrawVersion,
            @Param("expectedVersion")
            Integer expectedVersion
    );

    // 队列顺延或优先期队列更新时，只增加乐观锁版本。
    @Update("""
            UPDATE reservation_rounds
            SET version = version + 1
            WHERE id = #{roundId}
              AND status IN ('OPEN', 'FROZEN')
              AND version = #{expectedVersion}
            """)
    int advanceQueueVersion(
            @Param("roundId") Long roundId,
            @Param("expectedVersion")
            Integer expectedVersion
    );

    // 查找已经结束优先保护期，但尚未执行首次随机抽签的轮次。
    @Select("""
            SELECT id
            FROM reservation_rounds
            WHERE status = 'OPEN'
              AND draw_version = 0
              AND priority_ends_at <= #{now}
              AND seller_response_deadline > #{now}
            ORDER BY priority_ends_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<Long> findPriorityEndedRoundIds(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    // 查找已经达到预约截止时间的待冻结轮次。
    @Select("""
            SELECT id
            FROM reservation_rounds
            WHERE status = 'OPEN'
              AND cooling_ends_at <= #{now}
              AND seller_response_deadline > #{now}
            ORDER BY cooling_ends_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<Long> findCoolingEndedRoundIds(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );
}
