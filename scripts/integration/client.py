
import requests


BASE_URL = "http://127.0.0.1:8081"


class ApiError(RuntimeError):

    def __init__(self, status, method, path, message):
        self.status = status
        self.method = method
        self.path = path

        super().__init__(
            f"{method} {path}: HTTP {status}, {message}"
        )


class CampusTradeClient:

    def __init__(self, name):
        self.name = name

        # 每个客户端拥有独立的 Cookie 和 Session。
        self.session = requests.Session()

        # 本地测试不使用系统代理。
        self.session.trust_env = False

        self.csrf_token = None
        self.csrf_header = None

    def refresh_csrf(self):
        data = self.request(
            "GET",
            "/api/auth/csrf"
        )

        self.csrf_token = data["token"]
        self.csrf_header = data["headerName"]

    def request(
        self,
        method,
        path,
        json=None,
        params=None,
        expected_status=200
    ):
        method = method.upper()

        if not path.startswith("/api/"):
            raise ValueError("Only local API paths are allowed")

        headers = {
            "Accept": "application/json"
        }

        if method in ("POST", "PUT", "PATCH", "DELETE"):
            if self.csrf_token is None:
                self.refresh_csrf()

            headers[self.csrf_header] = self.csrf_token

        response = self.session.request(
            method=method,
            url=BASE_URL + path,
            headers=headers,
            json=json,
            params=params,
            timeout=10,
            allow_redirects=False
        )

        if response.status_code == 204:
            payload = None
        else:
            try:
                payload = response.json()
            except ValueError:
                payload = None

        if response.status_code != expected_status:
            message = (
                payload.get("message", response.reason)
                if isinstance(payload, dict)
                else response.reason
            )

            raise ApiError(
                response.status_code,
                method,
                path,
                message
            )

        if expected_status >= 400:
            return payload

        if response.status_code == 204:
            return None

        if not isinstance(payload, dict):
            raise RuntimeError(
                f"Invalid API response: {method} {path}"
            )

        if payload.get("code") != 200:
            raise RuntimeError(
                f"API business error: {method} {path}, "
                f"{payload.get('message')}"
            )

        return payload.get("data")

    def login(self, email, password):
        self.refresh_csrf()

        self.request(
            "POST",
            "/api/auth/login",
            json={
                "email": email,
                "password": password
            }
        )

        # 登录后重新获取 CSRF Token。
        self.refresh_csrf()

        print(f"[PASS] {self.name} login")

    def create_product(self, product):
        return self.request(
            "POST",
            "/api/products",
            json=product
        )

    def publish_product(self, product_id):
        return self.request(
            "POST",
            f"/api/products/{product_id}/publish"
        )

    def get_reservation(self, product_id):
        return self.request(
            "GET",
            f"/api/products/{product_id}/reservations/mine"
        )

    def reserve(self, product_id, expected_status=200):
        return self.request(
            "POST",
            f"/api/products/{product_id}/reservations",
            expected_status=expected_status
        )

    def cancel(
        self,
        product_id,
        reservation_id,
        expected_status=200
    ):
        return self.request(
            "DELETE",
            f"/api/products/{product_id}/reservations/mine",
            params={
                "reservationId": reservation_id
            },
            expected_status=expected_status
        )

    def logout(self):
        self.request(
            "POST",
            "/api/auth/logout",
            expected_status=204
        )

        self.csrf_token = None
        self.csrf_header = None

    def close(self):
        self.session.close()
