from __future__ import annotations

import sqlite3
from typing import Any

TASK_COLUMNS = "id,title,description,status,owner,priority,created_at"


class TaskRepository:
    def __init__(self, connection: sqlite3.Connection) -> None:
        self._connection = connection

    def find_by_owner(self, owner: str) -> list[sqlite3.Row]:
        return self._connection.execute(
            f"SELECT {TASK_COLUMNS} FROM tasks WHERE owner = ? ORDER BY id",
            (owner,),
        ).fetchall()

    def insert(self, fields: dict[str, Any]) -> sqlite3.Row:
        cursor = self._connection.execute(
            "INSERT INTO tasks(title,description,status,owner,priority,created_at) VALUES (?,?,?,?,?,datetime('now'))",
            (fields["title"], fields["description"], fields["status"], fields["owner"], fields["priority"]),
        )
        self._connection.commit()
        return self._connection.execute(
            f"SELECT {TASK_COLUMNS} FROM tasks WHERE id = ?",
            (cursor.lastrowid,),
        ).fetchone()

    def status_and_priority_by_owner(self, owner: str) -> list[sqlite3.Row]:
        return self._connection.execute(
            "SELECT status,priority FROM tasks WHERE owner = ?",
            (owner,),
        ).fetchall()
