
package com.campustrade.service;

import com.campustrade.mapper.EmailVerificationCodeMapper;
import com.campustrade.mapper.UserMapper;
import com.campustrade.model.EmailVerificationCode;
import com.campustrade.model.User;
import com.campustrade.security.VerificationCodeGenerator;
import com.campustrade.security.VerificationCodeHasher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationCodeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CODE_ID = 100L;
    private static final String CODE = "123456";
    private static final String HASH = "test_hash";

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

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);
        user.setStatus("PENDING_VERIFICATION");
        user.setEmailVerified(false);
    }

    private EmailVerificationCode createValidCode() {
        EmailVerificationCode record =
                new EmailVerificationCode();

        record.setId(CODE_ID);
        record.setUserId(USER_ID);
        record.setCodeHash(HASH);
        record.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        record.setAttemptCount(0);

        return record;
    }

    @Test
    void issueCode_shouldCreateCodeSuccessfully() {
        when(userMapper.findByIdForUpdate(USER_ID))
                .thenReturn(user);

        when(userMapper.getVerificationFailureCount(USER_ID))
                .thenReturn(0);

        when(codeMapper.findLastIssuedAt(USER_ID))
                .thenReturn(null);

        when(codeMapper.countIssuedSince(
                eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(0);

        when(generator.generate())
                .thenReturn(CODE);

        when(hasher.hash(USER_ID, CODE))
                .thenReturn(HASH);

        when(codeMapper.insert(any(EmailVerificationCode.class)))
                .thenAnswer(invocation -> {
                    EmailVerificationCode record =
                            invocation.getArgument(0);
                    record.setId(CODE_ID);
                    return 1;
                });

        VerificationCodeService.IssuedCode result =
                service.issueCode(USER_ID);

        assertNotNull(result);
        assertEquals(CODE, result.code());
        assertTrue(result.expiresAt().isAfter(LocalDateTime.now()));

        verify(codeMapper).invalidateUnused(
                eq(USER_ID), any(LocalDateTime.class));

        verify(codeMapper).insert(any(EmailVerificationCode.class));
    }

    @Test
    void issueCode_shouldRejectDuringCooldown() {
        when(userMapper.findByIdForUpdate(USER_ID))
                .thenReturn(user);

        when(userMapper.getVerificationFailureCount(USER_ID))
                .thenReturn(0);

        when(codeMapper.findLastIssuedAt(USER_ID))
                .thenReturn(LocalDateTime.now().minusSeconds(30));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.issueCode(USER_ID)
        );

        assertTrue(exception.getMessage().contains("频繁"));

        verify(codeMapper, never())
                .insert(any(EmailVerificationCode.class));
    }

    @Test
    void issueCode_shouldRejectAfterHourlyLimit() {
        when(userMapper.findByIdForUpdate(USER_ID))
                .thenReturn(user);

        when(userMapper.getVerificationFailureCount(USER_ID))
                .thenReturn(0);

        when(codeMapper.findLastIssuedAt(USER_ID))
                .thenReturn(LocalDateTime.now().minusMinutes(2));

        when(codeMapper.countIssuedSince(
                eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(5);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.issueCode(USER_ID)
        );

        assertTrue(exception.getMessage().contains("一小时"));

        verify(codeMapper, never())
                .insert(any(EmailVerificationCode.class));
    }

    @Test
    void issueCode_shouldRejectWhenAccountLocked() {
        when(userMapper.findByIdForUpdate(USER_ID))
                .thenReturn(user);

        when(userMapper.getVerificationFailureCount(USER_ID))
                .thenReturn(5);

        assertThrows(
                IllegalStateException.class,
                () -> service.issueCode(USER_ID)
        );

        verify(codeMapper, never())
                .insert(any(EmailVerificationCode.class));
    }

    @Test
    void verifyCode_shouldReturnUserNotFound() {
        when(userMapper.findByIdForUpdate(USER_ID))
                .thenReturn(null);

        VerificationCodeService.VerifyResult result =
                service.verifyCode(USER_ID, CODE);

        assertEquals(
                VerificationCodeService.VerifyResult.USER_NOT_FOUND,
                result
        );
    }

    @Test
    void verifyCode_shouldReturnCodeNotFound() {
        when(userMapper.findByIdForUpdate(USER_ID))
                .thenReturn(user);

        when(userMapper.getVerificationFailureCount(USER_ID))
                .thenReturn(0);

        when(codeMapper.findLatestUnusedForUpdate(USER_ID))
                .thenReturn(null);

        VerificationCodeService.VerifyResult result =
                service.verifyCode(USER_ID, CODE);

        assertEquals(
                VerificationCodeService.VerifyResult.CODE_NOT_FOUND,
                result
        );
    }

    @Test
    void verifyCode_shouldRejectExpiredCode() {
        EmailVerificationCode record = createValidCode();
        record.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(userMapper.findByIdForUpdate(USER_ID))
                .thenReturn(user);

        when(userMapper.getVerificationFailureCount(USER_ID))
                .thenReturn(0);

        when(codeMapper.findLatestUnusedForUpdate(USER_ID))
                .thenReturn(record);

        VerificationCodeService.VerifyResult result =
                service.verifyCode(USER_ID, CODE);

        assertEquals(
                VerificationCodeService.VerifyResult.CODE_EXPIRED,
                result
        );

        verify(codeMapper, never())
                .markUsed(anyLong(), any(LocalDateTime.class));
    }

    @Test
    void verifyCode_shouldIncrementAttemptsWhenWrong() {
        EmailVerificationCode record = createValidCode();

        when(userMapper.findByIdForUpdate(USER_ID))
                .thenReturn(user);

        when(userMapper.getVerificationFailureCount(USER_ID))
                .thenReturn(0);

        when(codeMapper.findLatestUnusedForUpdate(USER_ID))
                .thenReturn(record);

        when(hasher.matches(USER_ID, "999999", HASH))
                .thenReturn(false);

        when(codeMapper.incrementAttemptCount(
                eq(CODE_ID), any(LocalDateTime.class)))
                .thenReturn(1);

        when(userMapper.incrementVerificationFailureCount(USER_ID))
                .thenReturn(1);

        VerificationCodeService.VerifyResult result =
                service.verifyCode(USER_ID, "999999");

        assertEquals(
                VerificationCodeService.VerifyResult.WRONG_CODE,
                result
        );

        verify(codeMapper).incrementAttemptCount(
                eq(CODE_ID), any(LocalDateTime.class));

        verify(userMapper).incrementVerificationFailureCount(USER_ID);

        verify(codeMapper, never())
                .markUsed(anyLong(), any(LocalDateTime.class));
    }

    @Test
    void verifyCode_shouldRejectAfterFifthWrongAttempt() {
        EmailVerificationCode record = createValidCode();
        record.setAttemptCount(4);

        when(userMapper.findByIdForUpdate(USER_ID))
                .thenReturn(user);

        when(userMapper.getVerificationFailureCount(USER_ID))
                .thenReturn(4, 5);

        when(codeMapper.findLatestUnusedForUpdate(USER_ID))
                .thenReturn(record);

        when(hasher.matches(USER_ID, "999999", HASH))
                .thenReturn(false);

        when(codeMapper.incrementAttemptCount(
                eq(CODE_ID), any(LocalDateTime.class)))
                .thenReturn(1);

        when(userMapper.incrementVerificationFailureCount(USER_ID))
                .thenReturn(1);

        VerificationCodeService.VerifyResult first =
                service.verifyCode(USER_ID, "999999");

        assertEquals(
                VerificationCodeService.VerifyResult.WRONG_CODE,
                first
        );

        VerificationCodeService.VerifyResult second =
                service.verifyCode(USER_ID, CODE);

        assertEquals(
                VerificationCodeService.VerifyResult.TOO_MANY_ATTEMPTS,
                second
        );

        verify(userMapper, times(1))
                .incrementVerificationFailureCount(USER_ID);

        verify(codeMapper, never())
                .markUsed(anyLong(), any(LocalDateTime.class));
    }

    @Test
    void verifyCode_shouldActivateUserWhenCorrect() {
        EmailVerificationCode record = createValidCode();

        when(userMapper.findByIdForUpdate(USER_ID))
                .thenReturn(user);

        when(userMapper.getVerificationFailureCount(USER_ID))
                .thenReturn(0);

        when(codeMapper.findLatestUnusedForUpdate(USER_ID))
                .thenReturn(record);

        when(hasher.matches(USER_ID, CODE, HASH))
                .thenReturn(true);

        when(codeMapper.markUsed(
                eq(CODE_ID), any(LocalDateTime.class)))
                .thenReturn(1);

        when(userMapper.activateEmail(USER_ID))
                .thenReturn(1);

        VerificationCodeService.VerifyResult result =
                service.verifyCode(USER_ID, CODE);

        assertEquals(
                VerificationCodeService.VerifyResult.SUCCESS,
                result
        );

        verify(codeMapper).markUsed(
                eq(CODE_ID), any(LocalDateTime.class));

        verify(userMapper).activateEmail(USER_ID);

        verify(userMapper, never())
                .incrementVerificationFailureCount(anyLong());
    }
}
