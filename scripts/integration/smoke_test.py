
from client import CampusTradeClient

from fixtures import (
    connect_database,
    create_test_accounts,
    cleanup_test_accounts
)


def main():

    connection = connect_database()

    accounts = {}
    clients = {}

    try:
        accounts = create_test_accounts(connection)

        for key, account in accounts.items():

            client = CampusTradeClient(account.name)

            clients[key] = client

            client.login(
                account.email,
                account.password
            )

        session_ids = []

        for client in clients.values():

            session_id = client.session.cookies.get(
                "JSESSIONID"
            )

            if not session_id:
                raise AssertionError(
                    f"{client.name} has no session"
                )

            session_ids.append(session_id)

        assert len(session_ids) == 6

        assert len(set(session_ids)) == 6, (
            "Different clients share the same session"
        )

        print("[PASS] All six sessions are independent")

        for client in clients.values():
            client.request(
                "GET",
                "/api/products/mine"
            )

        print("[PASS] All authenticated requests succeeded")

        for client in clients.values():
            client.logout()

        print("[PASS] All accounts logged out")

        print("")
        print("=" * 45)
        print("SMOKE TEST PASSED")
        print("=" * 45)

    finally:

        for client in clients.values():
            client.close()

        try:
            cleanup_test_accounts(
                connection,
                accounts
            )
        finally:
            connection.close()


if __name__ == "__main__":
    main()
