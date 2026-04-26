import uuid
from datetime import datetime, timezone

from sqlalchemy import Column, ForeignKey, Text, String, DateTime, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import relationship

from app.db.base import Base


class Message(Base):
    __tablename__ = "messages"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    chat_id = Column(UUID(as_uuid=True), ForeignKey("chats.id"), nullable=False, index=True)
    sender_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    content = Column(Text, nullable=False)
    message_type = Column(String(20), nullable=False, default="text")
    idempotency_key = Column(String(255), nullable=True, unique=True, index=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    edited_at = Column(DateTime(timezone=True), nullable=True)

    reply_to_message_id = Column(
        UUID(as_uuid=True),
        ForeignKey("messages.id", ondelete="SET NULL"),
        nullable=True,
        index=True,
    )
    forwarded_from_message_id = Column(
        UUID(as_uuid=True),
        ForeignKey("messages.id", ondelete="SET NULL"),
        nullable=True,
        index=True,
    )
    forwarded_from_sender_name = Column(String(255), nullable=True)

    chat = relationship("Chat", back_populates="messages", foreign_keys=[chat_id])
    sender = relationship("User", foreign_keys=[sender_id])
    reads = relationship(
        "MessageRead",
        back_populates="message",
        foreign_keys="MessageRead.message_id",
        cascade="all, delete-orphan",
        passive_deletes=True,
    )

    reply_to = relationship(
        "Message",
        remote_side="Message.id",
        foreign_keys=[reply_to_message_id],
        post_update=True,
    )
    forwarded_from = relationship(
        "Message",
        remote_side="Message.id",
        foreign_keys=[forwarded_from_message_id],
        post_update=True,
    )
    attachments = relationship(
        "Attachment",
        secondary="message_attachment_links",
        primaryjoin="Message.id == foreign(MessageAttachmentLink.message_id)",
        secondaryjoin="foreign(MessageAttachmentLink.attachment_id) == Attachment.id",
        viewonly=True,
        lazy="selectin",
    )


class MessageRead(Base):
    __tablename__ = "message_reads"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    message_id = Column(
        UUID(as_uuid=True),
        ForeignKey("messages.id", ondelete="CASCADE"),
        nullable=False,
    )
    user_id = Column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
    )
    read_at = Column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        nullable=False,
    )

    message = relationship("Message", back_populates="reads", foreign_keys=[message_id])
    user = relationship("User")