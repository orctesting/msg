import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends
from sqlalchemy import select, delete
from sqlalchemy.ext.asyncio import AsyncSession
import structlog

from app.db.session import get_async_session
from app.db.models.user import User
from app.db.models.contact import Contact
from app.db.models.user_contact_dismissal import UserContactDismissal
from app.core.dependencies import get_current_user
from app.core.exceptions import NotFoundException, ConflictException
from app.api.schemas.contact import (
    ContactCreateIn,
    ContactUpdateIn,
    ContactOut,
    ContactListOut,
    ContactDismissIn,
)
from app.api.schemas.base import OkResponse

logger = structlog.get_logger()
router = APIRouter(prefix="/contacts", tags=["contacts"])


def _normalize_phone(phone: str) -> str:
    return phone.strip()


def _to_out(contact: Contact) -> ContactOut:
    return ContactOut(
        id=contact.id,
        phone=contact.phone,
        display_name=contact.display_name,
        contact_user_id=contact.contact_user_id,
        is_registered=contact.contact_user_id is not None,
        created_at=contact.created_at,
        updated_at=contact.updated_at,
    )


async def _find_user_by_phone(session: AsyncSession, phone: str) -> User | None:
    res = await session.execute(select(User).where(User.phone == phone))
    return res.scalar_one_or_none()


@router.get("", response_model=ContactListOut)
async def list_my_contacts(
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    res = await session.execute(
        select(Contact)
        .where(Contact.owner_user_id == current_user.id)
        .order_by(Contact.display_name.asc())
    )
    items = list(res.scalars().all())
    return ContactListOut(contacts=[_to_out(c) for c in items])


@router.post("", response_model=ContactOut, status_code=201)
async def create_my_contact(
    body: ContactCreateIn,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    phone = _normalize_phone(body.phone)

    dup = await session.execute(
        select(Contact).where(
            Contact.owner_user_id == current_user.id,
            Contact.phone == phone,
        )
    )
    if dup.scalar_one_or_none() is not None:
        raise ConflictException("Contact with this phone already exists")

    user = await _find_user_by_phone(session, phone)
    contact = Contact(
        owner_user_id=current_user.id,
        contact_user_id=user.id if user else None,
        phone=phone,
        display_name=body.display_name.strip(),
    )
    session.add(contact)

    # Если был dismissal на этого пользователя — удалить
    if user is not None:
        await session.execute(
            delete(UserContactDismissal).where(
                UserContactDismissal.user_id == current_user.id,
                UserContactDismissal.peer_user_id == user.id,
            )
        )

    await session.commit()
    await session.refresh(contact)
    logger.info("contact_created", owner_id=str(current_user.id), contact_id=str(contact.id))
    return _to_out(contact)


@router.patch("/{contact_id}", response_model=ContactOut)
async def update_my_contact(
    contact_id: uuid.UUID,
    body: ContactUpdateIn,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    res = await session.execute(
        select(Contact).where(
            Contact.id == contact_id,
            Contact.owner_user_id == current_user.id,
        )
    )
    contact = res.scalar_one_or_none()
    if contact is None:
        raise NotFoundException("Contact not found")

    if body.display_name is not None:
        contact.display_name = body.display_name.strip()

    if body.phone is not None:
        new_phone = _normalize_phone(body.phone)
        if new_phone != contact.phone:
            dup = await session.execute(
                select(Contact).where(
                    Contact.owner_user_id == current_user.id,
                    Contact.phone == new_phone,
                    Contact.id != contact_id,
                )
            )
            if dup.scalar_one_or_none() is not None:
                raise ConflictException("Contact with this phone already exists")
            contact.phone = new_phone
            user = await _find_user_by_phone(session, new_phone)
            contact.contact_user_id = user.id if user else None

    contact.updated_at = datetime.now(timezone.utc)
    await session.commit()
    await session.refresh(contact)
    return _to_out(contact)


@router.delete("/{contact_id}", response_model=OkResponse)
async def delete_my_contact(
    contact_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    res = await session.execute(
        select(Contact).where(
            Contact.id == contact_id,
            Contact.owner_user_id == current_user.id,
        )
    )
    contact = res.scalar_one_or_none()
    if contact is None:
        raise NotFoundException("Contact not found")

    await session.delete(contact)
    await session.commit()
    return OkResponse(detail="Contact deleted")


@router.post("/dismiss", response_model=OkResponse)
async def dismiss_peer(
    body: ContactDismissIn,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    if body.peer_user_id == current_user.id:
        raise ConflictException("Cannot dismiss yourself")

    peer_res = await session.execute(select(User).where(User.id == body.peer_user_id))
    if peer_res.scalar_one_or_none() is None:
        raise NotFoundException("Peer user not found")

    existing = await session.execute(
        select(UserContactDismissal).where(
            UserContactDismissal.user_id == current_user.id,
            UserContactDismissal.peer_user_id == body.peer_user_id,
        )
    )
    if existing.scalar_one_or_none() is not None:
        return OkResponse(detail="Already dismissed")

    session.add(
        UserContactDismissal(
            user_id=current_user.id,
            peer_user_id=body.peer_user_id,
        )
    )
    await session.commit()
    return OkResponse(detail="Dismissed")