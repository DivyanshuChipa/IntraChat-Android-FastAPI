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
connected_clients = {}

IST = timezone(timedelta(hours=5, minutes=30))

# ✅ List of messages that should NOT be saved to DB
SIGNAL_TYPES = {
    "call_request", "call_accept", "call_reject", "call_end","call_rejected",
    "webrtc_offer", "webrtc_answer", "ice_candidate"
}

async def send_to_user(username: str, message: str):
    if username in connected_clients:
        try:
            await connected_clients[username].send_text(message)
            return True
        except Exception:
            connected_clients.pop(username, None)
    return False

@router.websocket("/ws/{username}")
async def websocket_endpoint(ws: WebSocket, username: str):
    await ws.accept()
    connected_clients[username] = ws

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
                    if receiver and receiver in connected_clients:
                        await connected_clients[receiver].send_text(final_raw)
                        print(f"📡 Signal {msg_type} from {sender} to {receiver}")
                    else:
                        # Agar user online nahi hai, toh call request fail ho jayegi
                        # Hum sender ko bata sakte hain (Optional)
                        print(f"⚠️ User {receiver} offline for call.")
                    
                    continue # Loop wapas ghuma do, niche save logic me mat jao
                
                # ==========================================
                # 💬 NORMAL CHAT LOGIC (OLD)
                # ==========================================

                # Typing (Already handled, but can be simplified)
                if msg_type == "typing":
                    if receiver and receiver in connected_clients:
                        await connected_clients[receiver].send_text(final_raw)
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
                    if target in connected_clients:
                        sent = await send_to_user(target, final_raw)
                        if sent:
                            mark_message_delivered_for_user(msg_id, target)
            
            except Exception as e:
                print(f"Error processing message: {e}")
                continue

    except WebSocketDisconnect:
        connected_clients.pop(username, None)
        print(f"🔴 {username} disconnected")
