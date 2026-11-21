import { fetchAdmin } from "../api.js";

export async function loadSystemStatus() {
    const data = await fetchAdmin("/admin/system_status");
    if (data && data.success) {
        document.getElementById("stat-cpu").innerText = data.cpu_percent + " %";
        document.getElementById("bar-cpu").style.width = Math.min(100, data.cpu_percent) + "%";

        document.getElementById("stat-ram").innerText = data.ram_percent + " %";
        document.getElementById("stat-ram-sub").innerText = `RAM: ${data.ram_used_gb} GB / ${data.ram_total_gb} GB`;
        document.getElementById("bar-ram").style.width = Math.min(100, data.ram_percent) + "%";

        document.getElementById("stat-disk").innerText = data.disk_percent + " %";
        document.getElementById("stat-disk-sub").innerText = `Free: ${data.disk_free_gb} GB / ${data.disk_total_gb} GB`;
        document.getElementById("bar-disk").style.width = Math.min(100, data.disk_percent) + "%";

        document.getElementById("stat-clients").innerText = data.active_clients;
    }
}

export function initSystemTab() {
    document.getElementById("btn-refresh-system")?.addEventListener("click", loadSystemStatus);
}
