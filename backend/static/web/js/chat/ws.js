import { state } from "./state.js";
import { logout } from "./ui.js";
import { renderUserList } from "./users.js";
import { displayMessage, showTypingIndicator } from "./messages.js";
import {
  handleCallRequest,
  setupWebRTC,
  stopWebRTC,
  hideCallOverlay,
  handleOffer,
  handleAnswer,
  handleIceCandidate
} from "./webrtc.js";

export function connectWS() {
  state.ws = new WebSocket(`ws://${location.host}/ws/${state.myUsername}`);

  state.ws.onopen = () => {
    console.log("WebSocket connected");
  };

  state.ws.onmessage = (event) => {
    let msg;
    try {
      msg = JSON.parse(event.data);
    } catch (e) {
      console.error("Invalid WS JSON:", event.data);
      return;
    }

    if (msg.type === "error") {
      alert("Session Error: " + msg.text);
      logout();
      return;
    }

    let chatPartner = null;
    if (msg.receiver === "Family Group") {
      chatPartner = "Family Group";
    } else if (msg.sender === state.myUsername) {
      chatPartner = msg.receiver;
    } else if (msg.receiver === state.myUsername) {
      chatPartner = msg.sender;
    }

    const isRelevant = chatPartner === state.currentReceiver;

    if (chatPartner && ["text", "file", "utility_options"].includes(msg.type)) {
      state.latestMessageTimes[chatPartner] = msg.timestamp || Date.now();
      if (!isRelevant && msg.sender !== state.myUsername) {
        state.unreadCounts[chatPartner] = (state.unreadCounts[chatPartner] || 0) + 1;
      }
      renderUserList();
    }

    switch (msg.type) {
      case "text":
      case "utility_options":
        if (isRelevant) {
          displayMessage(
            msg.sender,
            msg.text || msg.message || "",
            msg.sender === state.myUsername ? "sent" : "received",
            null,
            null,
            false,
            null,
            "",
            msg.options,
            msg.timestamp || Date.now()
          );
        }
        break;

      case "typing":
        if (msg.sender === state.currentReceiver) {
          showTypingIndicator(msg.sender);
        }
        break;

      case "file":
        if (isRelevant) {
          const fileUrl = msg.url || msg.file_url || msg.fileUrl || null;
          const fileName = msg.filename || msg.fileName || msg.file_name || "file";
          displayMessage(
            msg.sender,
            msg.text || "📎 Shared File: " + fileName,
            msg.sender === state.myUsername ? "sent" : "received",
            fileUrl,
            null,
            false,
            null,
            "",
            msg.options,
            msg.timestamp || Date.now()
          );
        }
        break;

      case "call_request":
        handleCallRequest(msg);
        break;

      case "call_accept":
        document.getElementById("call-status").innerText = "Connecting...";
        setupWebRTC();
        break;

      case "call_reject":
      case "call_rejected":
        alert(msg.sender + " rejected the call");
        stopWebRTC();
        hideCallOverlay();
        break;

      case "call_end":
      case "call_ended":
        stopWebRTC();
        hideCallOverlay();
        break;

      case "webrtc_offer":
        if (
          document.getElementById("call-overlay").style.display === "flex" &&
          document.getElementById("accept-call").style.display === "block"
        ) {
          state.pendingOffer = msg;
        } else {
          handleOffer(msg);
        }
        break;

      case "webrtc_answer":
        handleAnswer(msg);
        break;

      case "ice_candidate":
        handleIceCandidate(msg);
        break;

      case "status":
        break;

      default:
        console.warn("Unknown WS message:", msg);
    }
  };

  state.ws.onerror = (e) => {
    console.error("WebSocket error:", e);
  };

  state.ws.onclose = (e) => {
    console.warn("WebSocket closed", e.code, e.reason);
    if (e.code === 1008) {
      alert("Access Denied: " + (e.reason || "Account not registered or approved on this server."));
      logout();
    }
  };
}
