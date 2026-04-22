/***********************
 * LOGIN (index.html)
 ***********************/
let isLoginMode = true;

function toggleAuthMode() {
  isLoginMode = !isLoginMode;
  const btn = document.getElementById("auth-btn");
  const link = document.getElementById("toggle-link");
  const msg = document.getElementById("toggle-msg");
  const errorEl = document.getElementById("error");

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

  // Clear error when switching modes
  if (errorEl) {
      errorEl.innerText = "";
      errorEl.style.color = "var(--error-color)"; // Reset color
  }
}

async function login() {
  console.log("Auth attempt...");
  const userEl = document.getElementById("username");
  const passEl = document.getElementById("password");
  const errorEl = document.getElementById("error");
  const btn = document.getElementById("auth-btn");

  if (!userEl || !passEl) return;

  const username = userEl.value.trim();
  const password = passEl.value.trim();

  if (!username || !password) {
    if (errorEl) {
        errorEl.innerText = "Please fill all fields";
        errorEl.style.color = "var(--error-color)";
    }
    return;
  }

  // Disable button to prevent double clicks
  if (btn) {
    btn.disabled = true;
    btn.innerText = "Wait...";
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
        // ✅ LOGIN SUCCESS
        localStorage.setItem("username", data.username);
        localStorage.setItem("token", data.token);
        window.location.href = "/chat.html";
      } else {
        // ✅ REGISTER SUCCESS (Might be Pending or Active)
        alert(data.message); // Server ka message dikhao (Wait for approval etc.)
        toggleAuthMode();
      }
    } else {
      // ❌ FAILURE / PENDING STATUS
      if (errorEl) {
          errorEl.innerText = data.message || "Operation failed";

          // Agar Pending hai toh Orange color, warna Red
          if (res.status === 403 || (data.message && data.message.toLowerCase().includes("pending"))) {
              errorEl.style.color = "#FF9800"; // Orange
          } else {
              errorEl.style.color = "var(--error-color)"; // Red
          }
      }
    }
  } catch (e) {
    console.error("Auth error:", e);
    if (errorEl) {
        errorEl.innerText = "Server connection error";
        errorEl.style.color = "var(--error-color)";
    }
  } finally {
      // Re-enable button
      if (btn) {
        btn.disabled = false;
        btn.innerText = isLoginMode ? "LOGIN" : "REGISTER";
      }
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
let unreadCounts = {}; // username -> count
let allUsersList = []; // Array to keep track of loaded users for search/sort
let latestMessageTimes = {}; // username -> timestamp

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
    initSettings();
    loadUsers();
    connectWS();
  }
}

function initSettings() {
    const lumirVisible = localStorage.getItem("lumirVisible") !== "false"; // Default true
    const lumirToggle = document.getElementById("lumir-toggle");
    if (lumirToggle) {
        lumirToggle.checked = lumirVisible;
    }
}

function toggleLumirVisibility() {
    const lumirToggle = document.getElementById("lumir-toggle");
    localStorage.setItem("lumirVisible", lumirToggle.checked);
    loadUsers(); // Refresh list
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
  if (ws) ws.close();
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

    allUsersList = [];

    // Family Group
    allUsersList.push({
      username: "Family Group",
      profile_photo: "/assets/family_group.svg"
    });

    // Lumir AI (if enabled)
    const lumirVisible = localStorage.getItem("lumirVisible") !== "false";
    if (lumirVisible) {
        allUsersList.push({
            username: "Lumir",
            profile_photo: "/assets/lumir5.svg"
        });
    }

    data.users.forEach(user => {
      if (user.username !== myUsername) {
        allUsersList.push(user);
      } else {
        // Current user
        if (user.profile_photo) {
          document.getElementById("my-avatar").src = user.profile_photo;
          userAvatars[myUsername] = user.profile_photo;
        }
      }
    });

    // Pre-calculate latest message times from history if available or wait for history load
    // For now, render the list
    renderUserList();

    // Attempt to load history to determine sorting right after loading users
    // This will hit /messages and update latestMessageTimes, then re-render
    try {
        const msgRes = await fetch("/messages");
        const msgData = await msgRes.json();

        msgData.forEach(m => {
            // Figure out who the conversation is with
            let otherUser = null;
            if (m.receiver === "Family Group") {
                otherUser = "Family Group";
            } else if (m.sender === myUsername) {
                otherUser = m.receiver;
            } else if (m.receiver === myUsername) {
                otherUser = m.sender;
            }

            if (otherUser) {
                if (!latestMessageTimes[otherUser] || m.timestamp > latestMessageTimes[otherUser]) {
                    latestMessageTimes[otherUser] = m.timestamp;
                }
            }
        });
        renderUserList(); // Re-render sorted
    } catch (err) {
        console.error("Failed to load history for sorting:", err);
    }

  } catch (e) {
    console.error("Error loading users:", e);
  }
}

function filterUsers() {
  const searchInput = document.getElementById("user-search").value.toLowerCase();
  const userItems = document.querySelectorAll(".user-item");

  userItems.forEach(item => {
    // Only search through dynamic users, keep static groups pinned unless they match
    // Or we can just filter everything. Let's filter everything based on name.
    const nameSpan = item.querySelector(".user-info-wrapper span");
    if (nameSpan) {
        const name = nameSpan.innerText.toLowerCase();
        if (name.includes(searchInput)) {
            item.style.display = "flex";
        } else {
            item.style.display = "none";
        }
    }
  });
}

function renderUserList() {
    const list = document.getElementById("user-list");
    list.innerHTML = "";

    // Determine sorting:
    // Pinned at top: Family Group, then Lumir
    // Then rest of users sorted by latest message time

    let pinnedUsers = allUsersList.filter(u => u.username === "Family Group" || u.username === "Lumir");
    let normalUsers = allUsersList.filter(u => u.username !== "Family Group" && u.username !== "Lumir");

    // Sort pinned: Family Group first
    pinnedUsers.sort((a, b) => {
        if (a.username === "Family Group") return -1;
        if (b.username === "Family Group") return 1;
        return 0;
    });

    // Sort normal users by latest message time descending
    normalUsers.sort((a, b) => {
        const timeA = latestMessageTimes[a.username] ? new Date(latestMessageTimes[a.username]).getTime() : 0;
        const timeB = latestMessageTimes[b.username] ? new Date(latestMessageTimes[b.username]).getTime() : 0;
        return timeB - timeA;
    });

    const sortedUsers = [...pinnedUsers, ...normalUsers];

    sortedUsers.forEach(user => {
        addUserToList(user);
    });

    filterUsers(); // Re-apply search filter if there's any text
}

function addUserToList(user) {
  const div = document.createElement("div");
  div.className = "user-item";
  div.onclick = () => selectUser(user.username);
  div.setAttribute("data-username", user.username);

  let imgUrl = user.profile_photo || "https://via.placeholder.com/40";

  // 🔥 AVATAR OVERRIDES
  if (user.username === "Lumir") {
      imgUrl = "/assets/lumir5.svg";
  } else if (user.username === "Family Group") {
      imgUrl = "/assets/family_group.svg";
  }

  userAvatars[user.username] = imgUrl;

  let count = unreadCounts[user.username] || 0;
  let badgeClass = count > 0 ? "badge active" : "badge";

  if (currentReceiver === user.username) {
      div.classList.add("active");
  }

  div.innerHTML = `
    <div class="user-info-wrapper">
        <img src="${imgUrl}" class="avatar">
        <span>${user.username}</span>
    </div>
    <span class="${badgeClass}" id="badge-${user.username}">${count > 0 ? count : ''}</span>
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

  // Clear unread count when opening a chat
  if (unreadCounts[name]) {
      unreadCounts[name] = 0;
      renderUserList(); // Re-render to update badges
  }

  // Highlight active user
  const userItems = document.querySelectorAll(".user-item");
  userItems.forEach(item => {
    if (item.getAttribute("data-username") === name) {
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
        m.fileUrl,
        null, false, null, "", m.options
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

    // 🔥 SECURITY CHECK HANDLER 🔥
    if (msg.type === "error") {
        alert("Session Error: " + msg.text);
        logout(); // Kick user out if server rejects connection
        return;
    }

    // Determine the logical conversation partner
    let chatPartner = null;
    if (msg.receiver === "Family Group") {
        chatPartner = "Family Group";
    } else if (msg.sender === myUsername) {
        chatPartner = msg.receiver;
    } else if (msg.receiver === myUsername) {
        chatPartner = msg.sender;
    }

    // Only display if relevant to current chat
    const isRelevant = (chatPartner === currentReceiver);

    // Update latest message time for sorting
    if (chatPartner && ["text", "file", "utility_options"].includes(msg.type)) {
        latestMessageTimes[chatPartner] = msg.timestamp || Date.now();
        // If message is for another chat, increase unread count
        if (!isRelevant && msg.sender !== myUsername) {
            unreadCounts[chatPartner] = (unreadCounts[chatPartner] || 0) + 1;
        }
        renderUserList(); // Re-render to sort and show badges
    }

    switch (msg.type) {

      case "text":
      case "utility_options":
        if (isRelevant) {
          displayMessage(
            msg.sender,
            msg.text || msg.message || "",
            msg.sender === myUsername ? "sent" : "received",
            null, null, false, null, "", msg.options
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
            msg.url,
            null, false, null, "", msg.options
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
  const isVideoFile = file.type.startsWith("video/");
  const formData = new FormData();
  formData.append("file", file);

  // 🔥 OPTIMISTIC UI: Show "uploading" state immediately
  const tempId = 'msg-' + Date.now();
  const localUrl = URL.createObjectURL(file);
  const localThumbnail = isVideoFile ? await createVideoThumbnail(localUrl) : null;

  // Show message with loader
  displayMessage(myUsername, "Uploading...", "sent", localUrl, tempId, true, localThumbnail, file.type);

  try {
    const res = await fetch("/upload", {
      method: "POST",
      body: formData
    });
    const data = await res.json();

    const msgElement = document.getElementById(tempId);

    if (data.url) {
      // ✅ UPLOAD SUCCESS
      if (msgElement) {
        // Remove loader styles
        const bubble = msgElement.querySelector('.msg-bubble');
        if (bubble) bubble.classList.remove('loading');

        const loader = msgElement.querySelector('.loader-overlay');
        if (loader) loader.remove();

        // Update image click to open real URL
        const img = msgElement.querySelector('img');
        if (img) {
            img.onclick = () => window.open(data.url);
        }

        // Update video source for fallback previews that rendered <video src="blob:...">
        const video = msgElement.querySelector('video');
        if (video) {
            video.src = data.url;
            video.load();
        }

        // Update file link if it's not an image
        const link = msgElement.querySelector('a');
        if (link) {
            link.href = data.url;
            link.innerText = "📎 Shared File: " + data.filename;
        }
      }

      // Send WS message
      const payload = {
        type: "file",
        receiver: currentReceiver,
        url: data.url,
        filename: data.filename
      };
      ws.send(JSON.stringify(payload));

    } else {
       throw new Error("Upload failed");
    }
  } catch (e) {
    console.error("File upload error:", e);
    const msgElement = document.getElementById(tempId);
    if (msgElement) {
        const bubble = msgElement.querySelector('.msg-bubble');
        if (bubble) {
             bubble.classList.remove('loading');
             bubble.innerHTML += `<br><span style="color:red; font-size: 12px;">❌ Upload Failed</span>`;
        }
        const loader = msgElement.querySelector('.loader-overlay');
        if (loader) loader.remove();
    }
  } finally {
    URL.revokeObjectURL(localUrl);
    fileInput.value = "";
  }
}

/***********************
 * DISPLAY MESSAGE
 ***********************/
function displayMessage(sender, text, type, fileUrl = null, msgId = null, isLoading = false, videoThumbnail = null, mimeType = "", options = null) {
  const row = document.createElement("div");
  row.className = `msg-row ${type}`;
  if (msgId) row.id = msgId;

  const bubble = document.createElement("div");
  bubble.className = "msg-bubble";
  if (isLoading) bubble.classList.add('loading');

  const displayName = sender === myUsername ? "Me" : sender;

  let content = "";
  if (currentReceiver === "Family Group" && sender !== myUsername) {
    // Generate a color based on sender string
    const colors = ['#E53935', '#D81B60', '#8E24AA', '#3949AB', '#039BE5', '#00897B', '#43A047', '#F4511E'];
    let hash = 0;
    for (let i = 0; i < sender.length; i++) {
        hash = sender.charCodeAt(i) + ((hash << 5) - hash);
    }
    const colorIndex = Math.abs(hash) % colors.length;
    const senderColor = colors[colorIndex];
    content += `<div style="color: ${senderColor}; font-weight: bold; font-size: 0.8em; margin-bottom: 4px;">${displayName}</div>`;
  } else if (sender !== myUsername) {
    // Private chat from others
    content += `<div style="font-weight: bold; font-size: 0.8em; margin-bottom: 4px;">${displayName}</div>`;
  }

  if (fileUrl) {
    if (isImage(fileUrl, mimeType)) {
      content += `<br>
        <div class="media-preview-container">
            <img src="${fileUrl}" class="media-preview-image" onclick="window.open('${fileUrl}')">
            ${isLoading ? '<div class="loader-overlay"></div>' : ''}
        </div>
        <br>`;
    } else if (isVideo(fileUrl, mimeType)) {
      if (videoThumbnail) {
        // Local upload with generated thumbnail
        content += `<br>
          <div class="media-preview-container">
            <div class="video-thumbnail-wrapper">
              <img src="${videoThumbnail}" class="media-preview-image video-thumbnail" onclick="window.open('${fileUrl}')">
              <span class="video-play-icon">▶</span>
            </div>
            ${isLoading ? '<div class="loader-overlay"></div>' : ''}
          </div>
          <br>`;
      } else {
        // Received video: Use native <video> tag for preview
        content += `<br>
          <div class="media-preview-container">
            <video src="${fileUrl}" class="media-preview-image video-thumbnail video-thumbnail-player" controls preload="metadata"></video>
            ${isLoading ? '<div class="loader-overlay"></div>' : ''}
          </div>
          <br>`;
      }
    } else {
      content += `<div style="position: relative;">
          <a href="${fileUrl}" target="_blank" style="color: inherit;">${text}</a>
          ${isLoading ? '<div class="loader-overlay" style="width: 20px; height: 20px; border-width: 2px;"></div>' : ''}
      </div>`;
    }
  } else {
    // 🔥 LINKIFY TEXT
    content += linkify(text);
  }

  bubble.innerHTML = content;

  // 🔥 RENDER OPTIONS
  if (options && Array.isArray(options) && options.length > 0) {
      const optionsContainer = document.createElement("div");
      optionsContainer.className = "msg-options";

      options.forEach(opt => {
          const btn = document.createElement("button");
          btn.className = "option-btn";
          btn.innerText = opt;
          btn.onclick = () => handleOptionClick(opt);
          optionsContainer.appendChild(btn);
      });

      bubble.appendChild(optionsContainer);
  }

  row.appendChild(bubble);
  document.getElementById("messages").appendChild(row);

  document.getElementById("messages").scrollTop =
    document.getElementById("messages").scrollHeight;
}

function handleOptionClick(option) {
    if (!ws) return;

    let command = option; // Default: send the button text

    // Mapping of interactive options to internal commands
    const commandMap = {
        "🛂 Passport A6 (6 Photos)": "###passport###",
        "🛂 Passport A6 (9 Photos)": "###passport9###",
        "📄 Extract Text (OCR)": "###ocr###",
        "🗜️ Compress Image": "PROMPT_SIZE",
        "📄 Convert to PDF": "###topdf###",
        "🧠 Analyze Image (AI)": "###analyzeimage###",
        "📄 Extract PDF Text": "###pdf2text###",
        "🔗 Merge PDFs": "###mergepdfs###",
        "🗜️ Compress PDF": "###compresspdf###",
        "🎵 Extract Audio (MP3)": "###extractaudio###",
        "🗜️ Compress Video": "###compressvideo:28:mp4###",
        "🔄 Rotate Video": "###rotatevideo:90###",
        "🎞️ Convert to MP4": "###convertmp4###"
    };

    if (commandMap[option]) {
        command = commandMap[option];
    }

    if (command === "PROMPT_SIZE" || option === "Compress Image") {
        const size = prompt("Enter target size in KB (e.g. 500):");
        if (!size) return;
        command = `###compress<${size}>###`;
    } else if (
        option === "🛂 Master Passport" ||
        option === "Passport + Date" ||
        option === "📅 Passport + Date" ||
        option === "📅 Passport + Date/Name"
    ) {
        const pageInput = prompt("Page Size? (A6/B4)", "A6");
        if (!pageInput) return;
        const page = pageInput.trim().toUpperCase() === "B4" ? "b4" : "a6";

        const defaultLayout = page === "b4" ? "3x2" : "3x3";
        const layoutHelp = page === "b4"
            ? "Layout? (3x1 / 3x2 / 3x3 / 4x3)"
            : "Layout? (3x1 / 3x2 / 3x3)";
        const layoutInput = prompt(layoutHelp, defaultLayout);
        if (!layoutInput) return;
        const layout = layoutInput.trim().toLowerCase();

        const rawName = prompt("Name (optional):", "") || "";
        const safeName = rawName.trim().replace(/[<>]/g, "");

        // Date picker (optional)
        const dateInput = document.createElement("input");
        dateInput.type = "date";
        dateInput.style.display = "none";
        document.body.appendChild(dateInput);

        const submitPassportCommand = (formattedDate = "") => {
            let finalCmd = `###passport### ###passportpage<${page}>### ###passportlayout<${layout}>###`;
            if (formattedDate) {
                finalCmd += ` ###passportdate<${formattedDate}>###`;
            }
            if (safeName) {
                finalCmd += ` ###passportname<${safeName}>###`;
            }
            sendText(finalCmd);
            dateInput.remove();
        };

        dateInput.onchange = () => {
            const val = dateInput.value; // yyyy-mm-dd
            if (!val) {
                submitPassportCommand("");
                return;
            }
            const [y, m, d] = val.split("-");
            submitPassportCommand(`${d}/${m}/${y}`);
        };

        const addDate = confirm("Add Date bhi chahiye?");
        if (addDate) {
            if (dateInput.showPicker) {
                dateInput.showPicker();
            } else {
                dateInput.click();
            }
        } else {
            submitPassportCommand("");
        }
        return;
    }

    sendText(command);
}

function sendText(text) {
    if (!text || !ws) return;
    const payload = {
        type: "text",
        receiver: currentReceiver,
        text: text
    };
    ws.send(JSON.stringify(payload));
    displayMessage(myUsername, text, "sent");
}

/***********************
 * HELPERS
 ***********************/
function linkify(text) {
    // Regex for URLs starting with http:// or https://
    const urlRegex = /(https?:\/\/[^\s]+)/g;
    return text.replace(urlRegex, function(url) {
        return `<a href="${url}" target="_blank" style="color: inherit; text-decoration: underline;">${url}</a>`;
    });
}

function isImage(url, mimeType = "") {
  if (mimeType.startsWith("image/")) return true;
  if (mimeType.startsWith("video/")) return false;
  return /\.(jpg|jpeg|png|webp|gif|svg)$/i.test(url);
}

function isVideo(url, mimeType = "") {
  if (mimeType.startsWith("video/")) return true;
  return /\.(mp4|mov|webm|mkv|avi|m4v)$/i.test(url);
}

function createVideoThumbnail(videoUrl) {
  return new Promise((resolve) => {
    const video = document.createElement("video");
    video.src = videoUrl;
    video.muted = true;
    video.playsInline = true;
    video.preload = "metadata";

    const fallback = () => resolve(null); // Return null to fallback to native video player

    video.onloadeddata = () => {
      try {
        const canvas = document.createElement("canvas");
        canvas.width = 320;
        canvas.height = (video.videoHeight / video.videoWidth) * 320 || 180;
        const ctx = canvas.getContext("2d");
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL("image/jpeg", 0.75));
      } catch (e) {
        console.error("Thumbnail generation failed:", e);
        fallback();
      }
    };

    video.onerror = fallback;
  });
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
  const now = Date.now();
  if (now - lastTypingSent > 2000 && ws && currentReceiver !== "Family Group") {
      ws.send(JSON.stringify({ type: "typing", receiver: currentReceiver }));
      lastTypingSent = now;
  }
  if (e.key === "Enter") sendMsg();
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
