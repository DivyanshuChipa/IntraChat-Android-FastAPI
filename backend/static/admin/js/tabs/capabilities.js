import { fetchAdmin } from "../api.js";
import { showToast } from "../ui.js";

let capPollTimer = null;

export async function loadCapabilities() {
    const data = await fetchAdmin("/admin/capabilities");
    if (!data || !data.success) return;

    const dirEl = document.getElementById("tools-dir-path");
    if (dirEl) dirEl.innerText = data.tools_dir || "Standard system paths";

    const container = document.getElementById("capabilities-grid");
    if (!container) return;
    container.innerHTML = "";

    const activeTasks = data.active_tasks || {};
    let hasRunningTask = false;

    data.capabilities.forEach((cap) => {
        const task = activeTasks[cap.key];
        const isRunning = task && task.status === "in_progress";
        if (isRunning) hasRunningTask = true;

        let statusBadge = "";
        if (isRunning) {
            statusBadge = `<span class="badge badge-warning"><i class="fas fa-spinner fa-spin"></i> Installing (${task.percent || 10}%)</span>`;
        } else if (cap.installed) {
            const srcLabel = cap.source === "local" ? "Intra Local" : "System PATH";
            statusBadge = `<span class="badge badge-success"><svg class="admin-icon" style="width:13px;height:13px;color:var(--success);" aria-hidden="true"><use href="#admin-check"></use></svg> Installed (${srcLabel})</span>`;
        } else {
            statusBadge = `<span class="badge badge-danger"><svg class="admin-icon" style="width:13px;height:13px;color:var(--danger);" aria-hidden="true"><use href="#admin-ban"></use></svg> Missing</span>`;
        }

        let actionBtn = "";
        if (isRunning) {
            actionBtn = `<button class="btn-action" disabled style="opacity:0.7; cursor:not-allowed;"><i class="fas fa-spinner fa-spin"></i> Downloading...</button>`;
        } else if (!cap.installed) {
            actionBtn = `<button class="btn-action btn-approve" data-action="install" data-key="${cap.key}"><i class="fas fa-download"></i> Install Addon</button>`;
        } else if (cap.can_uninstall) {
            actionBtn = `<button class="btn-action btn-delete" data-action="uninstall" data-key="${cap.key}"><svg class="admin-icon" style="width:14px;height:14px;color:var(--danger);" aria-hidden="true"><use href="#admin-trash"></use></svg> Remove Local</button>`;
        } else {
            actionBtn = `<span style="font-size:12px; color:var(--text-muted); display:inline-flex; align-items:center; gap:5px;"><svg class="admin-icon" style="width:14px;height:14px;" aria-hidden="true"><use href="#admin-security"></use></svg> System Installed</span>`;
        }

        let progressHtml = "";
        if (isRunning) {
            progressHtml = `
                <div class="progress-bar-bg" style="margin-top:12px; height:8px;">
                    <div class="progress-bar-fill" style="width: ${Math.max(8, task.percent || 10)}%;"></div>
                </div>
                <div style="font-size:11px; color:var(--text-muted); margin-top:4px;"><i class="fas fa-sync fa-spin"></i> ${task.message || "Downloading..."}</div>
            `;
        } else if (task && task.status === "failed") {
            progressHtml = `
                <div style="font-size:11px; color:var(--danger); margin-top:8px;"><i class="fas fa-exclamation-triangle"></i> ${task.message}</div>
            `;
        }

        const pathDisplay = cap.path
            ? `<div style="font-family:monospace; font-size:11px; color:var(--text-muted); margin-top:8px; word-break:break-all;"><i class="fas fa-terminal"></i> ${cap.path}</div>`
            : "";

        const card = document.createElement("div");
        card.className = "card";
        card.style.display = "flex";
        card.style.flexDirection = "column";
        card.style.justifyContent = "space-between";
        card.innerHTML = `
            <div>
                <div style="display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:10px; gap:8px;">
                    <h3 style="font-size:16px; font-weight:700; color:var(--text-main); margin:0;">${cap.title}</h3>
                    ${statusBadge}
                </div>
                <p style="font-size:13px; color:var(--text-muted); margin-bottom:10px; line-height:1.5;">${cap.description}</p>
                ${pathDisplay}
                ${progressHtml}
            </div>
            <div style="margin-top:16px; padding-top:12px; border-top:1px solid var(--surface-border); display:flex; justify-content:flex-end;">
                ${actionBtn}
            </div>
        `;
        container.appendChild(card);
    });

    if (hasRunningTask) {
        if (!capPollTimer) {
            capPollTimer = setInterval(loadCapabilities, 1500);
        }
    } else if (capPollTimer) {
        clearInterval(capPollTimer);
        capPollTimer = null;
    }
}

export async function installCapability(toolKey) {
    const res = await fetchAdmin(`/admin/capabilities/install?tool_key=${encodeURIComponent(toolKey)}`, { method: "POST" });
    if (res && res.success) {
        showToast(`Started downloading & installing ${toolKey}...`);
        loadCapabilities();
        if (!capPollTimer) {
            capPollTimer = setInterval(loadCapabilities, 1500);
        }
    } else {
        showToast(res ? res.message : "Failed to start install.", true);
    }
}

export async function uninstallCapability(toolKey) {
    if (!confirm(`Are you sure you want to remove ${toolKey} local binaries from %LOCALAPPDATA%/IntraServer/bin?`)) return;
    const res = await fetchAdmin(`/admin/capabilities/uninstall?tool_key=${encodeURIComponent(toolKey)}`, { method: "POST" });
    if (res && res.success) {
        showToast(res.message);
        loadCapabilities();
    } else {
        showToast(res ? res.message : "Failed to remove tool.", true);
    }
}

export function initCapabilitiesTab() {
    document.getElementById("btn-refresh-capabilities")?.addEventListener("click", loadCapabilities);

    document.getElementById("capabilities-grid")?.addEventListener("click", (e) => {
        const btn = e.target.closest("button[data-action]");
        if (!btn) return;
        const { action, key } = btn.dataset;
        if (action === "install") installCapability(key);
        if (action === "uninstall") uninstallCapability(key);
    });
}
