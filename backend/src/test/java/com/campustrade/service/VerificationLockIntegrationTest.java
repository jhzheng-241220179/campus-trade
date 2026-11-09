
package com.campustrade.service;

import com.campustrade.exception.VerificationCodeLimitException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class VerificationLockIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Test
    void shouldLockAndRecoverAfterFiveWrongAttempts() {

        Long userId = createTestUser();

        try {
            // 1. 签发验证码
            VerificationCodeService.IssuedCode issued =
                    verificationCodeService.issueCode(userId);

            String correctCode = issued.code();

            String wrongCode = correctCode.equals("000000")
                    ? "999999" : "000000";

            // 2. 连续输错五次
            for (int i = 0; i < 5; i++) {
                var result = verificationCodeService.verifyCode(
                        userId, wrongCode
                );

                assertEquals(
                        VerificationCodeService.VerifyResult.WRONG_CODE,
                        result
                );
            }

            // 3. 检查账户错误次数
            Integer failureCount = jdbcTemplate.queryForObject(
                    """
                    SELECT verification_failure_count
                    FROM users
                    WHERE id = ?
                    """,
                    Integer.class,
                    userId
            );

            assertEquals(5, failureCount);

            // 4. 检查锁定截止时间
            Timestamp lockedUntil = jdbcTemplate.queryForObject(
                    """
                    SELECT verification_locked_until
                    FROM users
                    WHERE id = ?
                    """,
                    Timestamp.class,
                    userId
            );

            assertNotNull(lockedUntil);

            assertTrue(
                    lockedUntil.toLocalDateTime()
                            .isAfter(LocalDateTime.now())
            );

            // 5. 锁定期间不允许签发
            assertThrows(
                    VerificationCodeLimitException.class,
                    () -> verificationCodeService.issueCode(userId)
            );

            // 6. 锁定期间不能验证旧验证码
            assertEquals(
                    VerificationCodeService.VerifyResult.TOO_MANY_ATTEMPTS,
                    verificationCodeService.verifyCode(
                            userId, correctCode
                    )
            );

            // 7. 在测试数据库中模拟锁定到期
            jdbcTemplate.update(
                    """
                    UPDATE users
                    SET verification_locked_until =
                        DATE_SUB(NOW(), INTERVAL 1 MINUTE)
                    WHERE id = ?
                    """,
                    userId
            );

            // 同时模拟上次签发已超过冷却时间
            jdbcTemplate.update(
                    """
                    UPDATE email_verification_codes
                    SET created_at =
                        DATE_SUB(NOW(), INTERVAL 16 MINUTE)
                    WHERE user_id = ?
                    """,
                    userId
            );

            // 8. 到期后仍不能直接验证旧验证码
            assertEquals(
                    VerificationCodeService.VerifyResult.TOO_MANY_ATTEMPTS,
                    verificationCodeService.verifyCode(
                            userId, correctCode
                    )
            );

            // 9. 重新申请验证码，触发账户恢复
            VerificationCodeService.IssuedCode newCode =
                    verificationCodeService.issueCode(userId);

            assertNotNull(newCode.code());
            assertEquals(6, newCode.code().length());

            // 10. 检查错误次数已清零
            Integer resetCount = jdbcTemplate.queryForObject(
                    """
                    SELECT verification_failure_count
                    FROM users
                    WHERE id = ?
                    """,
                    Integer.class,
                    userId
            );

            assertEquals(0, resetCount);

            // 11. 检查锁定时间已清除
            Timestamp newLockedUntil = jdbcTemplate.queryForObject(
                    """
                    SELECT verification_locked_until
                    FROM users
                    WHERE id = ?
                    """,
                    Timestamp.class,
                    userId
            );

            assertNull(newLockedUntil);

            // 12. 检查旧验证码记录已经作废
            Integer usedCount = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM email_verification_codes
                    WHERE user_id = ?
                      AND used_at IS NOT NULL
                    """,
                    Integer.class,
                    userId
            );

            assertEquals(1, usedCount);

        } finally {

            // 清理测试数据，避免污染后续测试
            jdbcTemplate.update(
                    "DELETE FROM email_verification_codes WHERE user_id = ?",
                    userId
            );

            jdbcTemplate.update(
                    "DELETE FROM users WHERE id = ?",
                    userId
            );
        }
    }

    private Long createTestUser() {

        String email = "lock-test-"
                + UUID.randomUUID()
                + "@example.com";

        GeneratedKeyHolder keyHolder =
                new GeneratedKeyHolder();

        int rows = jdbcTemplate.update(connection -> {

            var statement = connection.prepareStatement(
                    """
                    INSERT INTO users (
                        email,
                        password_hash,
                        nickname,
                        role,
                        status,
                        email_verified
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );

            statement.setString(1, email);
            statement.setString(2, "integration-test-only");
            statement.setString(3, "LockTest");
            statement.setString(4, "USER");
            statement.setString(5, "PENDING_VERIFICATION");
            statement.setBoolean(6, false);

            return statement;

        }, keyHolder);

        assertEquals(1, rows);
        assertNotNull(keyHolder.getKey());

        return keyHolder.getKey().longValue();
    }

@Test
void failedDeliveryShouldInvalidateOnlySpecifiedCode() {

    Long userId = createTestUser();

    try {
        // 签发第一条验证码
        var issued = verificationCodeService.issueCode(userId);

        // 模拟 SMTP 发送失败后的补偿
        verificationCodeService.invalidateFailedDelivery(
                userId,
                issued.id()
        );

        // 确认指定验证码已作废
        Integer invalidated = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM email_verification_codes
                WHERE id = ?
                  AND user_id = ?
                  AND used_at IS NOT NULL
                """,
                Integer.class,
                issued.id(),
                userId
        );

        assertEquals(1, invalidated);

        // 补偿不应清除签发历史
        Integer total = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM email_verification_codes
                WHERE user_id = ?
                """,
                Integer.class,
                userId
        );

        assertEquals(1, total);

        // 重复补偿应保持已作废状态
        verificationCodeService.invalidateFailedDelivery(
                userId,
                issued.id()
        );

        Integer stillInvalidated = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM email_verification_codes
                WHERE id = ?
                  AND used_at IS NOT NULL
                """,
                Integer.class,
                issued.id()
        );

        assertEquals(1, stillInvalidated);

    } finally {
        jdbcTemplate.update(
                "DELETE FROM email_verification_codes WHERE user_id = ?",
                userId
        );

        jdbcTemplate.update(
                "DELETE FROM users WHERE id = ?",
                userId
        );
    }
}

}
