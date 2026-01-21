import json
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from datetime import datetime, timezone, timedelta
from users import get_all_users
from messages import (
    save_message, 
    create_delivery_entries, 
    get_undelivered_messages, 
    mark_delivered, 
    mark_message_delivered_for_user
)

router = APIRouter()
connected_clients = {} # username -> set of websockets

IST = timezone(timedelta(hours=5, minutes=30))

# ✅ List of messages that should NOT be saved to DB
SIGNAL_TYPES = {
    "call_request", "call_accept", "call_reject", "call_end","call_rejected",
    "webrtc_offer", "webrtc_answer", "ice_candidate"
}

async def send_to_user(username: str, message: str, exclude_ws: WebSocket = None):
    if username in connected_clients:
        to_remove = []
        for ws in connected_clients[username]:
            if ws == exclude_ws:
                continue
            try:
                await ws.send_text(message)
            except Exception:
                to_remove.append(ws)

        for ws in to_remove:
            connected_clients[username].discard(ws)

        if not connected_clients[username]:
            connected_clients.pop(username, None)
            return False
        return True
    return False

@router.websocket("/ws/{username}")
async def websocket_endpoint(ws: WebSocket, username: str):
    await ws.accept()
    if username not in connected_clients:
        connected_clients[username] = set()
    connected_clients[username].add(ws)

    # 1. Send offline messages (Same as before)
    pending = get_undelivered_messages(username)
    for msg in pending:
        # ... (Offline msg logic same rahega) ...
        # (Short karne ke liye yahan code skip kar raha hu, purana wala hi rahega)
        pass 

    try:
        await ws.send_text(json.dumps({
            "type": "status", "text": "Connected", "user": username
        }))

        while True:
            raw = await ws.receive_text()
            sender = username
            
            try:
                parsed = json.loads(raw)
                parsed["timestamp"] = int(datetime.now(IST).timestamp() * 1000)
                
                # Receiver aur Type nikalo
                receiver = parsed.get("receiver")
                msg_type = parsed.get("type", "text")
                
                # Final JSON jo bhejna hai
                final_raw = json.dumps(parsed)

                # ==========================================
                # 🚀 WEBRTC SIGNALING LOGIC (NEW)
                # ==========================================
                if msg_type in SIGNAL_TYPES:
                    # Isko DB me SAVE NAHI karna hai
                    # Bas receiver ko forward kar do
                    if receiver:
                        await send_to_user(receiver, final_raw, exclude_ws=ws)
                        print(f"📡 Signal {msg_type} from {sender} to {receiver}")
                    
                    continue # Loop wapas ghuma do, niche save logic me mat jao
                
                # ==========================================
                # 💬 NORMAL CHAT LOGIC (OLD)
                # ==========================================

                # Typing (Already handled, but can be simplified)
                if msg_type == "typing":
                    if receiver:
                        await send_to_user(receiver, final_raw, exclude_ws=ws)
                    continue

                # File / Text Logic
                file_url = parsed.get("url")
                file_name = parsed.get("filename")
                text_content = parsed.get("text", "")
                
                if msg_type == "file":
                    text_content = f"Shared File: {file_name}"

                # Sirf Text/File hi DB me save honge
                msg_id = save_message(
                    text=text_content,
                    sender=sender,
                    receiver=receiver,
                    msg_type=msg_type,
                    file_url=file_url,
                    file_name=file_name
                )
                
                # Delivery Logic (Same as before)
                recipients = []
                if receiver == "Family Group":
                    all_users = get_all_users()
                    recipients = [u["username"] for u in all_users if u["username"] != sender]
                else:
                    recipients = [receiver]
                
                create_delivery_entries(msg_id, recipients)

                for target in recipients:
                    sent = await send_to_user(target, final_raw)
                    if sent:
                        mark_message_delivered_for_user(msg_id, target)

                # Sync with sender's other devices
                await send_to_user(sender, final_raw, exclude_ws=ws)
            
            except Exception as e:
                print(f"Error processing message: {e}")
                continue

    except WebSocketDisconnect:
        if username in connected_clients:
            connected_clients[username].discard(ws)
            if not connected_clients[username]:
                connected_clients.pop(username, None)
        print(f"🔴 {username} disconnected")
