from fastapi import HTTPException


class UnauthorizedException(HTTPException):
    def __init__(self, detail: str = "Unauthorized"):
        super().__init__(status_code=401, detail=detail)


class ForbiddenException(HTTPException):
    def __init__(self, detail: str = "Forbidden"):
        super().__init__(status_code=403, detail=detail)


class NotFoundException(HTTPException):
    def __init__(self, detail: str = "Not found"):
        super().__init__(status_code=404, detail=detail)


class ConflictException(HTTPException):
    def __init__(self, detail: str = "Conflict"):
        super().__init__(status_code=409, detail=detail)


class RateLimitException(HTTPException):
    def __init__(self, detail: str = "Too many requests"):
        super().__init__(status_code=429, detail=detail)