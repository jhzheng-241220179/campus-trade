
package com.campustrade.mapper;

import com.campustrade.model.ReservationDraw;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReservationDrawMapper {

    @Insert("""
            INSERT INTO reservation_draws (
                round_id,
                draw_version,
                trigger_type,
                trigger_reservation_id,
                participant_count,
                total_weight,
                created_at
            )
            VALUES (
                #{roundId},
                #{drawVersion},
                #{triggerType},
                #{triggerReservationId},
                #{participantCount},
                #{totalWeight},
                #{createdAt}
            )
            """)
    @Options(
            useGeneratedKeys = true,
            keyProperty = "id"
    )
    int insert(ReservationDraw draw);

    @Select("""
            SELECT *
            FROM reservation_draws
            WHERE id = #{id}
            """)
    ReservationDraw findById(
            @Param("id") Long id
    );

    @Select("""
            SELECT *
            FROM reservation_draws
            WHERE round_id = #{roundId}
              AND draw_version = #{drawVersion}
            """)
    ReservationDraw findByRoundAndVersion(
            @Param("roundId") Long roundId,
            @Param("drawVersion") Integer drawVersion
    );

    @Select("""
            SELECT *
            FROM reservation_draws
            WHERE round_id = #{roundId}
            ORDER BY draw_version ASC
            """)
    List<ReservationDraw> findByRoundId(
            @Param("roundId") Long roundId
    );

    @Select("""
            SELECT *
            FROM reservation_draws
            WHERE round_id = #{roundId}
            ORDER BY draw_version DESC
            LIMIT 1
            """)
    ReservationDraw findLatestByRoundId(
            @Param("roundId") Long roundId
    );
}
