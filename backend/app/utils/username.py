import re
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models.user import User

_RU_TO_EN = {
    "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e", "ё": "e",
    "ж": "zh", "з": "z", "и": "i", "й": "y", "к": "k", "л": "l", "м": "m",
    "н": "n", "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u",
    "ф": "f", "х": "h", "ц": "ts", "ч": "ch", "ш": "sh", "щ": "sch",
    "ъ": "", "ы": "y", "ь": "", "э": "e", "ю": "yu", "я": "ya",
}

USERNAME_PATTERN = re.compile(r"^[a-z0-9_\-.#]{1,64}$")
_ALLOWED_CHARS = re.compile(r"[^a-z0-9_\-.#]+")


def transliterate(text: str) -> str:
    text = (text or "").strip().lower()
    out = []
    for ch in text:
        if ch in _RU_TO_EN:
            out.append(_RU_TO_EN[ch])
        elif ch == " ":
            out.append("_")
        else:
            out.append(ch)
    result = "".join(out)
    result = _ALLOWED_CHARS.sub("_", result)
    result = re.sub(r"_+", "_", result).strip("_")
    return result


def is_valid_username(value: str) -> bool:
    return bool(USERNAME_PATTERN.match(value or ""))


async def generate_unique_username(
    session: AsyncSession,
    first_name: str | None,
    last_name: str | None,
    fallback_id: str = "",
) -> str:
    base_parts = [transliterate(first_name or ""), transliterate(last_name or "")]
    base = "_".join([p for p in base_parts if p])
    if not base:
        base = f"user_{fallback_id[:8]}" if fallback_id else "user"
    base = base[:60]

    candidate = base
    counter = 0
    while True:
        existing = await session.execute(
            select(User).where(User.username == candidate)
        )
        if existing.scalar_one_or_none() is None:
            return candidate
        counter += 1
        candidate = f"{base}#{counter:03d}"
        if counter > 999:
            candidate = f"{base}#{fallback_id[:8]}"
            return candidate