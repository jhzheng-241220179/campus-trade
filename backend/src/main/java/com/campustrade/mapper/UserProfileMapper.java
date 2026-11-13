
package com.campustrade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserProfileMapper {

    @Update("""
            UPDATE users
            SET nickname = #{nickname},
                updated_at = CURRENT_TIMESTAMP
            WHERE email = #{email}
              AND status = 'ACTIVE'
              AND email_verified = TRUE
            """)
    int updateNickname(
            @Param("email") String email,
            @Param("nickname") String nickname
    );
}
