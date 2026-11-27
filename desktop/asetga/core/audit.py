"""Audit log: siapa mengubah apa dan kapan. Hanya ditambah, tidak pernah diubah."""

from __future__ import annotations

import json

from . import fmt
from .db import Database, new_id
from .models import AuditAction, AuditEntityType
from .session import Session


class ChangeSet:
    """Bandingkan nilai lama & baru; hanya field yang berubah yang dicatat."""

    def __init__(self):
        self.changes: dict[str, list[str | None]] = {}

    @staticmethod
    def _text(v) -> str | None:
        if v is None:
            return None
        if isinstance(v, bool):
            return "true" if v else "false"  # sama dengan Boolean.toString() di Kotlin
        return str(v)

    def field(self, label: str, old, new) -> "ChangeSet":
        o, n = self._text(old), self._text(new)
        if o != n:
            self.changes[label] = [o, n]
        return self

    def __bool__(self) -> bool:
        return bool(self.changes)


class AuditLog:
    def __init__(self, db: Database, session: Session):
        self.db = db
        self.session = session

    def log(self, entity_type: AuditEntityType, entity_id: str, action: AuditAction, summary: str,
            changes: ChangeSet | dict | None = None) -> None:
        data = changes.changes if isinstance(changes, ChangeSet) else changes
        js = json.dumps(data, ensure_ascii=False, separators=(",", ":")) if data else None
        u = self.session.user
        self.db.exec(
            "INSERT INTO audit_logs (id, timestamp, userId, userName, entityType, entityId, action, summary, changesJson, syncState) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')",
            (new_id(), fmt.now_ms(), u.id if u else None, u.full_name if u else "Sistem",
             entity_type.name, entity_id, action.name, summary, js),
        )

    def log_batch(self, entity_type: AuditEntityType, action: AuditAction, entries: list[tuple[str, str]]) -> None:
        u = self.session.user
        now = fmt.now_ms()
        with self.db._lock:
            self.db.conn.executemany(
                "INSERT INTO audit_logs (id, timestamp, userId, userName, entityType, entityId, action, summary, changesJson, syncState) "
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 'PENDING')",
                [(new_id(), now, u.id if u else None, u.full_name if u else "Sistem", entity_type.name, eid, action.name, s)
                 for eid, s in entries],
            )

    # ---- baca
    def search(self, entity_type: AuditEntityType | None = None, query: str = "", limit: int = 500, offset: int = 0):
        q = query.strip().lower()
        return self.db.q(
            "SELECT * FROM audit_logs WHERE (? IS NULL OR entityType = ?) "
            "AND (? = '' OR instr(lower(summary), ?) > 0 OR instr(lower(userName), ?) > 0) "
            "ORDER BY timestamp DESC LIMIT ? OFFSET ?",
            (entity_type.name if entity_type else None, entity_type.name if entity_type else None, q, q, q, limit, offset),
        )

    def for_entity(self, entity_type: AuditEntityType, entity_id: str):
        return self.db.q(
            "SELECT * FROM audit_logs WHERE entityType = ? AND entityId = ? ORDER BY timestamp DESC",
            (entity_type.name, entity_id),
        )


def changes_text(changes_json: str | None) -> str:
    """{"Nama":["A","B"]} → "Nama: A → B" (satu baris per field)."""
    if not changes_json:
        return ""
    try:
        data = json.loads(changes_json)
    except ValueError:
        return ""
    return "\n".join(f"{k}: {v[0] if v[0] is not None else '-'} → {v[1] if v[1] is not None else '-'}" for k, v in data.items())
