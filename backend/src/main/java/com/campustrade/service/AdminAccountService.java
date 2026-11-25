
package com.campustrade.service;

import com.campustrade.exception.ProductConflictException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminAccountService {

    private final JdbcTemplate jdbcTemplate;
    private final ReservationLifecycleService reservationLifecycleService;

    public AdminAccountService(
            JdbcTemplate jdbcTemplate,
            ReservationLifecycleService reservationLifecycleService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.reservationLifecycleService =
                reservationLifecycleService;
    }

    private record TargetUser(
            Long id,
            String role,
            String status,
            boolean emailVerified
    ) {
    }

    private TargetUser lockUser(Long userId) {
        List<TargetUser> users = jdbcTemplate.query(
                """
                SELECT id, role, status, email_verified
                FROM users
                WHERE id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new TargetUser(
                        rs.getLong("id"),
                        rs.getString("role"),
                        rs.getString("status"),
                        rs.getBoolean("email_verified")
                ),
                userId
        );

        if (users.isEmpty()) {
            throw new ProductConflictException(
                    "目标用户不存在"
            );
        }

        return users.get(0);
    }

    private void requireNormalUser(TargetUser user) {
        if (!"USER".equals(user.role())) {
            throw new ProductConflictException(
                    "该接口只能管理普通用户，不能操作管理员账户"
            );
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void ban(Long userId) {
        TargetUser user = lockUser(userId);

        requireNormalUser(user);

        if (!"ACTIVE".equals(user.status())) {
            throw new ProductConflictException(
                    "只有正常状态的普通用户可以封禁"
            );
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE users
                SET status = 'BANNED'
                WHERE id = ?
                  AND role = 'USER'
                  AND status = 'ACTIVE'
                """,
                userId
        );

        if (updated != 1) {
            throw new ProductConflictException(
                    "用户状态已变化，请刷新后重试"
            );
        }

        // 取消该用户作为卖家时的有效预约。
        reservationLifecycleService
                .cancelSellerReservations(userId);

        // 取消该用户作为买家时的有效预约。
        reservationLifecycleService
                .cancelBuyerReservations(userId);

        // 已发布商品撤回为草稿。
        // 原有草稿、已下架商品及图片保持不变。
        jdbcTemplate.update(
                """
                UPDATE products
                SET status = 'DRAFT',
                    version = version + 1
                WHERE seller_id = ?
                  AND status = 'PUBLISHED'
                """,
                userId
        );
    }

    @Transactional
    public void unban(Long userId) {
        TargetUser user = lockUser(userId);

        requireNormalUser(user);

        if (!"BANNED".equals(user.status())) {
            throw new ProductConflictException(
                    "只有已封禁用户可以解封"
            );
        }

        if (!user.emailVerified()) {
            throw new ProductConflictException(
                    "邮箱尚未验证，不能直接恢复为正常账户"
            );
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE users
                SET status = 'ACTIVE'
                WHERE id = ?
                  AND role = 'USER'
                  AND status = 'BANNED'
                  AND email_verified = TRUE
                """,
                userId
        );

        if (updated != 1) {
            throw new ProductConflictException(
                    "用户状态已变化，请刷新后重试"
            );
        }

        // 解封后不恢复历史预约。
        // 商品保持草稿，由用户自行决定是否重新发布。
    }
}
