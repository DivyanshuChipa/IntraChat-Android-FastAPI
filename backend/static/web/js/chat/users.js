import { state } from "./state.js";
import { displayMessage } from "./messages.js";

export async function loadUsers() {
  try {
    const res = await fetch("/users");
    const data = await res.json();

    if (!data.success || !Array.isArray(data.users)) {
      console.error("Invalid users response:", data);
      return;
    }

    state.allUsersList = [];

    state.allUsersList.push({
      username: "Family Group",
      profile_photo: "/assets/family_group.svg"
    });

    const lumirVisible = localStorage.getItem("lumirVisible") !== "false";
    if (lumirVisible) {
      state.allUsersList.push({
        username: "Lumir",
        profile_photo: "/assets/lumir5.svg"
      });
    }

    data.users.forEach((user) => {
      if (user.username !== state.myUsername) {
        state.allUsersList.push(user);
      } else if (user.profile_photo) {
        document.getElementById("my-avatar").src = user.profile_photo;
        const myThumb = document.getElementById("my-profile-thumb");
        if (myThumb) myThumb.src = user.profile_photo;
        state.userAvatars[state.myUsername] = user.profile_photo;
      }
    });

    renderUserList();

    try {
      const msgRes = await fetch("/messages");
      const msgData = await msgRes.json();

      msgData.forEach((m) => {
        let otherUser = null;
        if (m.receiver === "Family Group") {
          otherUser = "Family Group";
        } else if (m.sender === state.myUsername) {
          otherUser = m.receiver;
        } else if (m.receiver === state.myUsername) {
          otherUser = m.sender;
        }

        if (otherUser) {
          if (!state.latestMessageTimes[otherUser] || m.timestamp > state.latestMessageTimes[otherUser]) {
            state.latestMessageTimes[otherUser] = m.timestamp;
          }
        }
      });
      renderUserList();
    } catch (err) {
      console.error("Failed to load history for sorting:", err);
    }
  } catch (e) {
    console.error("Error loading users:", e);
  }
}

export function filterUsers() {
  const searchInput = document.getElementById("user-search").value.toLowerCase();
  const userItems = document.querySelectorAll(".user-item");

  userItems.forEach((item) => {
    const nameSpan = item.querySelector(".user-info-wrapper span");
    if (nameSpan) {
      const name = nameSpan.innerText.toLowerCase();
      item.style.display = name.includes(searchInput) ? "flex" : "none";
    }
  });
}

export function renderUserList() {
  const list = document.getElementById("user-list");
  list.innerHTML = "";

  const pinnedUsers = state.allUsersList.filter((u) => u.username === "Family Group" || u.username === "Lumir");
  const normalUsers = state.allUsersList.filter((u) => u.username !== "Family Group" && u.username !== "Lumir");

  pinnedUsers.sort((a, b) => {
    if (a.username === "Family Group") return -1;
    if (b.username === "Family Group") return 1;
    return 0;
  });

  normalUsers.sort((a, b) => {
    const timeA = state.latestMessageTimes[a.username] ? new Date(state.latestMessageTimes[a.username]).getTime() : 0;
    const timeB = state.latestMessageTimes[b.username] ? new Date(state.latestMessageTimes[b.username]).getTime() : 0;
    return timeB - timeA;
  });

  const sortedUsers = [...pinnedUsers, ...normalUsers];
  sortedUsers.forEach((user) => addUserToList(user));
  filterUsers();
}

function addUserToList(user) {
  const div = document.createElement("div");
  div.className = "user-item";
  div.addEventListener("click", () => selectUser(user.username));
  div.setAttribute("data-username", user.username);

  let imgUrl = user.profile_photo || "https://via.placeholder.com/40";
  if (user.username === "Lumir") {
    imgUrl = "/assets/lumir5.svg";
  } else if (user.username === "Family Group") {
    imgUrl = "/assets/family_group.svg";
  }

  state.userAvatars[user.username] = imgUrl;

  const count = state.unreadCounts[user.username] || 0;
  const badgeClass = count > 0 ? "badge active" : "badge";

  if (state.currentReceiver === user.username) {
    div.classList.add("active");
  }

  div.innerHTML = `
    <div class="user-info-wrapper">
        <img src="${imgUrl}" class="avatar">
        <span>${user.username}</span>
    </div>
    <span class="${badgeClass}" id="badge-${user.username}">${count > 0 ? count : ""}</span>
  `;

  document.getElementById("user-list").appendChild(div);
}

export async function selectUser(name) {
  state.currentReceiver = name;
  document.getElementById("chat-title").innerText = name;
  document.getElementById("messages").innerHTML = "";
  state.lastRenderedDateStr = null;

  const headerAvatar = document.getElementById("header-avatar");
  const headerSubtitle = document.getElementById("header-subtitle");

  if (name === "Lumir") {
    if (headerAvatar) headerAvatar.src = "/assets/lumir5.svg";
    if (headerSubtitle) headerSubtitle.innerText = "AI Neural Companion";
  } else if (name === "Family Group") {
    if (headerAvatar) headerAvatar.src = "/assets/family_group.svg";
    if (headerSubtitle) headerSubtitle.innerText = "Public LAN Broadcast";
  } else {
    if (headerAvatar) headerAvatar.src = state.userAvatars[name] || "/assets/lumir5.svg";
    if (headerSubtitle) headerSubtitle.innerText = "Intra Private Chat";
  }

  document.body.classList.add("chat-open");
  document.getElementById("back-btn").style.display = window.innerWidth <= 768 ? "inline-flex" : "none";

  const chatActions = document.getElementById("chat-actions");
  chatActions.style.display = name !== "Family Group" ? "block" : "none";

  if (state.unreadCounts[name]) {
    state.unreadCounts[name] = 0;
    renderUserList();
  }

  document.querySelectorAll(".user-item").forEach((item) => {
    item.classList.toggle("active", item.getAttribute("data-username") === name);
  });

  loadHistory();
}

export async function loadHistory() {
  try {
    const res = await fetch("/messages");
    const data = await res.json();

    const filteredMsgs = data
      .filter((m) => {
        if (state.currentReceiver === "Family Group") {
          return m.receiver === "Family Group";
        }
        return (
          (m.sender === state.myUsername && m.receiver === state.currentReceiver) ||
          (m.sender === state.currentReceiver && m.receiver === state.myUsername)
        );
      })
      .reverse();

    state.lastRenderedDateStr = null;
    filteredMsgs.forEach((m) => {
      const ts = m.timestamp || Date.now();
      const fileUrl = m.fileUrl || m.url || m.media_url || m.file_url || null;
      const fileName = m.fileName || m.filename || m.file_name || "file";
      const textContent = m.text || m.content || (fileUrl ? "📎 Shared File: " + fileName : "");
      displayMessage(
        m.sender,
        textContent,
        m.sender === state.myUsername ? "sent" : "received",
        fileUrl,
        null,
        false,
        null,
        "",
        m.options,
        ts
      );
    });
  } catch (e) {
    console.error("Error loading history:", e);
  }
}
