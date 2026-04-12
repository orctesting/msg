"""align_messages_and_push_schema_with_plan

Revision ID: 6284ec8ee70f
Revises: 1459136763dc
Create Date: 2026-04-12 18:05:57.808378

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql


# revision identifiers, used by Alembic.
revision: str = '6284ec8ee70f'
down_revision: Union[str, None] = '1459136763dc'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    with op.batch_alter_table("messages") as batch_op:
        batch_op.alter_column(
            "sender_id",
            existing_type=postgresql.UUID(as_uuid=True),
            nullable=True,
        )

    inspector = sa.inspect(op.get_bind())

    push_token_columns = [c["name"] for c in inspector.get_columns("push_tokens")]
    with op.batch_alter_table("push_tokens") as batch_op:
        if "failure_count" not in push_token_columns:
            batch_op.add_column(sa.Column("failure_count", sa.Integer(), nullable=False, server_default="0"))
        if "last_failure_reason" not in push_token_columns:
            batch_op.add_column(sa.Column("last_failure_reason", sa.String(length=255), nullable=True))
        if "last_used_at" not in push_token_columns:
            batch_op.add_column(sa.Column("last_used_at", sa.DateTime(timezone=True), nullable=True))

    push_log_table = "push_logs" if "push_logs" in inspector.get_table_names() else "push_log"
    push_log_columns = [c["name"] for c in inspector.get_columns(push_log_table)]

    with op.batch_alter_table(push_log_table) as batch_op:
        if "device_id" not in push_log_columns:
            batch_op.add_column(sa.Column("device_id", postgresql.UUID(as_uuid=True), nullable=True))
        if "push_token_id" not in push_log_columns:
            batch_op.add_column(sa.Column("push_token_id", postgresql.UUID(as_uuid=True), nullable=True))
        if "attempt_number" not in push_log_columns:
            batch_op.add_column(sa.Column("attempt_number", sa.Integer(), nullable=False, server_default="1"))
        if "provider_message_id" not in push_log_columns:
            batch_op.add_column(sa.Column("provider_message_id", sa.String(length=255), nullable=True))
        if "error_details" not in push_log_columns:
            batch_op.add_column(sa.Column("error_details", sa.Text(), nullable=True))
        if "idempotency_key" not in push_log_columns:
            batch_op.add_column(sa.Column("idempotency_key", postgresql.UUID(as_uuid=True), nullable=True))

    if push_log_table == "push_logs":
        fk_names = {fk["name"] for fk in inspector.get_foreign_keys(push_log_table)}
        with op.batch_alter_table(push_log_table) as batch_op:
            if "device_id" in [c["name"] for c in inspector.get_columns(push_log_table)] and "fk_push_logs_device_id_devices" not in fk_names:
                batch_op.create_foreign_key(
                    "fk_push_logs_device_id_devices",
                    "devices",
                    ["device_id"],
                    ["id"],
                    ondelete="CASCADE",
                )
            if "push_token_id" in [c["name"] for c in inspector.get_columns(push_log_table)] and "fk_push_logs_push_token_id_push_tokens" not in fk_names:
                batch_op.create_foreign_key(
                    "fk_push_logs_push_token_id_push_tokens",
                    "push_tokens",
                    ["push_token_id"],
                    ["id"],
                    ondelete="SET NULL",
                )

    conn = op.get_bind()
    if "idempotency_key" in [c["name"] for c in sa.inspect(conn).get_columns(push_log_table)]:
        conn.execute(sa.text(f"""
            UPDATE {push_log_table}
            SET idempotency_key = gen_random_uuid()
            WHERE idempotency_key IS NULL
        """))

    with op.batch_alter_table(push_log_table) as batch_op:
        cols = [c["name"] for c in sa.inspect(op.get_bind()).get_columns(push_log_table)]
        if "device_id" in cols:
            batch_op.alter_column("device_id", existing_type=postgresql.UUID(as_uuid=True), nullable=False)
        if "idempotency_key" in cols:
            batch_op.alter_column("idempotency_key", existing_type=postgresql.UUID(as_uuid=True), nullable=False)

    op.create_index("idx_push_tokens_device", "push_tokens", ["device_id"], unique=False)
    op.create_index("idx_push_log_message", push_log_table, ["message_id"], unique=False)
    op.create_index("idx_push_log_status", push_log_table, ["status"], unique=False)
    op.create_unique_constraint(
        f"uq_{push_log_table}_idempotency_attempt",
        push_log_table,
        ["idempotency_key", "attempt_number"],
    )


def downgrade() -> None:
    push_log_table = "push_logs" if "push_logs" in sa.inspect(op.get_bind()).get_table_names() else "push_log"

    op.drop_constraint(f"uq_{push_log_table}_idempotency_attempt", push_log_table, type_="unique")
    op.drop_index("idx_push_log_status", table_name=push_log_table)
    op.drop_index("idx_push_log_message", table_name=push_log_table)
    op.drop_index("idx_push_tokens_device", table_name="push_tokens")

    with op.batch_alter_table(push_log_table) as batch_op:
        for col in ["idempotency_key", "error_details", "provider_message_id", "attempt_number", "push_token_id", "device_id"]:
            cols = [c["name"] for c in sa.inspect(op.get_bind()).get_columns(push_log_table)]
            if col in cols:
                batch_op.drop_column(col)

    with op.batch_alter_table("push_tokens") as batch_op:
        for col in ["last_used_at", "last_failure_reason", "failure_count"]:
            cols = [c["name"] for c in sa.inspect(op.get_bind()).get_columns("push_tokens")]
            if col in cols:
                batch_op.drop_column(col)

    with op.batch_alter_table("messages") as batch_op:
        batch_op.alter_column(
            "sender_id",
            existing_type=postgresql.UUID(as_uuid=True),
            nullable=False,
        )