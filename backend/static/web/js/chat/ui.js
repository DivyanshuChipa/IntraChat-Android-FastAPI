import { state } from "./state.js";

export function initTheme() {
  const theme = localStorage.getItem("theme") || "light";
  document.body.setAttribute("data-theme", theme);
}

export function toggleTheme() {
  const current = document.body.getAttribute("data-theme");
  const next = current === "dark" ? "light" : "dark";
  document.body.setAttribute("data-theme", next);
  localStorage.setItem("theme", next);
}

export function initSidebar() {
  if (localStorage.getItem("sidebarMinimized") === "true") {
    document.getElementById("sidebar")?.classList.add("minimized");
  }
}

export function toggleSidebar() {
  const sidebar = document.getElementById("sidebar");
  if (!sidebar) return;
  sidebar.classList.toggle("minimized");
  localStorage.setItem("sidebarMinimized", sidebar.classList.contains("minimized"));
}

export function showSidebar() {
  document.body.classList.remove("chat-open");
}

export function initSettings() {
  const lumirVisible = localStorage.getItem("lumirVisible") !== "false";
  const lumirToggle = document.getElementById("lumir-toggle");
  if (lumirToggle) lumirToggle.checked = lumirVisible;
}

export function toggleSettings() {
  const overlay = document.getElementById("settings-overlay");
  if (!overlay) return;
  const isHidden = overlay.style.display === "none" || !overlay.style.display;
  overlay.style.display = isHidden ? "flex" : "none";
  if (isHidden) {
    const uDisplay = document.getElementById("settings-username-display");
    if (uDisplay && state.myUsername) uDisplay.innerText = state.myUsername;
  }
}

export function logout() {
  localStorage.removeItem("username");
  localStorage.removeItem("token");
  if (state.ws) state.ws.close();
  window.location = "/index.html";
}

export async function uploadPhoto(onSuccess) {
  const fileInput = document.getElementById("photo-input");
  if (!fileInput || fileInput.files.length === 0) return;

  const token = localStorage.getItem("token");
  const formData = new FormData();
  formData.append("username", state.myUsername);
  formData.append("file", fileInput.files[0]);

  try {
    const res = await fetch("/profile/upload_profile", {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: formData
    });
    const data = await res.json();
    if (data.success) {
      const bustUrl = data.profile_photo + "?t=" + Date.now();
      document.getElementById("my-avatar").src = bustUrl;
      const myThumb = document.getElementById("my-profile-thumb");
      if (myThumb) myThumb.src = bustUrl;
      alert("Profile photo updated!");
      if (typeof onSuccess === "function") onSuccess();
    } else {
      alert("Upload failed");
    }
  } catch (e) {
    console.error("Upload error:", e);
    alert("Error uploading photo");
  }
}

export async function deleteAccount() {
  const password = prompt("Enter your password to confirm account deletion:");
  if (!password) return;
  if (!confirm("Are you sure you want to permanently delete your account?")) return;

  const token = localStorage.getItem("token");
  try {
    const res = await fetch("/delete_account", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({ username: state.myUsername, password })
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
