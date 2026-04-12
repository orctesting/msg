"""align_otp_sessions_with_plan

Revision ID: 1459136763dc
Revises: 322490a2446b
Create Date: 2026-04-12 18:05:40.922405

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '1459136763dc'
down_revision: Union[str, None] = '322490a2446b'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    with op.batch_alter_table("otp_sessions") as batch_op:
        columns = [c["name"] for c in sa.inspect(op.get_bind()).get_columns("otp_sessions")]

        if "request_id" in columns and "session_id" not in columns:
            batch_op.alter_column("request_id", new_column_name="session_id")

        if "is_verified" in columns and "status" not in columns:
            batch_op.add_column(sa.Column("status", sa.String(length=20), nullable=True))

    conn = op.get_bind()
    inspector = sa.inspect(conn)
    columns = [c["name"] for c in inspector.get_columns("otp_sessions")]

    if "is_verified" in columns and "status" in columns:
        conn.execute(sa.text("""
            UPDATE otp_sessions
            SET status = CASE
                WHEN is_verified = true THEN 'verified'
                ELSE 'pending'
            END
            WHERE status IS NULL
        """))

    columns = [c["name"] for c in sa.inspect(conn).get_columns("otp_sessions")]
    with op.batch_alter_table("otp_sessions") as batch_op:
        if "max_attempts" not in columns:
            batch_op.add_column(sa.Column("max_attempts", sa.Integer(), nullable=False, server_default="5"))

        if "status" in columns:
            batch_op.alter_column("status", existing_type=sa.String(length=20), nullable=False, server_default="pending")

        if "is_verified" in columns:
            batch_op.drop_column("is_verified")

    op.create_index("idx_otp_sessions_phone", "otp_sessions", ["phone", "created_at"], unique=False)


def downgrade() -> None:
    with op.batch_alter_table("otp_sessions") as batch_op:
        columns = [c["name"] for c in sa.inspect(op.get_bind()).get_columns("otp_sessions")]

        if "is_verified" not in columns:
            batch_op.add_column(sa.Column("is_verified", sa.Boolean(), nullable=False, server_default=sa.text("false")))

    conn = op.get_bind()
    columns = [c["name"] for c in sa.inspect(conn).get_columns("otp_sessions")]
    if "status" in columns and "is_verified" in columns:
        conn.execute(sa.text("""
            UPDATE otp_sessions
            SET is_verified = CASE
                WHEN status = 'verified' THEN true
                ELSE false
            END
        """))

    with op.batch_alter_table("otp_sessions") as batch_op:
        columns = [c["name"] for c in sa.inspect(op.get_bind()).get_columns("otp_sessions")]

        if "max_attempts" in columns:
            batch_op.drop_column("max_attempts")

        if "status" in columns:
            batch_op.drop_column("status")

        if "session_id" in columns and "request_id" not in columns:
            batch_op.alter_column("session_id", new_column_name="request_id")

    op.drop_index("idx_otp_sessions_phone", table_name="otp_sessions")