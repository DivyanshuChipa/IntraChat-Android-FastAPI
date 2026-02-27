import json
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from datetime import datetime, timezone, timedelta
from lumir.engine import lumir_engine  # 👈 YE ADD KARO
from users import get_all_users, is_user_approved  # 👈 Import is_user_approved
from messages import (
    save_message, 
    create_delivery_entries, 
    get_undelivered_messages, 
    mark_delivered, 
    mark_message_delivered_for_user
)
router = APIRouter()
connected_clients = {}  # username -> set of websockets

IST = timezone(timedelta(hours=5, minutes=30))

# ✅ List of messages that should NOT be saved to DB
SIGNAL_TYPES = {
    "call_request", "call_accept", "call_reject", "call_end", "call_rejected", "call_ended",
    "webrtc_offer", "webrtc_answer", "ice_candidate"
}

async def send_to_user(username: str, message: str, exclude_ws: WebSocket = None):
    """
    Send message to all devices of a user
    exclude_ws: Skip this WebSocket (to avoid echo on sender device)
    """
    if username in connected_clients:
        to_remove = []
        sent_count = 0
        
        for ws in connected_clients[username]:
            if ws == exclude_ws:
                continue
            try:
                await ws.send_text(message)
                sent_count += 1
            except Exception:
                to_remove.append(ws)
        
        # Cleanup dead connections
        for ws in to_remove:
            connected_clients[username].discard(ws)
        
        # If no devices left, remove user entry
        if not connected_clients[username]:
            connected_clients.pop(username, None)
            return False
        
        return sent_count > 0
    return False

@router.websocket("/ws/{username}")
async def websocket_endpoint(ws: WebSocket, username: str):
    await ws.accept()
    
    # 🔥 SECURITY CHECK: Defense in Depth
    # Agar user approved nahi hai, toh connection close kar do.
    if not is_user_approved(username):
        print(f"🚫 Rejected connection from unapproved user: {username}")
        await ws.send_text(json.dumps({
            "type": "error",
            "text": "Your account is not approved yet."
        }))
        await ws.close(code=1008)  # Policy Violation
        return
    
    # Add this connection to user's device set
    if username not in connected_clients:
        connected_clients[username] = set()
    connected_clients[username].add(ws)
    
    print(f"✅ {username} connected (Total devices: {len(connected_clients[username])})")

    # 1. Send offline messages
    pending = get_undelivered_messages(username)
    for msg in pending:
        try:
            offline_data = {
                "type": msg.get("msg_type", "text"),
                "text": msg.get("text", ""),
                "sender": msg.get("sender", "Unknown"),
                "receiver": msg.get("receiver", username),
                "timestamp": msg.get("ts", 0),
                "url": msg.get("file_url"),
                "filename": msg.get("file_name")
            }
            await ws.send_text(json.dumps(offline_data))
            
            # Mark as delivered
            delivery_id = msg.get("delivery_id")
            if delivery_id:
                mark_delivered(delivery_id)
        except Exception as e:
            print(f"Error sending offline message: {e}")

    try:
        # Send connection confirmation
        await ws.send_text(json.dumps({
            "type": "status", 
            "text": "Connected", 
            "user": username
        }))

        while True:
            raw = await ws.receive_text()
            sender = username
            
            try:
                parsed = json.loads(raw)
                parsed["timestamp"] = int(datetime.now(IST).timestamp() * 1000)
                parsed["sender"] = sender  #😈 yeah idendify krega ki sender chutiya hai kon labdekha

                receiver = parsed.get("receiver")
                msg_type = parsed.get("type", "text")

                final_raw = json.dumps(parsed)

                # ==========================================
                # 🤖 LUMIR REAL BOT LOGIC (Phase 3)
                # ==========================================
                if receiver == "Lumir":
                    # 🛑 FIX: Ignore typing signals for Lumir so it doesn't spam empty replies
                    if msg_type == "typing":
                        continue

                    text_content = parsed.get("text", "")
                    file_url = parsed.get("url")
                    file_name = parsed.get("filename")

                    # 1. User ka message DB mein save karo (History ke liye)
                    save_message(
                        text=text_content if msg_type != "file" else f"Shared File: {file_name}",
                        sender=sender,
                        receiver="Lumir",
                        msg_type=msg_type,
                        file_url=file_url,
                        file_name=file_name
                    )

                    # 2. Lumir Engine se reply maango (ye dictionary dega)
                    bot_res = lumir_engine.process(text=text_content, file_url=file_url, sender=sender)

                    # 3. Lumir ka reply DB mein save karo
                    msg_id = save_message(
                        text=bot_res.get("text", ""),
                        sender="Lumir",
                        receiver=sender,
                        msg_type=bot_res.get("type", "text"),
                        file_url=bot_res.get("file_url"),
                        file_name=bot_res.get("file_name")
                    )

                    # 3.5 Create Delivery Entry (Mark as pending initially)
                    # This ensures if the WS is dead, it stays 0 and gets fetched on reconnect
                    create_delivery_entries(msg_id, [sender])

                    # 4. Lumir ka reply user (Android) ko bhejo
                    bot_reply = {
                        "type": bot_res.get("type", "text"),
                        "text": bot_res.get("text", ""),
                        "url": bot_res.get("file_url"),
                        "filename": bot_res.get("file_name"),
                        "sender": "Lumir",
                        "receiver": sender,
                        "timestamp": int(datetime.now(IST).timestamp() * 1000)
                    }

                    # 🪄 THE FIX: Agar engine ne option buttons bheje hain, toh JSON mein add karo!
                    if "options" in bot_res:
                        bot_reply["options"] = bot_res["options"]

                    sent_success = await send_to_user(sender, json.dumps(bot_reply))

                    # If sent successfully, mark as delivered
                    if sent_success:
                        mark_message_delivered_for_user(msg_id, sender)

                    # 🛑 Baki chat logic skip karo (Forwarding ya group logic nahi chalega)
                    continue

                # ==========================================
                # 🚀 WEBRTC SIGNALING LOGIC
                # ==========================================
                if msg_type in SIGNAL_TYPES:
                    # Don't save to DB, just forward
                    if receiver:
                        await send_to_user(receiver, final_raw, exclude_ws=ws)
                        print(f"📡 Signal {msg_type} from {sender} to {receiver}")
                    continue

                # ==========================================
                # ⌨️ TYPING INDICATOR
                # ==========================================
                if msg_type == "typing":
                    if receiver:
                        await send_to_user(receiver, final_raw, exclude_ws=ws)
                    continue

                # ==========================================
                # 💬 NORMAL CHAT LOGIC (Text/File)
                # ==========================================
                file_url = parsed.get("url")
                file_name = parsed.get("filename")
                text_content = parsed.get("text", "")
                
                if msg_type == "file":
                    text_content = f"Shared File: {file_name}"

                # Save to DB
                msg_id = save_message(
                    text=text_content,
                    sender=sender,
                    receiver=receiver,
                    msg_type=msg_type,
                    file_url=file_url,
                    file_name=file_name
                )
                
                # Determine recipients
                recipients = []
                if receiver == "Family Group":
                    all_users = get_all_users()
                    recipients = [u["username"] for u in all_users if u["username"] != sender]
                else:
                    recipients = [receiver]
                
                create_delivery_entries(msg_id, recipients)

                # ✅ FIXED: Send to all recipients with exclude
                for target in recipients:
                    sent = await send_to_user(target, final_raw, exclude_ws=ws)
                    if sent:
                        mark_message_delivered_for_user(msg_id, target)
                
                # ✅ FIXED: Always sync sender's other devices
                await send_to_user(sender, final_raw, exclude_ws=ws)
            
            except Exception as e:
                print(f"❌ Error processing message: {e}")
                continue

    except WebSocketDisconnect:
        # Remove only this connection
        if username in connected_clients:
            connected_clients[username].discard(ws)
            remaining = len(connected_clients[username])
            
            if not connected_clients[username]:
                connected_clients.pop(username, None)
                print(f"🔴 {username} fully disconnected")
            else:
                print(f"🔴 {username} device disconnected ({remaining} devices remaining)")
