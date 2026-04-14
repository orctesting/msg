"""fix push_logs.push_token_id nullable

Revision ID: b2c3d4e5f6a7
Revises: a1b2c3d4e5f6
Create Date: 2026-04-15 02:00:00.000000
"""

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "b2c3d4e5f6a7"
down_revision = "a1b2c3d4e5f6"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.alter_column(
        "push_logs",
        "push_token_id",
        existing_type=postgresql.UUID(as_uuid=True),
        nullable=True,
    )


def downgrade() -> None:
    op.execute(sa.text("""
        UPDATE push_logs SET push_token_id = (
            SELECT pt.id FROM push_tokens pt
            JOIN devices d ON d.id = push_logs.device_id
            WHERE pt.device_id = d.id
            LIMIT 1
        ) WHERE push_token_id IS NULL
    """))
    op.alter_column(
        "push_logs",
        "push_token_id",
        existing_type=postgresql.UUID(as_uuid=True),
        nullable=False,
    )