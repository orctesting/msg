"""fix message_reads duplicates and add unique constraint

Revision ID: a1b2c3d4e5f6
Revises: 9b1f0f4c1a01
Create Date: 2026-04-15 01:10:00.000000
"""

from alembic import op
import sqlalchemy as sa

revision = "a1b2c3d4e5f6"
down_revision = "9b1f0f4c1a01"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Remove duplicate message_reads, keep the earliest
    op.execute(sa.text("""
        DELETE FROM message_reads
        WHERE id NOT IN (
            SELECT DISTINCT ON (message_id, user_id) id
            FROM message_reads
            ORDER BY message_id, user_id, read_at ASC
        )
    """))

    op.create_unique_constraint(
        "uq_message_reads_message_user",
        "message_reads",
        ["message_id", "user_id"],
    )


def downgrade() -> None:
    op.drop_constraint("uq_message_reads_message_user", "message_reads", type_="unique")