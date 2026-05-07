"""user profile fields + user_avatars

Revision ID: c9d0e1f2a3b4
Revises: b8c9d0e1f2a3
Create Date: 2026-04-28 00:00:00.000000
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "c9d0e1f2a3b4"
down_revision = "b8c9d0e1f2a3"
branch_labels = None
depends_on = None


def upgrade() -> None:
    with op.batch_alter_table("users") as batch_op:
        batch_op.add_column(sa.Column("username", sa.String(length=64), nullable=True))
        batch_op.add_column(sa.Column("first_name", sa.String(length=100), nullable=True))
        batch_op.add_column(sa.Column("last_name", sa.String(length=100), nullable=True))
        batch_op.add_column(sa.Column("birth_date", sa.Date(), nullable=True))
        batch_op.add_column(sa.Column("bio", sa.String(length=1000), nullable=True))
        batch_op.add_column(sa.Column("email", sa.String(length=255), nullable=True))
        batch_op.add_column(
            sa.Column(
                "primary_avatar_attachment_id",
                postgresql.UUID(as_uuid=True),
                nullable=True,
            )
        )
        batch_op.add_column(
            sa.Column(
                "primary_avatar_thumb_attachment_id",
                postgresql.UUID(as_uuid=True),
                nullable=True,
            )
        )

    op.create_foreign_key(
        "fk_users_primary_avatar",
        "users",
        "attachments",
        ["primary_avatar_attachment_id"],
        ["id"],
        ondelete="SET NULL",
    )
    op.create_foreign_key(
        "fk_users_primary_avatar_thumb",
        "users",
        "attachments",
        ["primary_avatar_thumb_attachment_id"],
        ["id"],
        ondelete="SET NULL",
    )

    op.create_table(
        "user_avatars",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "full_attachment_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("attachments.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "crop_attachment_id",
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
    )
    op.create_index("ix_user_avatars_user_id", "user_avatars", ["user_id"])

    # Backfill username для существующих пользователей через транслитерацию
    op.execute(sa.text("""
        DO $$
        DECLARE
            r RECORD;
            base_un TEXT;
            try_un TEXT;
            counter INT;
        BEGIN
            FOR r IN SELECT id, display_name FROM users WHERE username IS NULL LOOP
                -- Простой ASCII-фолбэк: оставляем только латиницу/цифры/_/./-/#
                base_un := lower(regexp_replace(coalesce(r.display_name, ''), '[^a-zA-Z0-9_\-.#]+', '_', 'g'));
                base_un := regexp_replace(base_un, '^_+|_+$', '', 'g');
                IF base_un = '' OR base_un IS NULL THEN
                    base_un := 'user_' || substr(r.id::text, 1, 8);
                END IF;
                try_un := base_un;
                counter := 0;
                WHILE EXISTS(SELECT 1 FROM users WHERE username = try_un) LOOP
                    counter := counter + 1;
                    try_un := base_un || '#' || lpad(counter::text, 3, '0');
                END LOOP;
                UPDATE users SET username = try_un WHERE id = r.id;
            END LOOP;
        END $$;
    """))

    # Делаем username NOT NULL и unique
    with op.batch_alter_table("users") as batch_op:
        batch_op.alter_column("username", existing_type=sa.String(length=64), nullable=False)
        batch_op.create_unique_constraint("uq_users_username", ["username"])


def downgrade() -> None:
    with op.batch_alter_table("users") as batch_op:
        batch_op.drop_constraint("uq_users_username", type_="unique")

    op.drop_index("ix_user_avatars_user_id", table_name="user_avatars")
    op.drop_table("user_avatars")

    op.drop_constraint("fk_users_primary_avatar_thumb", "users", type_="foreignkey")
    op.drop_constraint("fk_users_primary_avatar", "users", type_="foreignkey")

    with op.batch_alter_table("users") as batch_op:
        batch_op.drop_column("primary_avatar_thumb_attachment_id")
        batch_op.drop_column("primary_avatar_attachment_id")
        batch_op.drop_column("email")
        batch_op.drop_column("bio")
        batch_op.drop_column("birth_date")
        batch_op.drop_column("last_name")
        batch_op.drop_column("first_name")
        batch_op.drop_column("username")