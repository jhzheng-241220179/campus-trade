
package com.campustrade.mapper;

import com.campustrade.model.ReservationDrawEntry;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReservationDrawEntryMapper {

    @Insert("""
            <script>
            INSERT INTO reservation_draw_entries (
                draw_id,
                round_id,
                reservation_id,
                reservation_order,
                draw_weight,
                queue_position
            )
            VALUES
            <foreach collection="entries"
                     item="entry"
                     separator=",">
                (
                    #{entry.drawId},
                    #{entry.roundId},
                    #{entry.reservationId},
                    #{entry.reservationOrder},
                    #{entry.drawWeight},
                    #{entry.queuePosition}
                )
            </foreach>
            </script>
            """)
    int insertBatch(
            @Param("entries")
            List<ReservationDrawEntry> entries
    );

    @Select("""
            SELECT
                draw_id,
                round_id,
                reservation_id,
                reservation_order,
                draw_weight,
                queue_position
            FROM reservation_draw_entries
            WHERE draw_id = #{drawId}
            ORDER BY queue_position ASC
            """)
    @ConstructorArgs({
        @Arg(column = "draw_id", javaType = Long.class),
        @Arg(column = "round_id", javaType = Long.class),
        @Arg(column = "reservation_id", javaType = Long.class),
        @Arg(column = "reservation_order", javaType = int.class),
        @Arg(column = "draw_weight", javaType = int.class),
        @Arg(column = "queue_position", javaType = int.class)
    })
    List<ReservationDrawEntry> findByDrawId(
            @Param("drawId") Long drawId
    );
}
