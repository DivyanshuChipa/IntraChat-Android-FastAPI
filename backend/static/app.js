/***********************
 * LOGIN (index.html)
 ***********************/
let isLoginMode = true;

function toggleAuthMode() {
  isLoginMode = !isLoginMode;
  const btn = document.getElementById("auth-btn");
  const link = document.getElementById("toggle-link");
  const msg = document.getElementById("toggle-msg");
  const title = document.querySelector(".brand-title");

  if (isLoginMode) {
    btn.innerText = "LOGIN";
    msg.innerText = "No account?";
    link.innerText = "Register";
  } else {
    btn.innerText = "REGISTER";
    msg.innerText = "Have an account?";
    link.innerText = "Login";
  }
  document.getElementById("error").innerText = "";
}

async function handleAuth() {
  const username = document.getElementById("username").value.trim();
  const password = document.getElementById("password").value.trim();
  const errorEl = document.getElementById("error");

  if (!username || !password) {
    errorEl.innerText = "Please fill all fields";
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
        window.location = "/chat.html";
      } else {
        alert("Registration successful! Please login.");
        toggleAuthMode();
      }
    } else {
      errorEl.innerText = data.message || (isLoginMode ? "Login failed" : "Registration failed");
    }
  } catch (e) {
    console.error("Auth error:", e);
    errorEl.innerText = "Server connection error";
  }
}

/***********************
 * CHAT (chat.html)
 ***********************/
const myUsername = localStorage.getItem("username");
let ws = null;
let currentReceiver = "Family Group";

if (window.location.pathname.includes("chat.html")) {
  if (!myUsername) {
    window.location = "/index.html";
  } else {
    document.getElementById("profile-name").innerText = "👤 " + myUsername;
    initTheme();
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

  let imgUrl = "https://via.placeholder.com/40";
  if (user.profile_photo) {
    imgUrl = user.profile_photo;
  }

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
  document.getElementById("chat-header").innerText = name;
  document.getElementById("messages").innerHTML = "";

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
  const header = document.getElementById("chat-header");
  const originalText = sender;
  header.innerText = sender + " is typing...";

  clearTimeout(typingTimeout);
  typingTimeout = setTimeout(() => {
    header.innerText = originalText;
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
