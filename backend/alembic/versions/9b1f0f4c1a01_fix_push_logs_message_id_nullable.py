"""fix push_logs.message_id nullable

Revision ID: 9b1f0f4c1a01
Revises: 6284ec8ee70f
Create Date: 2026-04-12 12:00:00.000000
"""

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision = "9b1f0f4c1a01"
down_revision = "6284ec8ee70f"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.alter_column(
        "push_logs",
        "message_id",
        existing_type=postgresql.UUID(as_uuid=True),
        nullable=False,
    )


def downgrade() -> None:
    op.alter_column(
        "push_logs",
        "message_id",
        existing_type=postgresql.UUID(as_uuid=True),
        nullable=True,
    )