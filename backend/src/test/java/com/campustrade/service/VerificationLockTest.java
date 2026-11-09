
package com.campustrade.service;

import com.campustrade.exception.VerificationCodeLimitException;
import com.campustrade.mapper.EmailVerificationCodeMapper;
import com.campustrade.mapper.UserMapper;
import com.campustrade.model.EmailVerificationCode;
import com.campustrade.model.User;
import com.campustrade.security.VerificationCodeGenerator;
import com.campustrade.security.VerificationCodeHasher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationLockTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private EmailVerificationCodeMapper codeMapper;

    @Mock
    private VerificationCodeGenerator generator;

    @Mock
    private VerificationCodeHasher hasher;

    @InjectMocks
    private VerificationCodeService service;

    private User pendingUser() {
        User user = new User();
        user.setId(1L);
        user.setStatus("PENDING_VERIFICATION");
        user.setEmailVerified(false);
        return user;
    }

    @Test
    void fifthWrongAttemptShouldLockAccount() {

        User user = pendingUser();

        EmailVerificationCode record =
                new EmailVerificationCode();

        record.setId(10L);
        record.setCodeHash("stored-hash");
        record.setAttemptCount(4);
        record.setExpiresAt(
                LocalDateTime.now().plusMinutes(5)
        );

        when(userMapper.findByIdForUpdate(1L))
                .thenReturn(user);

        // 第一次检查时累计错误次数为 4
        // 本次错误更新后变为 5
        when(userMapper.getVerificationFailureCount(1L))
                .thenReturn(4, 5);

        when(codeMapper.findLatestUnusedForUpdate(1L))
                .thenReturn(record);

        when(codeMapper.incrementAttemptCount(
                eq(10L), any(LocalDateTime.class)))
                .thenReturn(1);

        when(userMapper.incrementVerificationFailureCount(1L))
                .thenReturn(1);

        when(userMapper.lockVerification(
                eq(1L), any(LocalDateTime.class)))
                .thenReturn(1);

        LocalDateTime before = LocalDateTime.now();

        VerificationCodeService.VerifyResult result =
                service.verifyCode(1L, "000000");

        LocalDateTime after = LocalDateTime.now();

        assertEquals(
                VerificationCodeService.VerifyResult.WRONG_CODE,
                result
        );

        ArgumentCaptor<LocalDateTime> captor =
                ArgumentCaptor.forClass(LocalDateTime.class);

        verify(userMapper).lockVerification(
                eq(1L), captor.capture()
        );

        LocalDateTime lockedUntil = captor.getValue();

        assertFalse(
                lockedUntil.isBefore(before.plusMinutes(15))
        );

        assertFalse(
                lockedUntil.isAfter(after.plusMinutes(15))
        );
    }

    @Test
    void lockedAccountShouldRejectNewCode() {

        User user = pendingUser();
        user.setVerificationLockedUntil(
                LocalDateTime.now().plusMinutes(10)
        );

        when(userMapper.findByIdForUpdate(1L))
                .thenReturn(user);

        assertThrows(
                VerificationCodeLimitException.class,
                () -> service.issueCode(1L)
        );

        verifyNoInteractions(codeMapper, generator, hasher);
    }

    @Test
    void expiredLockShouldAllowNewCode() {

        User user = pendingUser();
        user.setVerificationLockedUntil(
                LocalDateTime.now().minusMinutes(1)
        );

        when(userMapper.findByIdForUpdate(1L))
                .thenReturn(user);

        when(codeMapper.countIssuedSince(
                eq(1L), any(LocalDateTime.class)))
                .thenReturn(0);

        when(userMapper.resetExpiredVerificationLock(
                eq(1L), any(LocalDateTime.class)))
                .thenReturn(1);

        when(generator.generate())
                .thenReturn("123456");

        when(hasher.hash(1L, "123456"))
                .thenReturn("test-hash");

        when(codeMapper.insert(any(EmailVerificationCode.class)))
                .thenAnswer(invocation -> {
                    EmailVerificationCode record =
                            invocation.getArgument(0);

                    record.setId(20L);
                    return 1;
                });

        VerificationCodeService.IssuedCode issued =
                service.issueCode(1L);

        assertEquals("123456", issued.code());

        verify(userMapper).resetExpiredVerificationLock(
                eq(1L), any(LocalDateTime.class)
        );

        verify(codeMapper).invalidateUnused(
                eq(1L), any(LocalDateTime.class)
        );

        verify(codeMapper).insert(
                any(EmailVerificationCode.class)
        );
    }

    @Test
    void expiredLockShouldNotAllowOldCodeVerification() {

        User user = pendingUser();
        user.setVerificationLockedUntil(
                LocalDateTime.now().minusMinutes(1)
        );

        when(userMapper.findByIdForUpdate(1L))
                .thenReturn(user);

        VerificationCodeService.VerifyResult result =
                service.verifyCode(1L, "123456");

        assertEquals(
                VerificationCodeService.VerifyResult.TOO_MANY_ATTEMPTS,
                result
        );

        verifyNoInteractions(codeMapper, generator, hasher);
    }
}
