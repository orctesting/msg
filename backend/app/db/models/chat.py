import uuid
from datetime import datetime, timezone

from sqlalchemy import Column, String, DateTime, ForeignKey
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import relationship

from app.db.base import Base


class Chat(Base):
    __tablename__ = "chats"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(String(255), nullable=False)
    type = Column(String(20), default="group")
    created_at = Column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))

    pinned_message_id = Column(
        UUID(as_uuid=True),
        ForeignKey("messages.id", ondelete="SET NULL"),
        nullable=True,
    )
    pinned_by_user_id = Column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
    )
    pinned_at = Column(DateTime(timezone=True), nullable=True)

    messages = relationship(
        "Message",
        back_populates="chat",
        foreign_keys="Message.chat_id",
    )
    pinned_message = relationship("Message", foreign_keys=[pinned_message_id], post_update=True)
    pinned_by = relationship("User", foreign_keys=[pinned_by_user_id])