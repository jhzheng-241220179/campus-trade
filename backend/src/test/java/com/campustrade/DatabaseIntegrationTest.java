
package com.campustrade;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseIntegrationTest {
    @Autowired
    private com.campustrade.service.VerificationCodeService verificationCodeService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private void assertTestDatabase() {
        String database = jdbcTemplate.queryForObject(
                "SELECT DATABASE()", String.class);
        assertEquals("campustrade_test", database);
    }

    @Test
    void transactionCommit() {
        assertTestDatabase();

        Long id = transactionTemplate.execute(status -> {
            jdbcTemplate.update("""
                INSERT INTO users (nickname, email_verified)
                VALUES ('test_commit', false)
                """);
            return jdbcTemplate.queryForObject(
                    "SELECT LAST_INSERT_ID()", Long.class);
        });

        assertNotNull(id);

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE id = ?",
                    Integer.class, id);

            assertEquals(1, count);
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM users WHERE id = ?", id);
        }
    }

    @Test
    void transactionRollback() {
        assertTestDatabase();

        String nickname = "test_rollback_" +
                java.util.UUID.randomUUID();

        assertThrows(IllegalStateException.class, () ->
                transactionTemplate.execute(status -> {
                    jdbcTemplate.update(
                            "INSERT INTO users (nickname) VALUES (?)",
                            nickname);
                    throw new IllegalStateException(
                            "模拟业务异常");
                })
        );

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE nickname = ?",
                Integer.class, nickname);

        assertEquals(0, count);
    }
@Test
void rowLockPreventsConcurrentUpdate() throws Exception {
    assertTestDatabase();

    String nickname = "lock_test_" + java.util.UUID.randomUUID();

    jdbcTemplate.update(
        "INSERT INTO users (nickname) VALUES (?)",
        nickname
    );

    Long id = jdbcTemplate.queryForObject(
        "SELECT id FROM users WHERE nickname = ?",
        Long.class, nickname
    );

    assertNotNull(id);

    java.util.concurrent.ExecutorService executor =
        java.util.concurrent.Executors.newSingleThreadExecutor();

    java.util.concurrent.CountDownLatch locked =
        new java.util.concurrent.CountDownLatch(1);

    java.util.concurrent.CountDownLatch release =
        new java.util.concurrent.CountDownLatch(1);

    try {
        java.util.concurrent.Future<?> first = executor.submit(() -> {
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE id = ? FOR UPDATE",
                    Long.class, id
                );

                locked.countDown();

                try {
                    if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IllegalStateException("等待释放锁超时");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            });
        });

        assertTrue(locked.await(5, java.util.concurrent.TimeUnit.SECONDS));

        org.springframework.transaction.support.TransactionTemplate second =
            new org.springframework.transaction.support.TransactionTemplate(
                transactionTemplate.getTransactionManager()
            );

        second.setTimeout(1);

        assertThrows(
            org.springframework.dao.DataAccessException.class,
            () -> second.executeWithoutResult(status -> {
                jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE id = ? FOR UPDATE",
                    Long.class, id
                );
            })
        );

        release.countDown();
        first.get(10, java.util.concurrent.TimeUnit.SECONDS);

    } finally {
        release.countDown();
        executor.shutdownNow();
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
    }
}

@Test
void concurrentVerificationSucceedsOnlyOnce() throws Exception {
    assertTestDatabase();

    String email = "it_" + java.util.UUID.randomUUID() + "@example.com";

    org.springframework.jdbc.support.KeyHolder keyHolder =
            new org.springframework.jdbc.support.GeneratedKeyHolder();

    jdbcTemplate.update(connection -> {
        java.sql.PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO users
                    (nickname, email, password_hash, email_verified, status)
                VALUES (?, ?, ?, false, 'PENDING_VERIFICATION')
                """,
                java.sql.Statement.RETURN_GENERATED_KEYS
        );
        ps.setString(1, "concurrent_test");
        ps.setString(2, email);
        ps.setString(3, "test_password_hash");
        return ps;
    }, keyHolder);

    Long userId = java.util.Objects.requireNonNull(
            keyHolder.getKey()).longValue();

    try {
        String code = verificationCodeService.issueCode(userId).code();

        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(2);

        java.util.concurrent.CountDownLatch ready =
                new java.util.concurrent.CountDownLatch(2);

        java.util.concurrent.CountDownLatch start =
                new java.util.concurrent.CountDownLatch(1);

        try {
            java.util.concurrent.Callable<
                    com.campustrade.service.VerificationCodeService.VerifyResult
                    > task = () -> {
                ready.countDown();
                if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("并发启动超时");
                }
                return verificationCodeService.verifyCode(userId, code);
            };

            var first = executor.submit(task);
            var second = executor.submit(task);

            assertTrue(ready.await(
                    5, java.util.concurrent.TimeUnit.SECONDS));

            start.countDown();

            var r1 = first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            var r2 = second.get(10, java.util.concurrent.TimeUnit.SECONDS);

            long successCount = java.util.stream.Stream.of(r1, r2)
                    .filter(r -> r ==
                            com.campustrade.service.VerificationCodeService
                                    .VerifyResult.SUCCESS)
                    .count();

            assertEquals(1, successCount);

            assertTrue(
                    r1 == com.campustrade.service.VerificationCodeService
                            .VerifyResult.USER_NOT_PENDING
                    || r2 == com.campustrade.service.VerificationCodeService
                            .VerifyResult.USER_NOT_PENDING
            );

            Boolean verified = jdbcTemplate.queryForObject(
                    "SELECT email_verified FROM users WHERE id = ?",
                    Boolean.class, userId);

            assertEquals(Boolean.TRUE, verified);

            Integer consumed = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM email_verification_codes
                    WHERE user_id = ? AND used_at IS NOT NULL
                    """,
                    Integer.class, userId);

            assertEquals(1, consumed);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }

    } finally {
        jdbcTemplate.update(
                "DELETE FROM email_verification_codes WHERE user_id = ?",
                userId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }
}
@Test
void wrongCodeShouldPersistAccountFailureCount() {
    assertTestDatabase();

    String email = "it_" + java.util.UUID.randomUUID() + "@example.com";

    org.springframework.jdbc.support.KeyHolder keyHolder =
            new org.springframework.jdbc.support.GeneratedKeyHolder();

    jdbcTemplate.update(connection -> {
        var ps = connection.prepareStatement("""
            INSERT INTO users
                (nickname, email, password_hash, email_verified, status)
            VALUES (?, ?, ?, false, 'PENDING_VERIFICATION')
            """, java.sql.Statement.RETURN_GENERATED_KEYS);

        ps.setString(1, "failure_test");
        ps.setString(2, email);
        ps.setString(3, "test_hash");
        return ps;
    }, keyHolder);

    Long userId = java.util.Objects.requireNonNull(
            keyHolder.getKey()).longValue();

    try {
        String code = verificationCodeService.issueCode(userId).code();

        String wrongCode = code.equals("000000")
                ? "999999" : "000000";

        for (int i = 1; i <= 5; i++) {
            var result = verificationCodeService.verifyCode(
                    userId, wrongCode);

            assertEquals(
                    com.campustrade.service.VerificationCodeService
                            .VerifyResult.WRONG_CODE,
                    result
            );

            Integer count = jdbcTemplate.queryForObject(
                    """
                    SELECT verification_failure_count
                    FROM users
                    WHERE id = ?
                    """,
                    Integer.class, userId);

            assertEquals(i, count);
        }

        var result = verificationCodeService.verifyCode(userId, code);

        assertEquals(
                com.campustrade.service.VerificationCodeService
                        .VerifyResult.TOO_MANY_ATTEMPTS,
                result
        );

        assertThrows(IllegalStateException.class,
                () -> verificationCodeService.issueCode(userId));

    } finally {
        jdbcTemplate.update(
                "DELETE FROM email_verification_codes WHERE user_id = ?",
                userId);
        jdbcTemplate.update(
                "DELETE FROM users WHERE id = ?",
                userId);
    }
}

@Test
void resendShouldRespectCooldownAndPreserveFailures() {
    assertTestDatabase();

    String email = "it_" + java.util.UUID.randomUUID() + "@example.com";

    org.springframework.jdbc.support.KeyHolder keyHolder =
            new org.springframework.jdbc.support.GeneratedKeyHolder();

    jdbcTemplate.update(connection -> {
        var ps = connection.prepareStatement("""
            INSERT INTO users
                (nickname, email, password_hash, email_verified, status)
            VALUES (?, ?, ?, false, 'PENDING_VERIFICATION')
            """, java.sql.Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, "resend_test");
        ps.setString(2, email);
        ps.setString(3, "test_hash");
        return ps;
    }, keyHolder);

    Long userId = java.util.Objects.requireNonNull(
            keyHolder.getKey()).longValue();

    try {
        String firstCode = verificationCodeService.issueCode(userId).code();
        String wrongCode = firstCode.equals("000000") ? "999999" : "000000";

        assertEquals(
                com.campustrade.service.VerificationCodeService.VerifyResult.WRONG_CODE,
                verificationCodeService.verifyCode(userId, wrongCode)
        );

        assertThrows(IllegalStateException.class,
                () -> verificationCodeService.issueCode(userId));

        jdbcTemplate.update("""
            UPDATE email_verification_codes
            SET created_at = DATE_SUB(NOW(), INTERVAL 70 SECOND)
            WHERE user_id = ?
            """, userId);

        verificationCodeService.issueCode(userId);

        Integer failures = jdbcTemplate.queryForObject(
                "SELECT verification_failure_count FROM users WHERE id = ?",
                Integer.class, userId);

        assertEquals(1, failures);

        Integer issued = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification_codes WHERE user_id = ?",
                Integer.class, userId);

        assertEquals(2, issued);

    } finally {
        jdbcTemplate.update(
                "DELETE FROM email_verification_codes WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }
}

@Test
void hourlyIssueLimitShouldRejectSixthCode() {
    assertTestDatabase();

    String email = "it_" + java.util.UUID.randomUUID() + "@example.com";

    org.springframework.jdbc.support.KeyHolder keyHolder =
            new org.springframework.jdbc.support.GeneratedKeyHolder();

    jdbcTemplate.update(connection -> {
        var ps = connection.prepareStatement("""
            INSERT INTO users
                (nickname, email, password_hash, email_verified, status)
            VALUES (?, ?, ?, false, 'PENDING_VERIFICATION')
            """, java.sql.Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, "hourly_limit_test");
        ps.setString(2, email);
        ps.setString(3, "test_hash");
        return ps;
    }, keyHolder);

    Long userId = java.util.Objects.requireNonNull(
            keyHolder.getKey()).longValue();

    try {
        for (int i = 0; i < 5; i++) {
            verificationCodeService.issueCode(userId);

            jdbcTemplate.update("""
                UPDATE email_verification_codes
                SET created_at = DATE_SUB(NOW(), INTERVAL 2 MINUTE)
                WHERE user_id = ?
                  AND used_at IS NULL
                """, userId);
        }

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> verificationCodeService.issueCode(userId)
        );

        assertTrue(exception.getMessage().contains("一小时"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification_codes WHERE user_id = ?",
                Integer.class, userId);

        assertEquals(5, count);

    } finally {
        jdbcTemplate.update(
                "DELETE FROM email_verification_codes WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }
}

}
