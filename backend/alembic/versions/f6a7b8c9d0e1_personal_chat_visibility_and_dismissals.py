"""personal chat visibility + contact dismissals

Revision ID: f6a7b8c9d0e1
Revises: e5f6a7b8c9d0
Create Date: 2026-04-19 02:00:00.000000
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "f6a7b8c9d0e1"
down_revision = "e5f6a7b8c9d0"
branch_labels = None
depends_on = None


def upgrade() -> None:
    with op.batch_alter_table("chat_members") as batch_op:
        batch_op.add_column(
            sa.Column(
                "is_visible",
                sa.Boolean(),
                nullable=False,
                server_default=sa.text("true"),
            )
        )

    op.create_table(
        "user_contact_dismissals",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "peer_user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.UniqueConstraint("user_id", "peer_user_id", name="uq_user_contact_dismissals_user_peer"),
    )
    op.create_index(
        "ix_user_contact_dismissals_user_id",
        "user_contact_dismissals",
        ["user_id"],
    )


def downgrade() -> None:
    op.drop_index("ix_user_contact_dismissals_user_id", table_name="user_contact_dismissals")
    op.drop_table("user_contact_dismissals")
    with op.batch_alter_table("chat_members") as batch_op:
        batch_op.drop_column("is_visible")