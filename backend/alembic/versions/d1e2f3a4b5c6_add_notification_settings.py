"""add notification_settings + notification_chat_whitelist

Revision ID: d1e2f3a4b5c6
Revises: c9d0e1f2a3b4
Create Date: 2026-05-02 00:00:00.000000
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "d1e2f3a4b5c6"
down_revision = "c9d0e1f2a3b4"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "notification_settings",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("platform", sa.String(length=20), nullable=False),
        sa.Column("mode", sa.String(length=20), nullable=False, server_default="all"),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.UniqueConstraint("user_id", "platform", name="uq_notification_settings_user_platform"),
    )
    op.create_index(
        "ix_notification_settings_user_id",
        "notification_settings",
        ["user_id"],
    )

    op.create_table(
        "notification_chat_whitelist",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("platform", sa.String(length=20), nullable=False),
        sa.Column(
            "chat_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("chats.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.UniqueConstraint(
            "user_id", "platform", "chat_id",
            name="uq_notification_chat_whitelist_user_platform_chat",
        ),
    )
    op.create_index(
        "ix_notification_chat_whitelist_user_platform",
        "notification_chat_whitelist",
        ["user_id", "platform"],
    )


def downgrade() -> None:
    op.drop_index(
        "ix_notification_chat_whitelist_user_platform",
        table_name="notification_chat_whitelist",
    )
    op.drop_table("notification_chat_whitelist")
    op.drop_index("ix_notification_settings_user_id", table_name="notification_settings")
    op.drop_table("notification_settings")