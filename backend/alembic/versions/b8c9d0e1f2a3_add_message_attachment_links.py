"""add message_attachment_links table

Revision ID: b8c9d0e1f2a3
Revises: a7b8c9d0e1f2
Create Date: 2026-04-27 00:00:00.000000
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "b8c9d0e1f2a3"
down_revision = "a7b8c9d0e1f2"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "message_attachment_links",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "message_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("messages.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "attachment_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("attachments.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.UniqueConstraint("message_id", "attachment_id", name="uq_message_attachment_link"),
    )
    op.create_index("ix_message_attachment_links_message_id", "message_attachment_links", ["message_id"])
    op.create_index("ix_message_attachment_links_attachment_id", "message_attachment_links", ["attachment_id"])

    # Backfill: для каждого attachment с message_id создаём link
    op.execute(sa.text("""
        INSERT INTO message_attachment_links (id, message_id, attachment_id, created_at)
        SELECT gen_random_uuid(), message_id, id, created_at
        FROM attachments
        WHERE message_id IS NOT NULL
        ON CONFLICT DO NOTHING
    """))


def downgrade() -> None:
    op.drop_index("ix_message_attachment_links_attachment_id", table_name="message_attachment_links")
    op.drop_index("ix_message_attachment_links_message_id", table_name="message_attachment_links")
    op.drop_table("message_attachment_links")