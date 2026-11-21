import { state } from "./state.js";

const rtcConfig = {
  iceServers: [
    { urls: "stun:stun.l.google.com:19302" },
    { urls: "stun:stun1.l.google.com:19302" }
  ]
};

export function showCallOverlay(username, status) {
  document.getElementById("call-username").innerText = username;
  document.getElementById("call-status").innerText = status;
  document.getElementById("call-overlay").style.display = "flex";

  document.getElementById("call-avatar").src =
    state.userAvatars[username] || "https://via.placeholder.com/100";
}

export function hideCallOverlay() {
  document.getElementById("call-overlay").style.display = "none";
  const ringtone = document.getElementById("ringtone");
  ringtone.pause();
  ringtone.currentTime = 0;
  state.pendingOffer = null;
}

export async function startCall() {
  if (!state.currentReceiver || state.currentReceiver === "Family Group") return;

  state.callReceiver = state.currentReceiver;
  state.isCaller = true;

  showCallOverlay(state.callReceiver, "Calling...");
  document.getElementById("accept-call").style.display = "none";
  document.getElementById("reject-call").style.display = "none";
  document.getElementById("end-call").style.display = "block";

  const myPhoto = state.userAvatars[state.myUsername];
  state.ws.send(JSON.stringify({
    type: "call_request",
    receiver: state.callReceiver,
    sender: state.myUsername,
    profile_photo: myPhoto || null
  }));

  setupWebRTC();
}

export async function handleCallRequest(msg) {
  state.callReceiver = msg.sender;
  if (msg.profile_photo) {
    state.userAvatars[msg.sender] = msg.profile_photo;
  }
  state.isCaller = false;

  showCallOverlay(state.callReceiver, "Incoming Call...");
  document.getElementById("accept-call").style.display = "block";
  document.getElementById("reject-call").style.display = "block";
  document.getElementById("end-call").style.display = "none";

  document.getElementById("ringtone").play().catch((e) => console.log("Audio play failed:", e));
}

export async function acceptCall() {
  document.getElementById("ringtone").pause();
  document.getElementById("call-status").innerText = "Connecting...";
  document.getElementById("accept-call").style.display = "none";
  document.getElementById("reject-call").style.display = "none";
  document.getElementById("end-call").style.display = "block";

  state.ws.send(JSON.stringify({
    type: "call_accept",
    receiver: state.callReceiver
  }));

  if (state.pendingOffer) {
    await handleOffer(state.pendingOffer);
    state.pendingOffer = null;
  } else {
    await setupWebRTC();
  }
}

export function rejectCall() {
  state.ws.send(JSON.stringify({
    type: "call_rejected",
    receiver: state.callReceiver
  }));
  hideCallOverlay();
}

export function endCall() {
  state.ws.send(JSON.stringify({
    type: "call_ended",
    receiver: state.callReceiver
  }));
  stopWebRTC();
  hideCallOverlay();
}

export async function setupWebRTC() {
  if (state.peerConnection) return;
  try {
    state.localStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
    state.peerConnection = new RTCPeerConnection(rtcConfig);

    state.localStream.getTracks().forEach((track) => {
      state.peerConnection.addTrack(track, state.localStream);
    });

    state.peerConnection.ontrack = (event) => {
      const remoteAudio = document.getElementById("remote-audio") || document.createElement("audio");
      remoteAudio.id = "remote-audio";
      remoteAudio.autoplay = true;
      remoteAudio.srcObject = event.streams[0];
      if (!remoteAudio.parentElement) document.body.appendChild(remoteAudio);
    };

    state.peerConnection.onicecandidate = (event) => {
      if (event.candidate) {
        state.ws.send(JSON.stringify({
          type: "ice_candidate",
          receiver: state.callReceiver,
          candidate: event.candidate
        }));
      }
    };

    if (state.isCaller) {
      const offer = await state.peerConnection.createOffer();
      await state.peerConnection.setLocalDescription(offer);
      state.ws.send(JSON.stringify({
        type: "webrtc_offer",
        receiver: state.callReceiver,
        sdp: offer.sdp
      }));
    }
  } catch (e) {
    console.error("WebRTC Setup Error:", e);
    if (location.protocol !== "https:" && location.hostname !== "localhost") {
      alert("Microphone access failed. Web calls require HTTPS to work on LAN.");
    } else {
      alert("Could not access microphone. Please check permissions.");
    }
    endCall();
  }
}

export async function handleOffer(msg) {
  if (!state.peerConnection) await setupWebRTC();
  await state.peerConnection.setRemoteDescription(new RTCSessionDescription({ type: "offer", sdp: msg.sdp }));
  const answer = await state.peerConnection.createAnswer();
  await state.peerConnection.setLocalDescription(answer);
  state.ws.send(JSON.stringify({
    type: "webrtc_answer",
    receiver: state.callReceiver,
    sdp: answer.sdp
  }));
}

export async function handleAnswer(msg) {
  if (!state.peerConnection) return;
  try {
    await state.peerConnection.setRemoteDescription(new RTCSessionDescription({ type: "answer", sdp: msg.sdp }));
    document.getElementById("call-status").innerText = "Connected";
  } catch (e) {
    console.error("Handle Answer Error:", e);
  }
}

export async function handleIceCandidate(msg) {
  if (state.peerConnection) {
    await state.peerConnection.addIceCandidate(new RTCIceCandidate(msg.candidate));
  }
}

export function stopWebRTC() {
  if (state.localStream) {
    state.localStream.getTracks().forEach((track) => track.stop());
    state.localStream = null;
  }
  if (state.peerConnection) {
    state.peerConnection.close();
    state.peerConnection = null;
  }
  document.getElementById("remote-audio")?.remove();
  state.isCaller = false;
  state.pendingOffer = null;
}
