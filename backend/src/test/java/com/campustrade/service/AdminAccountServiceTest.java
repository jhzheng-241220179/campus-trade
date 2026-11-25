
package com.campustrade.service;

import com.campustrade.exception.ProductConflictException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAccountServiceTest {

    private JdbcTemplate jdbcTemplate;
    private ReservationLifecycleService lifecycle;
    private AdminAccountService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        lifecycle = mock(ReservationLifecycleService.class);

        service = new AdminAccountService(
                jdbcTemplate,
                lifecycle
        );
    }

    private void givenUser(
            String role,
            String status,
            boolean verified
    ) {
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers
                        .<RowMapper<Object>>any(),
                eq(5L)
        )).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);

            ResultSet resultSet = mock(ResultSet.class);

            when(resultSet.getLong("id"))
                    .thenReturn(5L);

            when(resultSet.getString("role"))
                    .thenReturn(role);

            when(resultSet.getString("status"))
                    .thenReturn(status);

            when(resultSet.getBoolean("email_verified"))
                    .thenReturn(verified);

            return List.of(mapper.mapRow(resultSet, 0));
        });
    }

    @Test
    void banShouldWithdrawPublishedProducts() {
        givenUser("USER", "ACTIVE", true);

        when(jdbcTemplate.update(
                contains("UPDATE users"),
                eq(5L)
        )).thenReturn(1);

        assertDoesNotThrow(() -> service.ban(5L));

        verify(jdbcTemplate).update(
                contains("UPDATE users"),
                eq(5L)
        );

        verify(jdbcTemplate).update(
                contains("UPDATE products"),
                eq(5L)
        );
    }

    @Test
    void unbanShouldNotRepublishProducts() {
        givenUser("USER", "BANNED", true);

        when(jdbcTemplate.update(
                contains("UPDATE users"),
                eq(5L)
        )).thenReturn(1);

        assertDoesNotThrow(() -> service.unban(5L));

        verify(jdbcTemplate).update(
                contains("UPDATE users"),
                eq(5L)
        );

        verify(jdbcTemplate, never()).update(
                contains("UPDATE products"),
                (Object) any()
        );
    }

    @Test
    void cannotBanAdmin() {
        givenUser("ADMIN", "ACTIVE", true);

        assertThrows(
                ProductConflictException.class,
                () -> service.ban(5L)
        );

        verify(jdbcTemplate, never()).update(
                anyString(),
                (Object) any()
        );

        verifyNoInteractions(lifecycle);
    }

    @Test
    void cannotBanAlreadyBannedUser() {
        givenUser("USER", "BANNED", true);

        assertThrows(
                ProductConflictException.class,
                () -> service.ban(5L)
        );

        verify(jdbcTemplate, never()).update(
                anyString(),
                (Object) any()
        );

        verifyNoInteractions(lifecycle);
    }

    @Test
    void cannotUnbanUnverifiedUser() {
        givenUser("USER", "BANNED", false);

        assertThrows(
                ProductConflictException.class,
                () -> service.unban(5L)
        );

        verify(jdbcTemplate, never()).update(
                anyString(),
                (Object) any()
        );

        verifyNoInteractions(lifecycle);
    }

    @Test
    void cannotManageNonexistentUser() {
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers
                        .<RowMapper<Object>>any(),
                eq(5L)
        )).thenReturn(List.of());

        assertThrows(
                ProductConflictException.class,
                () -> service.ban(5L)
        );

        verify(jdbcTemplate, never()).update(
                anyString(),
                (Object) any()
        );

        verifyNoInteractions(lifecycle);
    }

    @Test
    void banShouldCancelSellerAndBuyerReservations() {
        givenUser("USER", "ACTIVE", true);

        when(jdbcTemplate.update(
                contains("UPDATE users"),
                eq(5L)
        )).thenReturn(1);

        service.ban(5L);

        var order = inOrder(jdbcTemplate, lifecycle);

        order.verify(jdbcTemplate).update(
                contains("UPDATE users"),
                eq(5L)
        );

        order.verify(lifecycle)
                .cancelSellerReservations(5L);

        order.verify(lifecycle)
                .cancelBuyerReservations(5L);

        order.verify(jdbcTemplate).update(
                contains("UPDATE products"),
                eq(5L)
        );
    }

    @Test
    void unbanShouldNotRestoreReservations() {
        givenUser("USER", "BANNED", true);

        when(jdbcTemplate.update(
                contains("UPDATE users"),
                eq(5L)
        )).thenReturn(1);

        service.unban(5L);

        verifyNoInteractions(lifecycle);
    }

    @Test
    void cancellationFailurePreventsProductWithdrawal() {
        givenUser("USER", "ACTIVE", true);

        when(jdbcTemplate.update(
                contains("UPDATE users"),
                eq(5L)
        )).thenReturn(1);

        doThrow(
                new ProductConflictException(
                        "预约取消失败"
                )
        ).when(lifecycle)
                .cancelSellerReservations(5L);

        assertThrows(
                ProductConflictException.class,
                () -> service.ban(5L)
        );

        verify(jdbcTemplate, never()).update(
                contains("UPDATE products"),
                eq(5L)
        );

        verify(lifecycle, never())
                .cancelBuyerReservations(5L);
    }
}
