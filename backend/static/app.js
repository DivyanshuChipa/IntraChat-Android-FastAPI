/***********************
 * LOGIN (index.html)
 ***********************/
async function login() {
  const username = document.getElementById("username").value;
  const password = document.getElementById("password").value;

  try {
    const res = await fetch("/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password })
    });

    const data = await res.json();

    if (data.success) {
      localStorage.setItem("username", data.username);
      window.location = "/chat.html";
    } else {
      document.getElementById("error").innerText = "Login failed";
    }
  } catch (e) {
    console.error("Login error:", e);
    document.getElementById("error").innerText = "Server error";
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
    document.getElementById("my-profile").innerText = "👤 " + myUsername;
    loadUsers();
    connectWS();
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
function selectUser(name) {
  currentReceiver = name;
  document.getElementById("chat-header").innerText =
    "Chat with: " + name;
  document.getElementById("messages").innerHTML = "";
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

    switch (msg.type) {

      case "text":
        displayMessage(
          msg.sender,
          msg.text || msg.message || "",
          msg.sender === myUsername ? "sent" : "received"
        );
        break;

      case "typing":
        // ignore for now (future typing UI)
        break;

      case "file":
        displayMessage(
          msg.sender,
          "📎 File: " + (msg.file_name || "received"),
          "received"
        );
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

  displayMessage("Me", text, "sent");
  input.value = "";
}

/***********************
 * DISPLAY MESSAGE
 ***********************/
function displayMessage(sender, text, type) {
  const row = document.createElement("div");
  row.className = `msg-row ${type}`;

  const bubble = document.createElement("div");
  bubble.className = "msg-bubble";
  bubble.innerHTML = `<b>${sender}:</b> ${text}`;

  row.appendChild(bubble);
  document.getElementById("messages").appendChild(row);

  document.getElementById("messages").scrollTop =
    document.getElementById("messages").scrollHeight;
}

/***********************
 * ENTER KEY HANDLER
 ***********************/
function handleEnter(e) {
  if (e.key === "Enter") {
    sendMsg();
  }
}

