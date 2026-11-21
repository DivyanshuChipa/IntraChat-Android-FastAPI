import { fetchAdmin } from "../api.js";
import { showToast } from "../ui.js";

export async function loadSettings() {
    const data = await fetchAdmin("/admin/settings");
    if (data && data.success) {
        document.getElementById("toggle-approval").checked = data.require_approval;
        document.getElementById("toggle-discovery").checked = data.discovery_enabled;
    }
}

export async function toggleRequireApproval(enabled) {
    const res = await fetchAdmin(`/admin/toggle_approval?enabled=${enabled}`, { method: "POST" });
    if (res && res.success) {
        showToast(enabled ? "Approval required for new accounts." : "New accounts approved automatically.");
    }
}

export async function toggleDiscovery(enabled) {
    const res = await fetchAdmin(`/admin/toggle_discovery?enabled=${enabled}`, { method: "POST" });
    if (res && res.success) {
        showToast(enabled ? "LAN Discovery Broadcast Enabled." : "Stealth Mode Activated.");
    }
}

export function initSecurityTab() {
    document.getElementById("toggle-approval")?.addEventListener("change", (e) => {
        toggleRequireApproval(e.target.checked);
    });
    document.getElementById("toggle-discovery")?.addEventListener("change", (e) => {
        toggleDiscovery(e.target.checked);
    });
}
