"""message_reads and push_logs fk cascade on delete

Revision ID: d4e5f6a7b8c9
Revises: c3d4e5f6a7b8
Create Date: 2026-04-18 01:00:00.000000
"""

from alembic import op
from sqlalchemy import text as sa_text

revision = "d4e5f6a7b8c9"
down_revision = "c3d4e5f6a7b8"
branch_labels = None
depends_on = None


def _drop_fks_referencing(conn, table: str, ref_table: str) -> None:
    """Drops all FK constraints on `table` that reference `ref_table`."""
    rows = conn.execute(
        sa_text(
            """
            SELECT conname
            FROM pg_constraint
            WHERE conrelid = :table::regclass
              AND contype = 'f'
              AND pg_get_constraintdef(oid) LIKE :pattern
            """
        ),
        {"table": table, "pattern": f"%REFERENCES {ref_table}%"},
    ).fetchall()
    for (name,) in rows:
        op.drop_constraint(name, table, type_="foreignkey")


def upgrade() -> None:
    conn = op.get_bind()

    # ── message_reads: CASCADE on message_id, user_id ──
    _drop_fks_referencing(conn, "message_reads", "messages")
    op.create_foreign_key(
        "fk_message_reads_message_id",
        "message_reads",
        "messages",
        ["message_id"],
        ["id"],
        ondelete="CASCADE",
    )

    _drop_fks_referencing(conn, "message_reads", "users")
    op.create_foreign_key(
        "fk_message_reads_user_id",
        "message_reads",
        "users",
        ["user_id"],
        ["id"],
        ondelete="CASCADE",
    )

    # ── push_logs: CASCADE on message_id, device_id; SET NULL on push_token_id ──
    _drop_fks_referencing(conn, "push_logs", "messages")
    op.create_foreign_key(
        "fk_push_logs_message_id",
        "push_logs",
        "messages",
        ["message_id"],
        ["id"],
        ondelete="CASCADE",
    )

    _drop_fks_referencing(conn, "push_logs", "devices")
    op.create_foreign_key(
        "fk_push_logs_device_id",
        "push_logs",
        "devices",
        ["device_id"],
        ["id"],
        ondelete="CASCADE",
    )

    _drop_fks_referencing(conn, "push_logs", "push_tokens")
    op.create_foreign_key(
        "fk_push_logs_push_token_id",
        "push_logs",
        "push_tokens",
        ["push_token_id"],
        ["id"],
        ondelete="SET NULL",
    )


def downgrade() -> None:
    op.drop_constraint("fk_push_logs_push_token_id", "push_logs", type_="foreignkey")
    op.drop_constraint("fk_push_logs_device_id", "push_logs", type_="foreignkey")
    op.drop_constraint("fk_push_logs_message_id", "push_logs", type_="foreignkey")

    op.drop_constraint("fk_message_reads_user_id", "message_reads", type_="foreignkey")
    op.drop_constraint("fk_message_reads_message_id", "message_reads", type_="foreignkey")

    op.create_foreign_key(None, "push_logs", "messages", ["message_id"], ["id"])
    op.create_foreign_key(None, "push_logs", "devices", ["device_id"], ["id"])
    op.create_foreign_key(None, "push_logs", "push_tokens", ["push_token_id"], ["id"])

    op.create_foreign_key(None, "message_reads", "messages", ["message_id"], ["id"])
    op.create_foreign_key(None, "message_reads", "users", ["user_id"], ["id"])