from fastapi import APIRouter, WebSocket, WebSocketDisconnect
router = APIRouter()

# Placeholder: You'll later add signaling endpoints (offer/answer/ice)
# For now keep a stub or simple ping endpoint.
@router.get("/ready")
async def ready():
    return {"ok": True, "msg": "calls module placeholder"}
