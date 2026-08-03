#!/usr/bin/env python3
import json
import sys
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE_URL = "http://127.0.0.1:8000/api/v1"
USERNAME = "android-e2e-teacher"
PASSWORD = "AndroidE2E-password-2026"


def request(path, *, payload=None, access_token=None, expected=200):
    body = None if payload is None else json.dumps(payload).encode()
    headers = {"Content-Type": "application/json"}
    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"
    try:
        with urlopen(Request(f"{BASE_URL}{path}", body, headers), timeout=10) as response:
            status = response.status
            data = json.loads(response.read() or b"{}")
    except HTTPError as error:
        status = error.code
        data = json.loads(error.read() or b"{}")
    if status != expected:
        raise AssertionError(f"{path}: expected HTTP {expected}, received {status}: {data}")
    return data


def main():
    request(
        "/auth/token/",
        payload={"username": USERNAME, "password": "invalid-password"},
        expected=401,
    )
    tokens = request(
        "/auth/token/",
        payload={"username": USERNAME, "password": PASSWORD},
    )
    assert tokens["access"] and tokens["refresh"]

    user = request("/auth/me/", access_token=tokens["access"])
    assert user["username"] == USERNAME
    assert user["memberships"][0]["role"] == "teacher"

    schools = request("/schools/", access_token=tokens["access"])
    assert [school["slug"] for school in schools] == ["android-e2e-school"]

    rotated = request(
        "/auth/token/refresh/",
        payload={"refresh": tokens["refresh"]},
    )
    assert rotated["access"] and rotated["refresh"]
    request("/auth/me/", access_token=rotated["access"])
    request("/auth/token/logout/", payload={"refresh": rotated["refresh"]})
    request(
        "/auth/token/refresh/",
        payload={"refresh": rotated["refresh"]},
        expected=401,
    )
    print("Android E2E API contract verified", file=sys.stdout)


if __name__ == "__main__":
    main()
