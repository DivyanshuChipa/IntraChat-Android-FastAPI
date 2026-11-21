let isLoginMode = true;

// Auto-redirect if already logged in
const existingUser = localStorage.getItem("username");
if (
  (window.location.pathname === "/" || window.location.pathname.includes("index.html")) &&
  existingUser
) {
  window.location.href = "/chat.html";
}

function toggleAuthMode(e) {
  if (e) e.preventDefault();
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

  if (errorEl) {
    errorEl.innerText = "";
    errorEl.style.color = "var(--error-color)";
  }
}

async function login() {
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
        localStorage.setItem("username", data.username);
        localStorage.setItem("token", data.token);
        window.location.href = "/chat.html";
      } else {
        alert(data.message);
        toggleAuthMode();
      }
    } else if (errorEl) {
      errorEl.innerText = data.message || "Operation failed";
      if (res.status === 403 || (data.message && data.message.toLowerCase().includes("pending"))) {
        errorEl.style.color = "#FF9800";
      } else {
        errorEl.style.color = "var(--error-color)";
      }
    }
  } catch (e) {
    console.error("Auth error:", e);
    if (errorEl) {
      errorEl.innerText = "Server connection error";
      errorEl.style.color = "var(--error-color)";
    }
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.innerText = isLoginMode ? "LOGIN" : "REGISTER";
    }
  }
}

window.addEventListener("DOMContentLoaded", () => {
  document.getElementById("auth-btn")?.addEventListener("click", login);
  document.getElementById("toggle-link")?.addEventListener("click", toggleAuthMode);

  const togglePassword = document.getElementById("togglePassword");
  const password = document.getElementById("password");
  togglePassword?.addEventListener("click", function () {
    const type = password.getAttribute("type") === "password" ? "text" : "password";
    password.setAttribute("type", type);
    this.classList.toggle("fa-eye-slash");
  });

  password?.addEventListener("keydown", (e) => {
    if (e.key === "Enter") login();
  });
});
