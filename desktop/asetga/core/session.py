"""Pengguna yang sedang masuk dan pemeriksaan peran (pengaman kedua setelah UI menyembunyikan tombol)."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

from .models import MSG_NO_ACCESS, AppError, Role


@dataclass(frozen=True)
class User:
    id: str
    username: str
    full_name: str
    role: Role
    is_active: bool = True

    @staticmethod
    def from_row(r) -> "User":
        return User(r["id"], r["username"], r["fullName"], Role[r["role"]], bool(r["isActive"]))


class Session:
    def __init__(self):
        self.user: User | None = None
        self.locked = False

    @property
    def role(self) -> Role | None:
        return self.user.role if self.user else None

    def login(self, user: User) -> None:
        self.user = user
        self.locked = False

    def logout(self) -> None:
        self.user = None
        self.locked = False

    def require(self, check: Callable[[Role], bool], message: str = MSG_NO_ACCESS) -> None:
        if self.user is None or not check(self.user.role):
            raise AppError(message)
