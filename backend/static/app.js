/***********************
 * LOGIN (index.html)
 ***********************/
let isLoginMode = true;

function toggleAuthMode() {
  isLoginMode = !isLoginMode;
  const btn = document.getElementById("auth-btn");
  const link = document.getElementById("toggle-link");
  const msg = document.getElementById("toggle-msg");

  if (!btn || !link || !msg) return;

  if (isLoginMode) {
    btn.innerText = "LOGIN";
    msg.innerText = "No account?";
    link.innerText = "Register";
  } else {
    btn.innerText = "REGISTER";
    msg.innerText = "Have an account?";
    link.innerText = "Login";
  }
  const errorEl = document.getElementById("error");
  if (errorEl) errorEl.innerText = "";
}

async function login() {
  console.log("Login attempt...");
  const userEl = document.getElementById("username");
  const passEl = document.getElementById("password");
  const errorEl = document.getElementById("error");

  if (!userEl || !passEl) return;
  const username = userEl.value.trim();
  const password = passEl.value.trim();

  if (!username || !password) {
    if (errorEl) errorEl.innerText = "Please fill all fields";
    return;
  }

  const endpoint = isLoginMode ? "/login" : "/register";

  try {
    const res = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password })
    });

    const data = await res.json();

    if (data.success) {
      if (isLoginMode) {
        localStorage.setItem("username", data.username);
        localStorage.setItem("token", data.token);
        window.location.href = "/chat.html";
      } else {
        alert("Registration successful! Please login.");
        toggleAuthMode();
      }
    } else {
      if (errorEl) errorEl.innerText = data.message || (isLoginMode ? "Login failed" : "Registration failed");
    }
  } catch (e) {
    console.error("Auth error:", e);
    if (errorEl) errorEl.innerText = "Server connection error";
  }
}

// Alias for compatibility
const handleAuth = login;

/***********************
 * CHAT (chat.html)
 ***********************/
const myUsername = localStorage.getItem("username");
let ws = null;
let currentReceiver = "Family Group";
let userAvatars = {}; // username -> avatarUrl

// Auto-redirect logic
if (window.location.pathname === "/" || window.location.pathname.includes("index.html")) {
  if (myUsername) {
    window.location.href = "/chat.html";
  }
}

if (window.location.pathname.includes("chat.html")) {
  if (!myUsername) {
    window.location.href = "/index.html";
  } else {
    const profileEl = document.getElementById("profile-name");
    if (profileEl) profileEl.innerText = myUsername;
    initTheme();
    initSidebar();
    loadUsers();
    connectWS();
  }
}

function initTheme() {
  const theme = localStorage.getItem("theme") || "light";
  document.body.setAttribute("data-theme", theme);
}

function toggleTheme() {
  const current = document.body.getAttribute("data-theme");
  const next = current === "dark" ? "light" : "dark";
  document.body.setAttribute("data-theme", next);
  localStorage.setItem("theme", next);
}

function initSidebar() {
  const state = localStorage.getItem("sidebarMinimized");
  if (state === "true") {
    document.getElementById("sidebar").classList.add("minimized");
  }
}

function toggleSidebar() {
  const sidebar = document.getElementById("sidebar");
  sidebar.classList.toggle("minimized");
  localStorage.setItem("sidebarMinimized", sidebar.classList.contains("minimized"));
}

function showSidebar() {
  document.body.classList.remove("chat-open");
}

function toggleSettings() {
  const panel = document.getElementById("settings-panel");
  panel.style.display = panel.style.display === "flex" ? "none" : "flex";
}

async function logout() {
  localStorage.removeItem("username");
  localStorage.removeItem("token");
  window.location = "/index.html";
}

async function uploadPhoto() {
  const fileInput = document.getElementById("photo-input");
  if (fileInput.files.length === 0) return;

  const token = localStorage.getItem("token");
  const formData = new FormData();
  formData.append("username", myUsername);
  formData.append("file", fileInput.files[0]);

  try {
    const res = await fetch("/profile/upload_profile", {
      method: "POST",
      headers: { "Authorization": `Bearer ${token}` },
      body: formData
    });
    const data = await res.json();
    if (data.success) {
      document.getElementById("my-avatar").src = data.profile_photo + "?t=" + Date.now();
      alert("Profile photo updated!");
      loadUsers(); // refresh user list to show new photo
    } else {
      alert("Upload failed");
    }
  } catch (e) {
    console.error("Upload error:", e);
    alert("Error uploading photo");
  }
}

async function deleteAccount() {
  const password = prompt("Enter your password to confirm account deletion:");
  if (!password) return;

  if (!confirm("Are you sure you want to permanently delete your account?")) return;

  const token = localStorage.getItem("token");
  try {
    const res = await fetch("/delete_account", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${token}`
      },
      body: JSON.stringify({ username: myUsername, password: password })
    });
    const data = await res.json();
    if (data.success) {
      alert("Account deleted.");
      logout();
    } else {
      alert("Error: " + data.message);
    }
  } catch (e) {
    console.error("Delete error:", e);
    alert("Server error during deletion");
  }
}

/***********************
 * LOAD USERS
 ***********************/
async function loadUsers() {
  try {
    const res = await fetch("/users");
    const data = await res.json();

    if (!data.success || !Array.isArray(data.users)) {
      console.error("Invalid users response:", data);
      return;
    }

    const list = document.getElementById("user-list");
    list.innerHTML = "";

    // Family Group
    addUserToList({
      username: "Family Group",
      profile_photo: null
    });

    data.users.forEach(user => {
      if (user.username !== myUsername) {
        addUserToList(user);
      } else {
        // Current user
        if (user.profile_photo) {
          document.getElementById("my-avatar").src = user.profile_photo;
          userAvatars[myUsername] = user.profile_photo;
        }
      }
    });

  } catch (e) {
    console.error("Error loading users:", e);
  }
}

function addUserToList(user) {
  const div = document.createElement("div");
  div.className = "user-item";
  div.onclick = () => selectUser(user.username);

  let imgUrl = user.profile_photo || "https://via.placeholder.com/40";
  userAvatars[user.username] = imgUrl;

  div.innerHTML = `
    <img src="${imgUrl}" class="avatar">
    <span>${user.username}</span>
  `;

  document.getElementById("user-list").appendChild(div);
}

/***********************
 * SELECT CHAT
 ***********************/
async function selectUser(name) {
  currentReceiver = name;
  document.getElementById("chat-title").innerText = name;
  document.getElementById("messages").innerHTML = "";

  // Mobile navigation
  document.body.classList.add("chat-open");
  document.getElementById("back-btn").style.display = window.innerWidth <= 768 ? "block" : "none";

  // Show call button for private chats
  const chatActions = document.getElementById("chat-actions");
  if (name !== "Family Group") {
    chatActions.style.display = "block";
  } else {
    chatActions.style.display = "none";
  }

  // Highlight active user
  const userItems = document.querySelectorAll(".user-item");
  userItems.forEach(item => {
    if (item.innerText.includes(name)) {
      item.classList.add("active");
    } else {
      item.classList.remove("active");
    }
  });

  loadHistory();
}

async function loadHistory() {
  try {
    const res = await fetch("/messages");
    const data = await res.json();

    // The backend returns messages in descending order (newest first)
    // We want to display them in chronological order
    const filteredMsgs = data.filter(m => {
      if (currentReceiver === "Family Group") {
        return m.receiver === "Family Group";
      } else {
        // Private chat: either (Me -> Him) or (Him -> Me)
        return (m.sender === myUsername && m.receiver === currentReceiver) ||
               (m.sender === currentReceiver && m.receiver === myUsername);
      }
    }).reverse();

    filteredMsgs.forEach(m => {
      displayMessage(
        m.sender,
        m.text || "📎 Shared File: " + (m.fileName || "file"),
        m.sender === myUsername ? "sent" : "received",
        m.fileUrl
      );
    });
  } catch (e) {
    console.error("Error loading history:", e);
  }
}

/***********************
 * WEBSOCKET
 ***********************/
function connectWS() {
  ws = new WebSocket(`ws://${location.host}/ws/${myUsername}`);

  ws.onopen = () => {
    console.log("WebSocket connected");
  };

  ws.onmessage = (event) => {
    let msg;
    try {
      msg = JSON.parse(event.data);
    } catch (e) {
      console.error("Invalid WS JSON:", event.data);
      return;
    }

    // Only display if relevant to current chat
    const isRelevant =
      (currentReceiver === "Family Group" && msg.receiver === "Family Group") ||
      (msg.sender === currentReceiver && msg.receiver === myUsername) ||
      (msg.sender === myUsername && msg.receiver === currentReceiver);

    switch (msg.type) {

      case "text":
        if (isRelevant) {
          displayMessage(
            msg.sender,
            msg.text || msg.message || "",
            msg.sender === myUsername ? "sent" : "received"
          );
        }
        break;

      case "typing":
        if (msg.sender === currentReceiver) {
          showTypingIndicator(msg.sender);
        }
        break;

      case "file":
        if (isRelevant) {
          displayMessage(
            msg.sender,
            msg.text || "📎 Shared File: " + (msg.filename || "received"),
            msg.sender === myUsername ? "sent" : "received",
            msg.url
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
        if (document.getElementById("call-overlay").style.display === "flex" &&
            document.getElementById("accept-call").style.display === "block") {
          // If we are showing the incoming call screen, wait for user to accept
          pendingOffer = msg;
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
        // online / offline
        break;

      default:
        console.warn("Unknown WS message:", msg);
    }
  };

  ws.onerror = (e) => {
    console.error("WebSocket error:", e);
  };

  ws.onclose = () => {
    console.warn("WebSocket closed");
  };
}

/***********************
 * SEND MESSAGE
 ***********************/
function sendMsg() {
  const input = document.getElementById("msg");
  const text = input.value.trim();

  if (!text || !ws) return;

  const payload = {
    type: "text",
    receiver: currentReceiver,
    text: text
  };

  ws.send(JSON.stringify(payload));

  displayMessage(myUsername, text, "sent");
  input.value = "";
}

async function sendFile() {
  const fileInput = document.getElementById("file-input");
  if (fileInput.files.length === 0) return;

  const file = fileInput.files[0];
  const formData = new FormData();
  formData.append("file", file);

  try {
    const res = await fetch("/upload", {
      method: "POST",
      body: formData
    });
    const data = await res.json();

    if (data.url) {
      const payload = {
        type: "file",
        receiver: currentReceiver,
        url: data.url,
        filename: data.filename
      };
      ws.send(JSON.stringify(payload));
      displayMessage(myUsername, "📎 Shared File: " + data.filename, "sent", data.url);
    }
  } catch (e) {
    console.error("File upload error:", e);
    alert("Error uploading file");
  }
}

/***********************
 * DISPLAY MESSAGE
 ***********************/
function displayMessage(sender, text, type, fileUrl = null) {
  const row = document.createElement("div");
  row.className = `msg-row ${type}`;

  const bubble = document.createElement("div");
  bubble.className = "msg-bubble";

  const displayName = sender === myUsername ? "Me" : sender;

  let content = "";
  if (currentReceiver === "Family Group" || sender !== myUsername) {
    content += `<b>${displayName}:</b> `;
  } else {
    content += `<b>Me:</b> `;
  }

  if (fileUrl) {
    if (isImage(fileUrl)) {
      content += `<br><img src="${fileUrl}" style="max-width: 200px; border-radius: 5px; cursor: pointer" onclick="window.open('${fileUrl}')"><br>`;
    } else {
      content += `<a href="${fileUrl}" target="_blank" style="color: inherit;">${text}</a>`;
    }
  } else {
    content += text;
  }

  bubble.innerHTML = content;

  row.appendChild(bubble);
  document.getElementById("messages").appendChild(row);

  document.getElementById("messages").scrollTop =
    document.getElementById("messages").scrollHeight;
}

/***********************
 * HELPERS
 ***********************/
function isImage(url) {
  return /\.(jpg|jpeg|png|webp|gif|svg)$/i.test(url);
}

let typingTimeout;
function showTypingIndicator(sender) {
  if (sender !== currentReceiver) return;
  const title = document.getElementById("chat-title");
  if (!title) return;

  const originalText = currentReceiver;
  title.innerText = sender + " is typing...";

  clearTimeout(typingTimeout);
  typingTimeout = setTimeout(() => {
    if (currentReceiver === originalText) {
      title.innerText = originalText;
    }
  }, 3000);
}

let lastTypingSent = 0;
function handleTyping() {
  if (!ws || !currentReceiver || currentReceiver === "Family Group") return;

  const now = Date.now();
  if (now - lastTypingSent > 2000) { // Throttle typing notifications
    ws.send(JSON.stringify({
      type: "typing",
      receiver: currentReceiver
    }));
    lastTypingSent = now;
  }
}

/***********************
 * ENTER KEY HANDLER
 ***********************/
function handleEnter(e) {
  handleTyping();
  if (e.key === "Enter") {
    sendMsg();
  }
}

window.addEventListener('resize', () => {
  if (window.innerWidth > 768) {
    document.body.classList.remove("chat-open");
    document.getElementById("back-btn").style.display = "none";
  } else if (currentReceiver && document.body.classList.contains("chat-open")) {
    document.getElementById("back-btn").style.display = "block";
  }
});

/***********************
 * WEBRTC CALLING
 ***********************/
let peerConnection = null;
let localStream = null;
let callReceiver = null;
let isCaller = false;
let pendingOffer = null;

const rtcConfig = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' }
  ]
};

async function startCall() {
  if (!currentReceiver || currentReceiver === "Family Group") return;

  callReceiver = currentReceiver;
  isCaller = true;

  showCallOverlay(callReceiver, "Calling...");
  document.getElementById("accept-call").style.display = "none";
  document.getElementById("reject-call").style.display = "none";
  document.getElementById("end-call").style.display = "block";

  const myPhoto = userAvatars[myUsername];

  const payload = {
    type: "call_request",
    receiver: callReceiver,
    sender: myUsername,
    profile_photo: myPhoto || null
  };
  ws.send(JSON.stringify(payload));

  // Android expects offer immediately
  setupWebRTC();
}

function showCallOverlay(username, status) {
  document.getElementById("call-username").innerText = username;
  document.getElementById("call-status").innerText = status;
  document.getElementById("call-overlay").style.display = "flex";

  if (userAvatars[username]) {
    document.getElementById("call-avatar").src = userAvatars[username];
  } else {
    document.getElementById("call-avatar").src = "https://via.placeholder.com/100";
  }
}

function hideCallOverlay() {
  document.getElementById("call-overlay").style.display = "none";
  document.getElementById("ringtone").pause();
  document.getElementById("ringtone").currentTime = 0;
  pendingOffer = null;
}

async function handleCallRequest(msg) {
  callReceiver = msg.sender;
  if (msg.profile_photo) {
    userAvatars[msg.sender] = msg.profile_photo;
  }
  isCaller = false;

  showCallOverlay(callReceiver, "Incoming Call...");
  document.getElementById("accept-call").style.display = "block";
  document.getElementById("reject-call").style.display = "block";
  document.getElementById("end-call").style.display = "none";

  document.getElementById("ringtone").play().catch(e => console.log("Audio play failed:", e));
}

async function acceptCall() {
  document.getElementById("ringtone").pause();
  document.getElementById("call-status").innerText = "Connecting...";
  document.getElementById("accept-call").style.display = "none";
  document.getElementById("reject-call").style.display = "none";
  document.getElementById("end-call").style.display = "block";

  const payload = {
    type: "call_accept",
    receiver: callReceiver
  };
  ws.send(JSON.stringify(payload));

  if (pendingOffer) {
    await handleOffer(pendingOffer);
    pendingOffer = null;
  } else {
    await setupWebRTC();
  }
}

function rejectCall() {
  const payload = {
    type: "call_rejected",
    receiver: callReceiver
  };
  ws.send(JSON.stringify(payload));
  hideCallOverlay();
}

function endCall() {
  const payload = {
    type: "call_ended",
    receiver: callReceiver
  };
  ws.send(JSON.stringify(payload));
  stopWebRTC();
  hideCallOverlay();
}

async function setupWebRTC() {
  if (peerConnection) return;
  try {
    localStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });

    peerConnection = new RTCPeerConnection(rtcConfig);

    localStream.getTracks().forEach(track => {
      peerConnection.addTrack(track, localStream);
    });

    peerConnection.ontrack = (event) => {
      const remoteAudio = document.getElementById("remote-audio") || document.createElement("audio");
      remoteAudio.id = "remote-audio";
      remoteAudio.autoplay = true;
      remoteAudio.srcObject = event.streams[0];
      if (!remoteAudio.parentElement) document.body.appendChild(remoteAudio);
    };

    peerConnection.onicecandidate = (event) => {
      if (event.candidate) {
        ws.send(JSON.stringify({
          type: "ice_candidate",
          receiver: callReceiver,
          candidate: event.candidate
        }));
      }
    };

    if (isCaller) {
      const offer = await peerConnection.createOffer();
      await peerConnection.setLocalDescription(offer);
      ws.send(JSON.stringify({
        type: "webrtc_offer",
        receiver: callReceiver,
        sdp: offer.sdp
      }));
    }
  } catch (e) {
    console.error("WebRTC Setup Error:", e);
    if (location.protocol !== 'https:' && location.hostname !== 'localhost') {
      alert("Microphone access failed. Web calls require HTTPS to work on LAN.");
    } else {
      alert("Could not access microphone. Please check permissions.");
    }
    endCall();
  }
}

async function handleOffer(msg) {
  if (!peerConnection) await setupWebRTC();
  await peerConnection.setRemoteDescription(new RTCSessionDescription({ type: 'offer', sdp: msg.sdp }));
  const answer = await peerConnection.createAnswer();
  await peerConnection.setLocalDescription(answer);
  ws.send(JSON.stringify({
    type: "webrtc_answer",
    receiver: callReceiver,
    sdp: answer.sdp
  }));
}

async function handleAnswer(msg) {
  if (!peerConnection) return;
  try {
    await peerConnection.setRemoteDescription(new RTCSessionDescription({ type: 'answer', sdp: msg.sdp }));
    document.getElementById("call-status").innerText = "Connected";
  } catch (e) {
    console.error("Handle Answer Error:", e);
  }
}

async function handleIceCandidate(msg) {
  if (peerConnection) {
    await peerConnection.addIceCandidate(new RTCIceCandidate(msg.candidate));
  }
}

function stopWebRTC() {
  if (localStream) {
    localStream.getTracks().forEach(track => track.stop());
    localStream = null;
  }
  if (peerConnection) {
    peerConnection.close();
    peerConnection = null;
  }
  const remoteAudio = document.getElementById("remote-audio");
  if (remoteAudio) remoteAudio.remove();

  isCaller = false;
  pendingOffer = null;
}
