"""add attachments table

Revision ID: a7b8c9d0e1f2
Revises: f6a7b8c9d0e1
Create Date: 2026-04-25 00:00:00.000000
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "a7b8c9d0e1f2"
down_revision = "f6a7b8c9d0e1"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "attachments",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "uploader_user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="SET NULL"),
            nullable=True,
        ),
        sa.Column(
            "chat_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("chats.id", ondelete="CASCADE"),
            nullable=True,
        ),
        sa.Column(
            "message_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("messages.id", ondelete="CASCADE"),
            nullable=True,
        ),
        sa.Column("storage_key", sa.Text(), nullable=False),
        sa.Column("original_filename", sa.String(length=500), nullable=False),
        sa.Column("mime_type", sa.String(length=255), nullable=False),
        sa.Column("size_bytes", sa.BigInteger(), nullable=False, server_default="0"),
        sa.Column("file_kind", sa.String(length=20), nullable=False, server_default="file"),
        sa.Column("width", sa.Integer(), nullable=True),
        sa.Column("height", sa.Integer(), nullable=True),
        sa.Column("duration_ms", sa.Integer(), nullable=True),
        sa.Column("thumbnail_key", sa.Text(), nullable=True),
        sa.Column("sha256", sa.String(length=64), nullable=True),
        sa.Column("status", sa.String(length=20), nullable=False, server_default="pending"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
    )
    op.create_index("ix_attachments_uploader_user_id", "attachments", ["uploader_user_id"])
    op.create_index("ix_attachments_chat_id", "attachments", ["chat_id"])
    op.create_index("ix_attachments_message_id", "attachments", ["message_id"])
    op.create_index("ix_attachments_status", "attachments", ["status"])


def downgrade() -> None:
    op.drop_index("ix_attachments_status", table_name="attachments")
    op.drop_index("ix_attachments_message_id", table_name="attachments")
    op.drop_index("ix_attachments_chat_id", table_name="attachments")
    op.drop_index("ix_attachments_uploader_user_id", table_name="attachments")
    op.drop_table("attachments")