
package com.campustrade.service;

import com.campustrade.mapper.EmailVerificationCodeMapper;
import com.campustrade.mapper.UserMapper;
import com.campustrade.model.EmailVerificationCode;
import com.campustrade.model.User;
import com.campustrade.security.VerificationCodeGenerator;
import com.campustrade.security.VerificationCodeHasher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificationCodeServiceTest {

    private UserMapper userMapper;
    private EmailVerificationCodeMapper codeMapper;
    private VerificationCodeGenerator generator;
    private VerificationCodeHasher hasher;
    private VerificationCodeService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        codeMapper = mock(EmailVerificationCodeMapper.class);
        generator = mock(VerificationCodeGenerator.class);

        hasher = new VerificationCodeHasher(
                "TestSecretKeyForCampusTrade1234567890"
        );

        service = new VerificationCodeService(
                userMapper,
                codeMapper,
                generator,
                hasher
        );
    }

    private User pendingUser() {
        User user = new User();
        user.setId(10L);
        user.setEmailVerified(false);
        user.setStatus("PENDING_VERIFICATION");
        return user;
    }

    private EmailVerificationCode validCode() {
        EmailVerificationCode record =
                new EmailVerificationCode();

        record.setId(100L);
        record.setUserId(10L);
        record.setCodeHash(hasher.hash(10L, "123456"));
        record.setAttemptCount(0);
        record.setUsedAt(null);
        record.setExpiresAt(
                LocalDateTime.now().plusMinutes(5)
        );

        return record;
    }

    // 1. 正常签发验证码
    @Test
    void issueCode_shouldInvalidateOldAndInsertNew() {

        when(userMapper.findByIdForUpdate(10L))
                .thenReturn(pendingUser());

        when(generator.generate())
                .thenReturn("123456");

        when(codeMapper.insert(any(EmailVerificationCode.class)))
                .thenAnswer(invocation -> {
                    EmailVerificationCode record =
                            invocation.getArgument(0);
                    record.setId(100L);
                    return 1;
                });

        VerificationCodeService.IssuedCode issued =
                service.issueCode(10L);

        assertEquals("123456", issued.code());
        assertTrue(issued.expiresAt()
                .isAfter(LocalDateTime.now()));

        ArgumentCaptor<EmailVerificationCode> captor =
                ArgumentCaptor.forClass(
                        EmailVerificationCode.class
                );

        verify(codeMapper).insert(captor.capture());

        EmailVerificationCode saved = captor.getValue();

        assertEquals(10L, saved.getUserId());
        assertEquals(0, saved.getAttemptCount());
        assertEquals(
                hasher.hash(10L, "123456"),
                saved.getCodeHash()
        );
        assertNotEquals("123456", saved.getCodeHash());

        verify(codeMapper).invalidateUnused(
                eq(10L),
                any(LocalDateTime.class)
        );

        var order = inOrder(codeMapper);
        order.verify(codeMapper).invalidateUnused(
                eq(10L),
                any(LocalDateTime.class)
        );
        order.verify(codeMapper).insert(
                any(EmailVerificationCode.class)
        );
    }

    // 2. 正确验证码：使用一次并激活账户
    @Test
    void verifyCode_shouldActivateUserWhenCorrect() {

        when(userMapper.findByIdForUpdate(10L))
                .thenReturn(pendingUser());

        when(codeMapper.findLatestUnusedForUpdate(10L))
                .thenReturn(validCode());

        when(codeMapper.markUsed(
                eq(100L), any(LocalDateTime.class)
        )).thenReturn(1);

        when(userMapper.activateEmail(10L))
                .thenReturn(1);

        var result = service.verifyCode(10L, "123456");

        assertEquals(
                VerificationCodeService.VerifyResult.SUCCESS,
                result
        );

        verify(codeMapper).markUsed(
                eq(100L), any(LocalDateTime.class)
        );
        verify(userMapper).activateEmail(10L);
        verify(codeMapper, never())
                .incrementAttemptCount(
                        anyLong(),
                        any(LocalDateTime.class)
                );
    }

    // 3. 错误验证码：记录错误次数
    @Test
    void verifyCode_shouldIncrementAttemptsWhenWrong() {

        when(userMapper.findByIdForUpdate(10L))
                .thenReturn(pendingUser());

        when(codeMapper.findLatestUnusedForUpdate(10L))
                .thenReturn(validCode());

        when(codeMapper.incrementAttemptCount(
                eq(100L), any(LocalDateTime.class)
        )).thenReturn(1);

        var result = service.verifyCode(10L, "654321");

        assertEquals(
                VerificationCodeService.VerifyResult.WRONG_CODE,
                result
        );

        verify(codeMapper).incrementAttemptCount(
                eq(100L), any(LocalDateTime.class)
        );
        verify(codeMapper, never()).markUsed(
                anyLong(), any(LocalDateTime.class)
        );
        verify(userMapper, never()).activateEmail(anyLong());
    }

    // 4. 过期验证码：拒绝验证
    @Test
    void verifyCode_shouldRejectExpiredCode() {

        EmailVerificationCode expired = validCode();
        expired.setExpiresAt(
                LocalDateTime.now().minusMinutes(1)
        );

        when(userMapper.findByIdForUpdate(10L))
                .thenReturn(pendingUser());

        when(codeMapper.findLatestUnusedForUpdate(10L))
                .thenReturn(expired);

        var result = service.verifyCode(10L, "123456");

        assertEquals(
                VerificationCodeService.VerifyResult.CODE_EXPIRED,
                result
        );

        verify(userMapper, never()).activateEmail(anyLong());
        verify(codeMapper, never()).markUsed(
                anyLong(), any(LocalDateTime.class)
        );
    }

    // 5. 已经错误五次：不允许再次尝试
    @Test
    void verifyCode_shouldRejectTooManyAttempts() {

        EmailVerificationCode exhausted = validCode();
        exhausted.setAttemptCount(5);

        when(userMapper.findByIdForUpdate(10L))
                .thenReturn(pendingUser());

        when(codeMapper.findLatestUnusedForUpdate(10L))
                .thenReturn(exhausted);

        var result = service.verifyCode(10L, "123456");

        assertEquals(
                VerificationCodeService.VerifyResult.TOO_MANY_ATTEMPTS,
                result
        );

        verify(codeMapper, never()).markUsed(
                anyLong(), any(LocalDateTime.class)
        );
        verify(userMapper, never()).activateEmail(anyLong());
    }

    // 6. 激活失败：抛出异常
    @Test
    void verifyCode_shouldThrowWhenActivationFails() {

        when(userMapper.findByIdForUpdate(10L))
                .thenReturn(pendingUser());

        when(codeMapper.findLatestUnusedForUpdate(10L))
                .thenReturn(validCode());

        when(codeMapper.markUsed(
                eq(100L), any(LocalDateTime.class)
        )).thenReturn(1);

        when(userMapper.activateEmail(10L))
                .thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.verifyCode(10L, "123456")
        );

        verify(codeMapper).markUsed(
                eq(100L), any(LocalDateTime.class)
        );
        verify(userMapper).activateEmail(10L);
    }
@Test
void verifyCode_shouldRejectReusedCode() {

    when(userMapper.findByIdForUpdate(10L))
            .thenReturn(pendingUser());

    // 模拟验证码已被之前的请求消耗
    when(codeMapper.findLatestUnusedForUpdate(10L))
            .thenReturn(null);

    var result = service.verifyCode(10L, "123456");

    assertEquals(
            VerificationCodeService.VerifyResult.CODE_NOT_FOUND,
            result
    );

    verify(codeMapper, never()).markUsed(
            anyLong(), any(LocalDateTime.class)
    );

    verify(userMapper, never()).activateEmail(anyLong());
}
@Test
void verifyCode_shouldRejectAfterFifthWrongAttempt() {

    EmailVerificationCode record = validCode();
    record.setAttemptCount(4);

    when(userMapper.findByIdForUpdate(10L))
            .thenReturn(pendingUser());

    when(codeMapper.findLatestUnusedForUpdate(10L))
            .thenReturn(record);

    when(codeMapper.incrementAttemptCount(
            eq(100L), any(LocalDateTime.class)
    )).thenReturn(1);

    var result = service.verifyCode(10L, "654321");

    assertEquals(
            VerificationCodeService.VerifyResult.WRONG_CODE,
            result
    );

    verify(codeMapper).incrementAttemptCount(
            eq(100L), any(LocalDateTime.class)
    );

    verify(userMapper, never()).activateEmail(anyLong());
}
}
