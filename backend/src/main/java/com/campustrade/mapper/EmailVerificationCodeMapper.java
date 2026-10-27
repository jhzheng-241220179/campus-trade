package com.campustrade.mapper;

import com.campustrade.model.EmailVerificationCode;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface EmailVerificationCodeMapper {

    // 1. 保存新验证码
    @Insert("""
        INSERT INTO email_verification_codes (
            user_id,
            code_hash,
            expires_at,
            attempt_count
        )
        VALUES (
            #{userId},
            #{codeHash},
            #{expiresAt},
            0
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EmailVerificationCode code);

    // 2. 查询用户最新的未使用验证码，并加行锁
    @Select("""
        SELECT *
        FROM email_verification_codes
        WHERE user_id = #{userId}
          AND used_at IS NULL
        ORDER BY created_at DESC, id DESC
        LIMIT 1
        FOR UPDATE
        """)
    EmailVerificationCode findLatestUnusedForUpdate(
            @Param("userId") Long userId
    );

    // 3. 使用户旧的未使用验证码失效
    @Update("""
        UPDATE email_verification_codes
        SET used_at = #{now}
        WHERE user_id = #{userId}
          AND used_at IS NULL
        """)
    int invalidateUnused(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now
    );

    // 4. 记录一次错误尝试
    @Update("""
        UPDATE email_verification_codes
        SET attempt_count = attempt_count + 1
        WHERE id = #{id}
          AND used_at IS NULL
          AND attempt_count < 5
          AND expires_at > #{now}
        """)
    int incrementAttemptCount(
            @Param("id") Long id,
            @Param("now") LocalDateTime now
    );

    // 5. 标记验证码已经成功使用
    @Update("""
        UPDATE email_verification_codes
        SET used_at = #{now}
        WHERE id = #{id}
          AND used_at IS NULL
          AND attempt_count < 5
          AND expires_at > #{now}
        """)
    int markUsed(
            @Param("id") Long id,
            @Param("now") LocalDateTime now
    );
}