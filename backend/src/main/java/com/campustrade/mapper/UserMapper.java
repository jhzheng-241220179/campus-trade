
package com.campustrade.mapper;

import com.campustrade.model.User;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM users WHERE email = #{email}")
    User findByEmail(String email);

    @Select("""
        SELECT *
        FROM users
        WHERE id = #{id}
        FOR UPDATE
        """)
    User findByIdForUpdate(@Param("id") Long id);

    @Update("""
        UPDATE users
        SET email_verified = TRUE,
            status = 'ACTIVE',
            verification_failure_count = 0,
            verification_locked_until = NULL,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
          AND email_verified = FALSE
          AND status = 'PENDING_VERIFICATION'
        """)
    int activateEmail(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM users WHERE id = #{id}")
    int countById(Long id);

    @Insert("""
        INSERT INTO users (
            email,
            password_hash,
            email_verified,
            nickname,
            role,
            status
        )
        VALUES (
            #{email},
            #{passwordHash},
            #{emailVerified},
            #{nickname},
            #{role},
            #{status}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Select("""
        SELECT verification_failure_count
        FROM users
        WHERE id = #{userId}
        """)
    int getVerificationFailureCount(
            @Param("userId") Long userId
    );

    @Update("""
        UPDATE users
        SET verification_failure_count =
            verification_failure_count + 1
        WHERE id = #{userId}
          AND verification_failure_count < 5
        """)
    int incrementVerificationFailureCount(
            @Param("userId") Long userId
    );
    @Update("""
    UPDATE users
    SET verification_failure_count = 0,
        verification_locked_until = NULL
    WHERE id = #{userId}
      AND verification_locked_until <= #{now}
    """)
int resetExpiredVerificationLock(
        @Param("userId") Long userId,
        @Param("now") LocalDateTime now
);

    @Update("""
        UPDATE users
        SET verification_locked_until = #{lockedUntil}
        WHERE id = #{userId}
        AND verification_failure_count >= 5
        AND verification_locked_until IS NULL
        """)
    int lockVerification(
            @Param("userId") Long userId,
            @Param("lockedUntil") LocalDateTime lockedUntil
    );
}
