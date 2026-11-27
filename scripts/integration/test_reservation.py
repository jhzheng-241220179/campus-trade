
import time
from datetime import datetime
from decimal import Decimal

from client import CampusTradeClient

from fixtures import (
    connect_database,
    create_test_accounts,
)


def check(condition, message):
    if not condition:
        raise AssertionError(message)

    print(f"[PASS] {message}")


def wait_until(query, condition, timeout=30):

    deadline = time.monotonic() + timeout

    while time.monotonic() < deadline:

        result = query()

        if condition(result):
            return result

        time.sleep(0.5)

    raise TimeoutError(
        "Expected reservation state was not reached"
    )


def query_database(connection, sql, params=()):

    # 结束上一次只读事务，避免读取旧快照。
    connection.rollback()

    with connection.cursor() as cursor:
        cursor.execute(sql, params)

        result = cursor.fetchall()

    connection.rollback()

    return result


def get_active_queue(connection, round_id):

    return query_database(
        connection,
        """
        SELECT
            id,
            buyer_id,
            reservation_order,
            draw_weight,
            queue_position,
            status
        FROM product_reservations
        WHERE round_id = %s
          AND status = 'ACTIVE'
        ORDER BY queue_position
        """,
        (round_id,)
    )


def get_draws(connection, round_id):

    return query_database(
        connection,
        """
        SELECT
            id,
            draw_version,
            trigger_type,
            trigger_reservation_id,
            participant_count,
            total_weight
        FROM reservation_draws
        WHERE round_id = %s
        ORDER BY draw_version
        """,
        (round_id,)
    )


def verify_queue(connection, round_id, expected_count):

    queue = get_active_queue(
        connection,
        round_id
    )

    check(
        len(queue) == expected_count,
        f"Active reservation count = {expected_count}"
    )

    positions = [
        row["queue_position"]
        for row in queue
    ]

    check(
        positions == list(
            range(1, expected_count + 1)
        ),
        "Queue positions are unique and continuous"
    )

    return queue


def verify_draw_history(connection, round_id):

    draws = get_draws(
        connection,
        round_id
    )

    check(
        len(draws) == 2,
        "Exactly two random draws recorded"
    )

    check(
        [row["draw_version"] for row in draws]
        == [1, 2],
        "Draw versions are continuous"
    )

    check(
        draws[0]["trigger_type"] == "PRIORITY_ENDED",
        "First draw triggered by priority deadline"
    )

    check(
        draws[1]["trigger_type"] == "NEW_RESERVATION",
        "Second draw triggered by new reservation"
    )

    check(
        draws[0]["participant_count"] == 2,
        "First draw contains two participants"
    )

    check(
        draws[1]["participant_count"] == 3,
        "Second draw contains three participants"
    )

    for draw in draws:

        entries = query_database(
            connection,
            """
            SELECT
                reservation_id,
                reservation_order,
                draw_weight,
                queue_position
            FROM reservation_draw_entries
            WHERE draw_id = %s
            ORDER BY queue_position
            """,
            (draw["id"],)
        )

        version = draw["draw_version"]

        check(
            len(entries)
            == draw["participant_count"],
            f"Draw {version}: entry count correct"
        )

        check(
            sum(
                row["draw_weight"]
                for row in entries
            ) == draw["total_weight"],
            f"Draw {version}: total weight correct"
        )

        check(
            [
                row["queue_position"]
                for row in entries
            ] == list(
                range(1, len(entries) + 1)
            ),
            f"Draw {version}: positions correct"
        )

        check(
            len({
                row["reservation_id"]
                for row in entries
            }) == len(entries),
            f"Draw {version}: no duplicate participants"
        )

        check(
            all(
                row["draw_weight"] > 0
                for row in entries
            ),
            f"Draw {version}: weights are positive"
        )

        ordered = sorted(
            entries,
            key=lambda row: row["reservation_order"]
        )

        weights = [
            row["draw_weight"]
            for row in ordered
        ]

        check(
            weights == sorted(
                weights,
                reverse=True
            ),
            f"Draw {version}: earlier buyers have "
            "greater or equal weights"
        )

    return draws


def cleanup(connection, accounts, product_id):

    # 只清理本次测试创建的数据。
    connection.rollback()

    try:
        with connection.cursor() as cursor:

            if product_id is not None:

                seller_id = accounts["seller"].id

                cursor.execute(
                    """
                    SELECT id
                    FROM products
                    WHERE id = %s
                      AND seller_id = %s
                    FOR UPDATE
                    """,
                    (product_id, seller_id)
                )

                if cursor.fetchone() is None:
                    raise RuntimeError(
                        "Test product ownership check failed"
                    )

                cursor.execute(
                    """
                    SELECT id
                    FROM reservation_rounds
                    WHERE product_id = %s
                    ORDER BY id
                    FOR UPDATE
                    """,
                    (product_id,)
                )

                rounds = cursor.fetchall()

                for row in rounds:

                    round_id = row["id"]

                    cursor.execute(
                        """
                        DELETE FROM reservation_draw_entries
                        WHERE round_id = %s
                        """,
                        (round_id,)
                    )

                    cursor.execute(
                        """
                        DELETE FROM reservation_draws
                        WHERE round_id = %s
                        """,
                        (round_id,)
                    )

                    cursor.execute(
                        """
                        DELETE FROM product_reservations
                        WHERE round_id = %s
                        """,
                        (round_id,)
                    )

                    cursor.execute(
                        """
                        DELETE FROM reservation_rounds
                        WHERE id = %s
                        """,
                        (round_id,)
                    )

                cursor.execute(
                    """
                    DELETE FROM product_images
                    WHERE product_id = %s
                    """,
                    (product_id,)
                )

                cursor.execute(
                    """
                    DELETE FROM products
                    WHERE id = %s
                      AND seller_id = %s
                    """,
                    (product_id, seller_id)
                )

            for account in accounts.values():

                cursor.execute(
                    """
                    DELETE FROM users
                    WHERE id = %s
                      AND email = %s
                      AND email LIKE 'ct-it-%%'
                    """,
                    (
                        account.id,
                        account.email
                    )
                )

        connection.commit()

        print("[PASS] Test data cleaned up")

    except Exception:
        connection.rollback()
        raise


def main():

    connection = connect_database()

    accounts = {}
    clients = {}

    product_id = None
    passed = False

    try:

        # ==========================================
        # 1. Prepare accounts and sessions
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

        buyer_a = clients["buyer_a"]
        buyer_b = clients["buyer_b"]
        buyer_c = clients["buyer_c"]
        buyer_d = clients["buyer_d"]

        # ==========================================
        # 2. Create and publish product
        # ==========================================

        print("\n=== Stage 2: Product ===")

        product = seller.create_product({
            "title": "IT reservation test product",
            "description": (
                "Automatically created integration test product"
            ),
            "price": 99.00,
            "category": "OTHER",
            "conditionLevel": "GOOD"
        })

        product_id = product

        check(
            product_id > 0,
            "Test product created"
        )

        seller.publish_product(product_id)

        print("[PASS] Test product published")

        initial = buyer_a.get_reservation(
            product_id
        )

        check(
            initial["roundId"] is None,
            "Product has no existing reservation round"
        )

        # ==========================================
        # 3. Priority window
        # ==========================================

        print("\n=== Stage 3: Priority window ===")

        a = buyer_a.reserve(product_id)

        round_id = a["roundId"]

        a_id = a["reservationId"]

        original_priority = a["priorityEndsAt"]
        original_cooling = a["coolingEndsAt"]
        original_deadline = a["sellerResponseDeadline"]

        priority_seconds = (
            datetime.fromisoformat(
                original_priority
            )
            - datetime.fromisoformat(
                a["firstReservedAt"]
            )
        ).total_seconds()

        cooling_seconds = (
            datetime.fromisoformat(
                original_cooling
            )
            - datetime.fromisoformat(
                a["firstReservedAt"]
            )
        ).total_seconds()

        print(
            "[INFO] Priority window:",
            priority_seconds,
            "seconds"
        )

        print(
            "[INFO] Cooling period:",
            cooling_seconds,
            "seconds"
        )

        check(
            priority_seconds == 10,
            "Integration priority configuration applied"
        )

        check(
            cooling_seconds == 60,
            "Integration cooling configuration applied"
        )

        check(
            a["queuePosition"] == 1,
            "First buyer obtains position 1"
        )

        check(
            a["drawVersion"] == 0,
            "No random draw during priority window"
        )

        b = buyer_b.reserve(product_id)

        b_id = b["reservationId"]

        check(
            b["roundId"] == round_id,
            "Second buyer joins the same round"
        )

        check(
            b["queuePosition"] == 2,
            "Second buyer obtains position 2"
        )

        check(
            b["drawVersion"] == 0,
            "Second buyer does not trigger random draw"
        )

        a = buyer_a.get_reservation(
            product_id
        )

        check(
            a["queuePosition"] == 1,
            "First buyer retains priority position"
        )

        verify_queue(
            connection,
            round_id,
            2
        )

        # ==========================================
        # 4. Automatic first random draw
        # ==========================================

        print("\n=== Stage 4: First automatic draw ===")

        a = wait_until(
            lambda: buyer_a.get_reservation(product_id),
            lambda result: result["drawVersion"] == 1,
            timeout=25
        )

        check(
            a["drawVersion"] == 1,
            "Scheduler completed first random draw"
        )

        verify_queue(
            connection,
            round_id,
            2
        )

        first_draws = get_draws(
            connection,
            round_id
        )

        check(
            len(first_draws) == 1,
            "First draw persisted in database"
        )

        # ==========================================
        # 5. New buyer triggers redraw
        # ==========================================

        print("\n=== Stage 5: Dynamic redraw ===")

        c = buyer_c.reserve(product_id)

        c_id = c["reservationId"]

        check(
            c["drawVersion"] == 2,
            "New buyer triggers second draw"
        )

        check(
            c["priorityEndsAt"] == original_priority,
            "Priority deadline unchanged"
        )

        check(
            c["coolingEndsAt"] == original_cooling,
            "Cooling deadline unchanged"
        )

        check(
            c["sellerResponseDeadline"]
            == original_deadline,
            "Seller response deadline unchanged"
        )

        queue_before = verify_queue(
            connection,
            round_id,
            3
        )

        repeated = buyer_c.reserve(product_id)

        check(
            repeated["reservationId"] == c_id,
            "Repeated reservation is idempotent"
        )

        check(
            repeated["drawVersion"] == 2,
            "Repeated reservation does not redraw"
        )

        queue_after = get_active_queue(
            connection,
            round_id
        )

        check(
            queue_before == queue_after,
            "Repeated reservation preserves queue"
        )

        # ==========================================
        # 6. Reject incorrect reservation IDs
        # ==========================================

        print("\n=== Stage 6: Cancellation security ===")

        buyer_a.cancel(
            product_id,
            b_id,
            expected_status=409
        )

        check(
            get_active_queue(
                connection,
                round_id
            ) == queue_before,
            "Cannot cancel another buyer's reservation"
        )

        buyer_a.cancel(
            product_id,
            0,
            expected_status=400
        )

        check(
            get_active_queue(
                connection,
                round_id
            ) == queue_before,
            "Invalid cancellation does not modify queue"
        )

        # ==========================================
        # 7. Cancel first candidate
        # ==========================================

        print("\n=== Stage 7: Queue compaction ===")

        by_id = {
            accounts["buyer_a"].id: buyer_a,
            accounts["buyer_b"].id: buyer_b,
            accounts["buyer_c"].id: buyer_c
        }

        first = queue_before[0]

        cancelled = by_id[
            first["buyer_id"]
        ].cancel(
            product_id,
            first["id"]
        )

        check(
            cancelled["reservationStatus"]
            == "CANCELLED",
            "First candidate cancelled successfully"
        )

        check(
            cancelled["drawVersion"] == 2,
            "Cancellation does not increment draw version"
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
                for row in queue_before[1:]
            ],
            "Remaining buyers keep relative order"
        )

        check(
            [
                row["draw_weight"]
                for row in compacted
            ] == [
                row["draw_weight"]
                for row in queue_before[1:]
            ],
            "Queue compaction preserves draw weights"
        )

        # ==========================================
        # 8. Automatic queue freeze
        # ==========================================

        print("\n=== Stage 8: Automatic freeze ===")

        remaining_client = by_id[
            compacted[0]["buyer_id"]
        ]

        frozen = wait_until(
            lambda: remaining_client.get_reservation(
                product_id
            ),
            lambda result: result["roundStatus"]
            == "FROZEN",
            timeout=80
        )

        # API may expose a time-based FROZEN status
        # before the scheduler persists the status.
        wait_until(
            lambda: query_database(
                connection,
                """
                SELECT status
                FROM reservation_rounds
                WHERE id = %s
                """,
                (round_id,)
            ),
            lambda rows: (
                len(rows) == 1
                and rows[0]["status"] == "FROZEN"
            ),
            timeout=10
        )

        check(
            frozen["drawVersion"] == 2,
            "Freeze preserves draw version"
        )

        check(
            not frozen["canReserve"],
            "Frozen round does not accept reservations"
        )

        buyer_d.reserve(
            product_id,
            expected_status=409
        )

        print(
            "[PASS] New buyer rejected after freeze"
        )

        check(
            get_active_queue(
                connection,
                round_id
            ) == compacted,
            "Freeze and rejected join preserve queue"
        )

        # ==========================================
        # 9. Cancellation after freeze
        # ==========================================

        print("\n=== Stage 9: Frozen queue cancellation ===")

        first_frozen = compacted[0]

        frozen_cancel = by_id[
            first_frozen["buyer_id"]
        ].cancel(
            product_id,
            first_frozen["id"]
        )

        check(
            frozen_cancel["drawVersion"] == 2,
            "Frozen cancellation does not redraw"
        )

        last_queue = verify_queue(
            connection,
            round_id,
            1
        )

        check(
            last_queue[0]["id"]
            == compacted[1]["id"],
            "Remaining frozen buyer advances to position 1"
        )

        last_buyer = last_queue[0]

        last_result = by_id[
            last_buyer["buyer_id"]
        ].cancel(
            product_id,
            last_buyer["id"]
        )

        check(
            last_result["roundStatus"] == "CANCELLED",
            "Last buyer cancellation closes round"
        )

        verify_queue(
            connection,
            round_id,
            0
        )

        # ==========================================
        # 10. Database consistency
        # ==========================================

        print("\n=== Stage 10: Draw history ===")

        draws = verify_draw_history(
            connection,
            round_id
        )

        check(
            draws[1]["trigger_reservation_id"] == c_id,
            "Second draw links to correct new reservation"
        )

        rows = query_database(
            connection,
            """
            SELECT status, draw_version
            FROM reservation_rounds
            WHERE id = %s
            """,
            (round_id,)
        )

        check(
            rows[0]["status"] == "CANCELLED",
            "Final round status persisted"
        )

        check(
            rows[0]["draw_version"] == 2,
            "Final round version matches draw history"
        )

        print("\n" + "=" * 50)
        print("RESERVATION INTEGRATION TEST PASSED")
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
