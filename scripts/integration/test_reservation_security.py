
from client import CampusTradeClient

from fixtures import (
    connect_database,
    create_test_accounts,
)

from test_reservation import (
    check,
    wait_until,
    query_database,
    get_active_queue,
    get_draws,
    verify_queue,
    cleanup,
)


def ban_user(admin, user_id, expected_status=200):

    return admin.request(
        "POST",
        f"/api/admin/users/{user_id}/ban",
        expected_status=expected_status
    )


def unban_user(admin, user_id):

    return admin.request(
        "POST",
        f"/api/admin/users/{user_id}/unban"
    )


def get_user_status(connection, user_id):

    rows = query_database(
        connection,
        """
        SELECT status
        FROM users
        WHERE id = %s
        """,
        (user_id,)
    )

    return rows[0]["status"]


def get_round(connection, round_id):

    rows = query_database(
        connection,
        """
        SELECT
            id,
            status,
            draw_version,
            close_reason
        FROM reservation_rounds
        WHERE id = %s
        """,
        (round_id,)
    )

    return rows[0]


def get_reservation_record(
    connection,
    reservation_id
):

    rows = query_database(
        connection,
        """
        SELECT
            id,
            buyer_id,
            round_id,
            status,
            cancel_reason,
            queue_position,
            draw_weight
        FROM product_reservations
        WHERE id = %s
        """,
        (reservation_id,)
    )

    return rows[0]


def get_draw_snapshot(connection, round_id):

    draws = get_draws(
        connection,
        round_id
    )

    entries = query_database(
        connection,
        """
        SELECT
            draw_id,
            round_id,
            reservation_id,
            reservation_order,
            draw_weight,
            queue_position
        FROM reservation_draw_entries
        WHERE round_id = %s
        ORDER BY draw_id, queue_position
        """,
        (round_id,)
    )

    return draws, entries


def main():

    connection = connect_database()

    accounts = {}
    clients = {}

    product_id = None
    passed = False

    try:

        # ==========================================
        # Stage 1: Prepare accounts
        # ==========================================

        print("\n=== Stage 1: Test accounts ===")

        accounts = create_test_accounts(
            connection
        )

        for key, account in accounts.items():

            client = CampusTradeClient(
                account.name
            )

            clients[key] = client

            client.login(
                account.email,
                account.password
            )

        seller = clients["seller"]
        admin = clients["admin"]

        buyer_a = clients["buyer_a"]
        buyer_b = clients["buyer_b"]
        buyer_c = clients["buyer_c"]
        buyer_d = clients["buyer_d"]

        # ==========================================
        # Stage 2: Prepare product
        # ==========================================

        print("\n=== Stage 2: Product ===")

        product_id = seller.create_product({
            "title": "IT reservation security product",
            "description": (
                "Integration test for account bans "
                "and stale cancellation requests"
            ),
            "price": 99.00,
            "category": "OTHER",
            "conditionLevel": "GOOD"
        })

        check(
            isinstance(product_id, int)
            and product_id > 0,
            "Test product created"
        )

        seller.publish_product(product_id)

        print("[PASS] Test product published")

        # ==========================================
        # Stage 3: Administrator authorization
        # ==========================================

        print(
            "\n=== Stage 3: Admin authorization ==="
        )

        ban_user(
            buyer_a,
            accounts["buyer_b"].id,
            expected_status=403
        )

        check(
            get_user_status(
                connection,
                accounts["buyer_b"].id
            ) == "ACTIVE",
            "Normal buyer cannot ban another user"
        )

        # ==========================================
        # Stage 4: First reservation round
        # ==========================================

        print("\n=== Stage 4: First round ===")

        a = buyer_a.reserve(product_id)
        b = buyer_b.reserve(product_id)
        c = buyer_c.reserve(product_id)

        round_id = a["roundId"]

        old_a_id = a["reservationId"]

        check(
            b["roundId"] == round_id
            and c["roundId"] == round_id,
            "Three buyers join the same round"
        )

        check(
            a["drawVersion"] == 0
            and b["drawVersion"] == 0
            and c["drawVersion"] == 0,
            "All buyers joined during priority window"
        )

        wait_until(
            lambda: buyer_a.get_reservation(
                product_id
            ),
            lambda result: (
                result["drawVersion"] == 1
            ),
            timeout=25
        )

        print("[PASS] First random draw completed")

        original_queue = verify_queue(
            connection,
            round_id,
            3
        )

        original_history = get_draw_snapshot(
            connection,
            round_id
        )

        check(
            len(original_history[0]) == 1
            and len(original_history[1]) == 3,
            "Initial draw history is complete"
        )

        # ==========================================
        # Stage 5: Ban first candidate
        # ==========================================

        print(
            "\n=== Stage 5: Ban first candidate ==="
        )

        first = original_queue[0]

        ban_user(
            admin,
            first["buyer_id"]
        )

        check(
            get_user_status(
                connection,
                first["buyer_id"]
            ) == "BANNED",
            "First candidate account is banned"
        )

        record = get_reservation_record(
            connection,
            first["id"]
        )

        check(
            record["status"] == "CANCELLED",
            "Banned buyer reservation is cancelled"
        )

        check(
            record["cancel_reason"] == "BUYER_BANNED",
            "Buyer ban cancellation reason persisted"
        )

        check(
            record["queue_position"] is None,
            "Banned buyer no longer has a queue position"
        )

        compacted = verify_queue(
            connection,
            round_id,
            2
        )

        check(
            [
                row["id"]
                for row in compacted
            ] == [
                row["id"]
                for row in original_queue[1:]
            ],
            "Remaining buyers keep relative order"
        )

        check(
            [
                row["draw_weight"]
                for row in compacted
            ] == [
                row["draw_weight"]
                for row in original_queue[1:]
            ],
            "Remaining buyers keep original draw weights"
        )

        check(
            get_round(
                connection,
                round_id
            )["draw_version"] == 1,
            "Buyer ban does not increment draw version"
        )

        check(
            get_draw_snapshot(
                connection,
                round_id
            ) == original_history,
            "Buyer ban preserves complete draw history"
        )

        # ==========================================
        # Stage 6: Ban remaining buyers
        # ==========================================

        print(
            "\n=== Stage 6: Ban remaining buyers ==="
        )

        ban_user(
            admin,
            compacted[0]["buyer_id"]
        )

        last_queue = verify_queue(
            connection,
            round_id,
            1
        )

        check(
            last_queue[0]["id"]
            == compacted[1]["id"],
            "Last remaining buyer advances to position 1"
        )

        ban_user(
            admin,
            last_queue[0]["buyer_id"]
        )

        verify_queue(
            connection,
            round_id,
            0
        )

        closed_round = get_round(
            connection,
            round_id
        )

        check(
            closed_round["status"] == "CANCELLED",
            "Banning last buyer closes round"
        )

        check(
            closed_round["close_reason"]
            == "NO_ACTIVE_BUYERS",
            "Empty round close reason is correct"
        )

        check(
            closed_round["draw_version"] == 1,
            "Closing empty round does not redraw"
        )

        check(
            get_draw_snapshot(
                connection,
                round_id
            ) == original_history,
            "First round history remains unchanged"
        )

        # ==========================================
        # Stage 7: Reopen with new round
        # ==========================================

        print(
            "\n=== Stage 7: Unban and new round ==="
        )

        unban_user(
            admin,
            accounts["buyer_a"].id
        )

        check(
            get_user_status(
                connection,
                accounts["buyer_a"].id
            ) == "ACTIVE",
            "Buyer A account restored"
        )

        old_record = get_reservation_record(
            connection,
            old_a_id
        )

        check(
            old_record["status"] == "CANCELLED",
            "Unban does not restore old reservation"
        )

        # 使用新的独立会话重新登录。
        buyer_a.close()

        buyer_a = CampusTradeClient("Buyer A")
        clients["buyer_a"] = buyer_a

        buyer_a.login(
            accounts["buyer_a"].email,
            accounts["buyer_a"].password
        )

        new_a = buyer_a.reserve(product_id)

        new_round_id = new_a["roundId"]
        new_a_id = new_a["reservationId"]

        check(
            new_round_id != round_id,
            "New reservation creates a different round"
        )

        check(
            new_a_id != old_a_id,
            "New reservation has a different ID"
        )

        check(
            new_a["reservationStatus"] == "ACTIVE",
            "New reservation is active"
        )

        # 等待新轮次完成首次抽签。
        # 此时再发送旧请求，可以稳定检查
        # 队列和历史记录是否被错误修改。
        wait_until(
            lambda: buyer_a.get_reservation(
                product_id
            ),
            lambda result: (
                result["drawVersion"] == 1
            ),
            timeout=25
        )

        # ==========================================
        # Stage 8: Stale cancellation
        # ==========================================

        print(
            "\n=== Stage 8: Stale cancellation ==="
        )

        queue_before = get_active_queue(
            connection,
            new_round_id
        )

        history_before = get_draw_snapshot(
            connection,
            new_round_id
        )

        buyer_a.cancel(
            product_id,
            old_a_id,
            expected_status=409
        )

        print(
            "[PASS] Old reservation cancellation "
            "rejected with HTTP 409"
        )

        current = buyer_a.get_reservation(
            product_id
        )

        check(
            current["roundId"] == new_round_id,
            "Current reservation round remains unchanged"
        )

        check(
            current["reservationId"] == new_a_id,
            "Current reservation ID remains unchanged"
        )

        check(
            current["reservationStatus"] == "ACTIVE",
            "Stale cancellation cannot cancel new reservation"
        )

        check(
            get_active_queue(
                connection,
                new_round_id
            ) == queue_before,
            "Stale cancellation does not modify queue"
        )

        check(
            get_draw_snapshot(
                connection,
                new_round_id
            ) == history_before,
            "Stale cancellation does not modify draw history"
        )

        # ==========================================
        # Stage 9: Prepare seller ban
        # ==========================================

        print(
            "\n=== Stage 9: Prepare seller ban ==="
        )

        d = buyer_d.reserve(product_id)

        check(
            d["roundId"] == new_round_id,
            "Buyer D joins the second round"
        )

        check(
            d["drawVersion"] == 2,
            "Buyer D triggers a second draw"
        )

        seller_ban_queue = verify_queue(
            connection,
            new_round_id,
            2
        )

        seller_ban_history = get_draw_snapshot(
            connection,
            new_round_id
        )

        # ==========================================
        # Stage 10: Ban seller
        # ==========================================

        print("\n=== Stage 10: Ban seller ===")

        ban_user(
            admin,
            accounts["seller"].id
        )

        check(
            get_user_status(
                connection,
                accounts["seller"].id
            ) == "BANNED",
            "Seller account is banned"
        )

        product_rows = query_database(
            connection,
            """
            SELECT status
            FROM products
            WHERE id = %s
            """,
            (product_id,)
        )

        check(
            product_rows[0]["status"] == "DRAFT",
            "Seller product withdrawn to draft"
        )

        final_round = get_round(
            connection,
            new_round_id
        )

        check(
            final_round["status"] == "CANCELLED",
            "Seller ban closes reservation round"
        )

        check(
            final_round["close_reason"] == "SELLER_BANNED",
            "Seller ban round reason is correct"
        )

        for reservation in seller_ban_queue:

            record = get_reservation_record(
                connection,
                reservation["id"]
            )

            check(
                record["status"] == "CANCELLED",
                "Seller ban cancels reservation "
                f"{record['id']}"
            )

            check(
                record["cancel_reason"] == "SELLER_BANNED",
                "Reservation cancellation reason is correct"
            )

            check(
                record["queue_position"] is None,
                "Cancelled reservation has no queue position"
            )

        verify_queue(
            connection,
            new_round_id,
            0
        )

        check(
            final_round["draw_version"] == 2,
            "Seller ban preserves draw version"
        )

        check(
            get_draw_snapshot(
                connection,
                new_round_id
            ) == seller_ban_history,
            "Seller ban preserves complete draw history"
        )

        check(
            get_draw_snapshot(
                connection,
                round_id
            ) == original_history,
            "Previous round history remains unchanged"
        )

        # ==========================================
        # Stage 11: Unban seller
        # ==========================================

        print("\n=== Stage 11: Unban seller ===")

        unban_user(
            admin,
            accounts["seller"].id
        )

        check(
            get_user_status(
                connection,
                accounts["seller"].id
            ) == "ACTIVE",
            "Seller account restored"
        )

        product_rows = query_database(
            connection,
            """
            SELECT status
            FROM products
            WHERE id = %s
            """,
            (product_id,)
        )

        check(
            product_rows[0]["status"] == "DRAFT",
            "Unban does not republish seller product"
        )

        check(
            get_round(
                connection,
                new_round_id
            )["status"] == "CANCELLED",
            "Unban does not reopen cancelled round"
        )

        print("\n" + "=" * 50)
        print("RESERVATION SECURITY TEST PASSED")
        print("=" * 50)

        passed = True

    finally:

        for client in clients.values():

            try:
                client.logout()
            except Exception:
                pass
            finally:
                client.close()

        try:

            if passed:
                cleanup(
                    connection,
                    accounts,
                    product_id
                )

            else:
                connection.rollback()

                print(
                    "[INFO] Failed test data retained "
                    "for debugging"
                )

                print(
                    "[INFO] Product ID:",
                    product_id
                )

                if "seller" in accounts:
                    print(
                        "[INFO] Seller ID:",
                        accounts["seller"].id
                    )

        finally:
            connection.close()


if __name__ == "__main__":
    main()
