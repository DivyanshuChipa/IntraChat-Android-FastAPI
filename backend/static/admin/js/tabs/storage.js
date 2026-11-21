import { fetchAdmin } from "../api.js";
import { showToast } from "../ui.js";

export async function runStorageCleanup() {
    const days = parseInt(document.getElementById("cleanup-days").value, 10) || 30;
    if (!confirm(`Delete all uploaded files older than ${days} days?`)) return;
    const res = await fetchAdmin(`/admin/cleanup?days=${days}`, { method: "POST" });
    if (res && res.success) showToast(res.message);
}

export function initStorageTab() {
    document.getElementById("btn-run-cleanup")?.addEventListener("click", runStorageCleanup);
}
