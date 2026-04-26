import uuid
from datetime import datetime, date

from sqlalchemy import String, Boolean, DateTime, Date, ForeignKey, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base


class User(Base):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    phone: Mapped[str] = mapped_column(String(20), unique=True, nullable=False)
    display_name: Mapped[str] = mapped_column(String(255), nullable=False)
    username: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    first_name: Mapped[str | None] = mapped_column(String(100), nullable=True)
    last_name: Mapped[str | None] = mapped_column(String(100), nullable=True)
    birth_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    bio: Mapped[str | None] = mapped_column(String(1000), nullable=True)
    email: Mapped[str | None] = mapped_column(String(255), nullable=True)

    primary_avatar_attachment_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("attachments.id", ondelete="SET NULL"),
        nullable=True,
    )
    primary_avatar_thumb_attachment_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("attachments.id", ondelete="SET NULL"),
        nullable=True,
    )

    role: Mapped[str] = mapped_column(String(20), nullable=False, default="user")
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now(), onupdate=func.now()
    )

    devices: Mapped[list["Device"]] = relationship("Device", back_populates="user")
    chat_memberships: Mapped[list["ChatMember"]] = relationship("ChatMember", back_populates="user")

    primary_avatar = relationship("Attachment", foreign_keys=[primary_avatar_attachment_id])
    primary_avatar_thumb = relationship("Attachment", foreign_keys=[primary_avatar_thumb_attachment_id])