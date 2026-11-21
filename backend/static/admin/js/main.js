import { getAdminKey, setAdminKey } from "./api.js";
import { initTheme, toggleTheme, openAuthModal, closeAuthModal, switchTab } from "./ui.js";
import { loadUsers, initUsersTab } from "./tabs/users.js";
import { loadSystemStatus, initSystemTab } from "./tabs/system.js";
import { loadSettings, initSecurityTab } from "./tabs/security.js";
import { loadAIConfig, initLumirTab } from "./tabs/lumir.js";
import { loadLocation, initWeatherTab } from "./tabs/weather.js";
import { loadCapabilities, initCapabilitiesTab } from "./tabs/capabilities.js";
import { initStorageTab } from "./tabs/storage.js";

initTheme();

function handleTabLoad(tabId) {
    if (tabId === "users") loadUsers();
    if (tabId === "system") loadSystemStatus();
    if (tabId === "security") loadSettings();
    if (tabId === "lumir") loadAIConfig();
    if (tabId === "capabilities") loadCapabilities();
    if (tabId === "weather") loadLocation();
}

function submitAdminKey() {
    const val = document.getElementById("input-admin-key").value.trim();
    if (!val) return;
    setAdminKey(val);
    closeAuthModal();
    loadUsers();
    loadSettings();
}

window.addEventListener("DOMContentLoaded", () => {
    // Header controls
    document.getElementById("btn-theme-toggle")?.addEventListener("click", toggleTheme);
    document.getElementById("btn-change-key")?.addEventListener("click", () => {
        openAuthModal(getAdminKey());
    });
    document.getElementById("btn-submit-admin-key")?.addEventListener("click", submitAdminKey);

    // Sidebar navigation
    document.getElementById("admin-sidebar")?.addEventListener("click", (e) => {
        const navItem = e.target.closest(".nav-item[data-tab]");
        if (!navItem) return;
        switchTab(navItem.dataset.tab, navItem, handleTabLoad);
    });

    // Initialize tab handlers
    initUsersTab();
    initSystemTab();
    initSecurityTab();
    initLumirTab();
    initWeatherTab();
    initCapabilitiesTab();
    initStorageTab();

    // Initial boot
    if (!getAdminKey()) {
        openAuthModal();
    } else {
        loadUsers();
        loadSettings();
    }
});
