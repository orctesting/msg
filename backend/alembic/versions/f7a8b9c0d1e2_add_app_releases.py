"""add app_releases table

Revision ID: f7a8b9c0d1e2
Revises: e1f2a3b4c5d6
Create Date: 2026-06-08 00:00:00.000000
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "f7a8b9c0d1e2"
down_revision = "e1f2a3b4c5d6"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "app_releases",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("platform", sa.String(length=20), nullable=False),
        sa.Column("channel", sa.String(length=20), nullable=False, server_default="stable"),
        sa.Column("version_name", sa.String(length=32), nullable=False),
        sa.Column("version_code", sa.Integer(), nullable=False),
        sa.Column("min_supported_version_code", sa.Integer(), nullable=True),
        sa.Column("storage_key", sa.Text(), nullable=False),
        sa.Column("file_size_bytes", sa.BigInteger(), nullable=False, server_default="0"),
        sa.Column("sha256", sa.String(length=64), nullable=False),
        sa.Column("release_notes", sa.Text(), nullable=True),
        sa.Column("is_published", sa.Boolean(), nullable=False, server_default=sa.text("false")),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("published_at", sa.DateTime(timezone=True), nullable=True),
        sa.UniqueConstraint("platform", "channel", "version_code", name="uq_app_releases_platform_channel_code"),
    )
    op.create_index(
        "ix_app_releases_lookup",
        "app_releases",
        ["platform", "channel", "is_published", sa.text("version_code DESC")],
    )


def downgrade() -> None:
    op.drop_index("ix_app_releases_lookup", table_name="app_releases")
    op.drop_table("app_releases")