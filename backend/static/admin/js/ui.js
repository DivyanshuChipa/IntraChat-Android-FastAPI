export function initTheme() {
    const saved = localStorage.getItem("theme") || "dark";
    document.documentElement.setAttribute("data-theme", saved);
    document.body.setAttribute("data-theme", saved);
}

export function toggleTheme() {
    const current = document.documentElement.getAttribute("data-theme") || "dark";
    const next = current === "dark" ? "light" : "dark";
    document.documentElement.setAttribute("data-theme", next);
    document.body.setAttribute("data-theme", next);
    localStorage.setItem("theme", next);
}

export function showToast(msg, isError = false) {
    const toast = document.getElementById("toast");
    const toastMsg = document.getElementById("toast-msg");
    toastMsg.innerText = msg;
    toast.style.borderColor = isError ? "var(--danger)" : "var(--success)";
    toast.classList.add("show");
    setTimeout(() => toast.classList.remove("show"), 3500);
}

export function openAuthModal(prefill = null) {
    if (prefill !== null) {
        document.getElementById("input-admin-key").value = prefill;
    }
    document.getElementById("auth-modal").style.display = "flex";
}

export function closeAuthModal() {
    document.getElementById("auth-modal").style.display = "none";
}

export function switchTab(tabId, navEl, onTabChange) {
    document.querySelectorAll(".nav-item").forEach((i) => i.classList.remove("active"));
    document.querySelectorAll(".tab-panel").forEach((p) => p.classList.remove("active"));
    navEl.classList.add("active");
    document.getElementById(`tab-${tabId}`).classList.add("active");

    if (typeof onTabChange === "function") {
        onTabChange(tabId);
    }
}
