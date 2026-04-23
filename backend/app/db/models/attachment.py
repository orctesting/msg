import uuid
from datetime import datetime

from sqlalchemy import String, Integer, BigInteger, Text, DateTime, ForeignKey, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base


class Attachment(Base):
    __tablename__ = "attachments"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    uploader_user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
        index=True,
    )
    chat_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("chats.id", ondelete="CASCADE"),
        nullable=True,
        index=True,
    )
    message_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("messages.id", ondelete="CASCADE"),
        nullable=True,
        index=True,
    )

    storage_key: Mapped[str] = mapped_column(Text, nullable=False)
    original_filename: Mapped[str] = mapped_column(String(500), nullable=False)
    mime_type: Mapped[str] = mapped_column(String(255), nullable=False)
    size_bytes: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    file_kind: Mapped[str] = mapped_column(String(20), nullable=False, default="file")

    width: Mapped[int | None] = mapped_column(Integer, nullable=True)
    height: Mapped[int | None] = mapped_column(Integer, nullable=True)
    duration_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    thumbnail_key: Mapped[str | None] = mapped_column(Text, nullable=True)
    sha256: Mapped[str | None] = mapped_column(String(64), nullable=True)

    status: Mapped[str] = mapped_column(
        String(20), nullable=False, default="pending", index=True
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
        onupdate=func.now(),
    )

    uploader = relationship("User", foreign_keys=[uploader_user_id])
    message = relationship(
        "Message",
        foreign_keys=[message_id],
        back_populates="attachments",
    )