"""message edit/reply/forward + pin + deleted_messages + user_pinned_dismissals

Revision ID: c3d4e5f6a7b8
Revises: b2c3d4e5f6a7
Create Date: 2026-04-18 00:00:00.000000
"""

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "c3d4e5f6a7b8"
down_revision = "b2c3d4e5f6a7"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # messages: edited_at, reply_to, forwarded_from
    with op.batch_alter_table("messages") as batch_op:
        batch_op.add_column(sa.Column("edited_at", sa.DateTime(timezone=True), nullable=True))
        batch_op.add_column(sa.Column("reply_to_message_id", postgresql.UUID(as_uuid=True), nullable=True))
        batch_op.add_column(sa.Column("forwarded_from_message_id", postgresql.UUID(as_uuid=True), nullable=True))
        batch_op.add_column(sa.Column("forwarded_from_sender_name", sa.String(length=255), nullable=True))

    op.create_foreign_key(
        "fk_messages_reply_to",
        "messages",
        "messages",
        ["reply_to_message_id"],
        ["id"],
        ondelete="SET NULL",
    )
    op.create_foreign_key(
        "fk_messages_forwarded_from",
        "messages",
        "messages",
        ["forwarded_from_message_id"],
        ["id"],
        ondelete="SET NULL",
    )
    op.create_index("ix_messages_reply_to_message_id", "messages", ["reply_to_message_id"])
    op.create_index("ix_messages_forwarded_from_message_id", "messages", ["forwarded_from_message_id"])

    # chats: pinned_message
    with op.batch_alter_table("chats") as batch_op:
        batch_op.add_column(sa.Column("pinned_message_id", postgresql.UUID(as_uuid=True), nullable=True))
        batch_op.add_column(sa.Column("pinned_by_user_id", postgresql.UUID(as_uuid=True), nullable=True))
        batch_op.add_column(sa.Column("pinned_at", sa.DateTime(timezone=True), nullable=True))

    op.create_foreign_key(
        "fk_chats_pinned_message",
        "chats",
        "messages",
        ["pinned_message_id"],
        ["id"],
        ondelete="SET NULL",
    )
    op.create_foreign_key(
        "fk_chats_pinned_by_user",
        "chats",
        "users",
        ["pinned_by_user_id"],
        ["id"],
        ondelete="SET NULL",
    )

    # deleted_messages
    op.create_table(
        "deleted_messages",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("original_message_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("chat_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("sender_id", postgresql.UUID(as_uuid=True), nullable=True),
        sa.Column("content", sa.Text(), nullable=False),
        sa.Column("message_type", sa.String(length=20), nullable=False),
        sa.Column("original_created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("edited_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("reply_to_message_id", postgresql.UUID(as_uuid=True), nullable=True),
        sa.Column("forwarded_from_message_id", postgresql.UUID(as_uuid=True), nullable=True),
        sa.Column("forwarded_from_sender_name", sa.String(length=255), nullable=True),
        sa.Column("deleted_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("deleted_by_user_id", postgresql.UUID(as_uuid=True), nullable=True),
        sa.ForeignKeyConstraint(["deleted_by_user_id"], ["users.id"], ondelete="SET NULL"),
    )
    op.create_index("ix_deleted_messages_original_message_id", "deleted_messages", ["original_message_id"])
    op.create_index("ix_deleted_messages_chat_id", "deleted_messages", ["chat_id"])

    # user_pinned_dismissals
    op.create_table(
        "user_pinned_dismissals",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("chat_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("pinned_message_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("dismissed_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["chat_id"], ["chats.id"], ondelete="CASCADE"),
        sa.UniqueConstraint("user_id", "chat_id", name="uq_user_pinned_dismissal_user_chat"),
    )
    op.create_index("ix_user_pinned_dismissals_user_id", "user_pinned_dismissals", ["user_id"])
    op.create_index("ix_user_pinned_dismissals_chat_id", "user_pinned_dismissals", ["chat_id"])


def downgrade() -> None:
    op.drop_index("ix_user_pinned_dismissals_chat_id", table_name="user_pinned_dismissals")
    op.drop_index("ix_user_pinned_dismissals_user_id", table_name="user_pinned_dismissals")
    op.drop_table("user_pinned_dismissals")

    op.drop_index("ix_deleted_messages_chat_id", table_name="deleted_messages")
    op.drop_index("ix_deleted_messages_original_message_id", table_name="deleted_messages")
    op.drop_table("deleted_messages")

    op.drop_constraint("fk_chats_pinned_by_user", "chats", type_="foreignkey")
    op.drop_constraint("fk_chats_pinned_message", "chats", type_="foreignkey")
    with op.batch_alter_table("chats") as batch_op:
        batch_op.drop_column("pinned_at")
        batch_op.drop_column("pinned_by_user_id")
        batch_op.drop_column("pinned_message_id")

    op.drop_index("ix_messages_forwarded_from_message_id", table_name="messages")
    op.drop_index("ix_messages_reply_to_message_id", table_name="messages")
    op.drop_constraint("fk_messages_forwarded_from", "messages", type_="foreignkey")
    op.drop_constraint("fk_messages_reply_to", "messages", type_="foreignkey")
    with op.batch_alter_table("messages") as batch_op:
        batch_op.drop_column("forwarded_from_sender_name")
        batch_op.drop_column("forwarded_from_message_id")
        batch_op.drop_column("reply_to_message_id")
        batch_op.drop_column("edited_at")