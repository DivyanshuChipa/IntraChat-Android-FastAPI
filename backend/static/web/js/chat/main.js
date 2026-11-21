import { state } from "./state.js";
import {
  initTheme,
  toggleTheme,
  initSidebar,
  toggleSidebar,
  showSidebar,
  initSettings,
  toggleSettings,
  logout,
  uploadPhoto,
  deleteAccount
} from "./ui.js";
import { loadUsers, filterUsers } from "./users.js";
import { sendMsg, sendFile, handleEnter } from "./messages.js";
import { connectWS } from "./ws.js";
import { startCall, acceptCall, rejectCall, endCall } from "./webrtc.js";

if (!state.myUsername) {
  window.location.href = "/index.html";
} else {
  fetch(`/api/verify_session?username=${encodeURIComponent(state.myUsername)}`)
    .then((res) => res.json())
    .then((data) => {
      if (!data.valid) {
        console.warn("Session invalid on server, kicking to login...");
        alert("Account not recognized on this server. Please login or register.");
        logout();
      }
    })
    .catch((err) => console.log("Session verify network check:", err));

  const profileEl = document.getElementById("profile-name");
  if (profileEl) profileEl.innerText = state.myUsername;

  initTheme();
  initSidebar();
  initSettings();
  loadUsers();
  connectWS();
}

window.addEventListener("DOMContentLoaded", () => {
  // Sidebar & Header Actions
  document.getElementById("btn-open-drop")?.addEventListener("click", () => window.open("/drop", "_blank"));
  document.getElementById("sidebar-toggle")?.addEventListener("click", toggleSidebar);
  document.getElementById("theme-toggle")?.addEventListener("click", toggleTheme);
  document.getElementById("btn-open-settings")?.addEventListener("click", toggleSettings);
  document.getElementById("user-search")?.addEventListener("input", filterUsers);

  // Settings Modal
  document.getElementById("btn-close-settings")?.addEventListener("click", toggleSettings);
  document.getElementById("settings-overlay")?.addEventListener("click", (e) => {
    if (e.target.id === "settings-overlay") toggleSettings();
  });
  document.getElementById("btn-avatar-edit")?.addEventListener("click", () => {
    document.getElementById("photo-input")?.click();
  });
  document.getElementById("btn-settings-photo")?.addEventListener("click", () => {
    document.getElementById("photo-input")?.click();
  });
  document.getElementById("photo-input")?.addEventListener("change", () => uploadPhoto(loadUsers));
  document.getElementById("lumir-toggle")?.addEventListener("change", (e) => {
    localStorage.setItem("lumirVisible", e.target.checked);
    loadUsers();
  });
  document.getElementById("btn-settings-drop")?.addEventListener("click", () => window.open("/drop", "_blank"));
  document.getElementById("btn-logout")?.addEventListener("click", logout);
  document.getElementById("btn-delete-account")?.addEventListener("click", deleteAccount);

  // Chat Area & Input
  document.getElementById("back-btn")?.addEventListener("click", showSidebar);
  document.getElementById("btn-call")?.addEventListener("click", startCall);
  document.getElementById("btn-attach")?.addEventListener("click", () => {
    document.getElementById("file-input")?.click();
  });
  document.getElementById("file-input")?.addEventListener("change", sendFile);
  document.getElementById("msg")?.addEventListener("keypress", handleEnter);
  document.getElementById("btn-send")?.addEventListener("click", sendMsg);

  // Call Overlay
  document.getElementById("accept-call")?.addEventListener("click", acceptCall);
  document.getElementById("reject-call")?.addEventListener("click", rejectCall);
  document.getElementById("end-call")?.addEventListener("click", endCall);
});

window.addEventListener("keydown", (e) => {
  if (e.key === "Escape") {
    const overlay = document.getElementById("settings-overlay");
    if (overlay && overlay.style.display !== "none") {
      overlay.style.display = "none";
    }
  }
});

window.addEventListener("resize", () => {
  if (window.innerWidth > 768) {
    document.body.classList.remove("chat-open");
    document.getElementById("back-btn").style.display = "none";
  } else if (state.currentReceiver && document.body.classList.contains("chat-open")) {
    document.getElementById("back-btn").style.display = "block";
  }
});
