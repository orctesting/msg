from fastapi import APIRouter

from app.api.v1 import auth, chats, messages, admin, push, ws, devices

api_v1_router = APIRouter()

api_v1_router.include_router(auth.router)
api_v1_router.include_router(chats.router)
api_v1_router.include_router(messages.router)
api_v1_router.include_router(admin.router)
api_v1_router.include_router(push.router)
api_v1_router.include_router(devices.router)
api_v1_router.include_router(ws.router)


@api_v1_router.get("/ping")
async def ping():
    return {"message": "pong"}