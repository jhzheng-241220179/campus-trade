
import getpass
import secrets
import uuid

from dataclasses import dataclass

import bcrypt
import pymysql


DB_HOST = "localhost"
DB_PORT = 3306
DB_NAME = "campustrade_test"
DB_USER = "campustrade_test_app"


@dataclass(frozen=True)
class TestAccount:
    id: int
    name: str
    email: str
    password: str
    role: str


def connect_database(password=None):

    if password is None:
        password = getpass.getpass(
            "Test database app password: "
        )

    connection = pymysql.connect(
        host=DB_HOST,
        port=DB_PORT,
        user=DB_USER,
        password=password,
        database=DB_NAME,
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=False
    )

    try:
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT DATABASE() AS db_name"
            )

            result = cursor.fetchone()

            if result["db_name"] != DB_NAME:
                raise RuntimeError(
                    "Unsafe database connection"
                )

        return connection

    except Exception:
        connection.close()
        raise


def create_test_accounts(connection):

    # 每次测试拥有独立的运行编号。
    run_id = uuid.uuid4().hex[:12]

    # 随机生成仅用于本次测试的密码。
    password = secrets.token_urlsafe(18)

    # Spring Security 使用 BCryptPasswordEncoder。
    password_hash = bcrypt.hashpw(
        password.encode("utf-8"),
        bcrypt.gensalt(rounds=10, prefix=b"2a")
    ).decode("utf-8")

    definitions = [
        ("seller", "Seller", "USER"),
        ("buyer_a", "Buyer A", "USER"),
        ("buyer_b", "Buyer B", "USER"),
        ("buyer_c", "Buyer C", "USER"),
        ("buyer_d", "Buyer D", "USER"),
        ("admin", "Admin", "ADMIN")
    ]

    accounts = {}

    try:
        with connection.cursor() as cursor:

            for key, name, role in definitions:

                email = (
                    f"ct-it-{run_id}-{key}"
                    "@example.com"
                )

                nickname = f"IT-{name}-{run_id[:6]}"

                cursor.execute(
                    """
                    INSERT INTO users (
                        email,
                        password_hash,
                        email_verified,
                        nickname,
                        role,
                        status
                    )
                    VALUES (
                        %s, %s, TRUE, %s, %s, 'ACTIVE'
                    )
                    """,
                    (
                        email,
                        password_hash,
                        nickname,
                        role
                    )
                )

                accounts[key] = TestAccount(
                    id=cursor.lastrowid,
                    name=name,
                    email=email,
                    password=password,
                    role=role
                )

        connection.commit()

    except Exception:
        connection.rollback()
        raise

    print(f"[INFO] Test run: {run_id}")
    print(f"[PASS] Created {len(accounts)} test accounts")

    return accounts


def cleanup_test_accounts(connection, accounts):

    if not accounts:
        return

    try:
        with connection.cursor() as cursor:

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

    except Exception:
        connection.rollback()
        raise

    print("[PASS] Test accounts cleaned up")
