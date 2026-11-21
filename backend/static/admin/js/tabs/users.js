import { fetchAdmin } from "../api.js";
import { showToast } from "../ui.js";

export async function loadUsers() {
    const tbody = document.getElementById("users-tbody");
    tbody.innerHTML = `<tr><td colspan="4" style="text-align:center; color:var(--text-muted);">Loading users...</td></tr>`;

    const data = await fetchAdmin("/admin/users");
    if (!data || !data.success) return;

    if (data.users.length === 0) {
        tbody.innerHTML = `<tr><td colspan="4" style="text-align:center; color:var(--text-muted);">No registered users found.</td></tr>`;
        return;
    }

    tbody.innerHTML = data.users.map((u) => {
        const avatar = u.profile_photo
            ? `<img src="${u.profile_photo}" onerror="this.src=''"/>`
            : u.username.charAt(0).toUpperCase();

        const isApproved = u.is_approved === 1 || u.is_approved === true;
        const statusBadge = isApproved
            ? `<span class="badge badge-success"><svg class="admin-icon" style="width:13px;height:13px;color:var(--success);" aria-hidden="true"><use href="#admin-check"></use></svg> Approved</span>`
            : `<span class="badge badge-warning"><i class="fas fa-clock"></i> Pending</span>`;

        const presenceBadge = u.is_online
            ? `<span class="badge badge-success"><i class="fas fa-circle" style="font-size:8px;"></i> Online</span>`
            : `<span class="badge badge-muted"><i class="fas fa-circle" style="font-size:8px;"></i> Offline</span>`;

        const approveBtn = !isApproved
            ? `<button class="btn-action btn-approve" data-action="approve" data-username="${u.username}"><svg class="admin-icon" style="width:14px;height:14px;color:var(--success);" aria-hidden="true"><use href="#admin-check"></use></svg> Approve</button>`
            : ``;

        return `
            <tr>
                <td>
                    <div class="user-cell">
                        <div class="user-avatar">${avatar}</div>
                        <span style="font-weight:600;">${u.username}</span>
                    </div>
                </td>
                <td>${statusBadge}</td>
                <td>${presenceBadge}</td>
                <td>
                    <div class="action-btns">
                        ${approveBtn}
                        <button class="btn-action btn-reset" data-action="reset" data-username="${u.username}"><i class="fas fa-key"></i> Reset</button>
                        <button class="btn-action btn-delete" data-action="delete" data-username="${u.username}" title="Delete User"><svg class="admin-icon" style="width:14px;height:14px;color:var(--danger);" aria-hidden="true"><use href="#admin-trash"></use></svg></button>
                    </div>
                </td>
            </tr>
        `;
    }).join("");
}

export async function approveUser(username) {
    const res = await fetchAdmin(`/admin/approve_user?username=${encodeURIComponent(username)}`, { method: "POST" });
    if (res && res.success) {
        showToast(`User ${username} approved!`);
        loadUsers();
    }
}

export async function resetPassword(username) {
    const newPass = prompt(`Enter new password for ${username}:`);
    if (!newPass) return;
    const res = await fetchAdmin(`/admin/reset_password?username=${encodeURIComponent(username)}&new_pass=${encodeURIComponent(newPass)}`, { method: "POST" });
    if (res && res.success) {
        showToast(`Password updated for ${username}!`);
    }
}

export async function deleteUser(username) {
    if (!confirm(`Are you sure you want to permanently delete user '${username}'?`)) return;
    const res = await fetchAdmin(`/admin/delete_user?username=${encodeURIComponent(username)}`, { method: "POST" });
    if (res && res.success) {
        showToast(`User ${username} deleted.`);
        loadUsers();
    }
}

export function initUsersTab() {
    document.getElementById("btn-refresh-users")?.addEventListener("click", loadUsers);

    document.getElementById("users-tbody")?.addEventListener("click", (e) => {
        const btn = e.target.closest("button[data-action]");
        if (!btn) return;
        const { action, username } = btn.dataset;
        if (action === "approve") approveUser(username);
        if (action === "reset") resetPassword(username);
        if (action === "delete") deleteUser(username);
    });
}
