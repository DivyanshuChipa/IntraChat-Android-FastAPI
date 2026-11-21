import { showToast, openAuthModal } from "./ui.js";

let adminKey = localStorage.getItem("intra_admin_key") || "";

export function getAdminKey() {
    return adminKey;
}

export function setAdminKey(key) {
    adminKey = key;
    localStorage.setItem("intra_admin_key", adminKey);
}

export function clearAdminKey() {
    adminKey = "";
    localStorage.removeItem("intra_admin_key");
}

export async function fetchAdmin(endpoint, options = {}) {
    if (!adminKey) {
        openAuthModal();
        return null;
    }

    options.headers = options.headers || {};
    options.headers["X-Admin-Key"] = adminKey;

    try {
        const res = await fetch(endpoint, options);
        if (res.status === 403) {
            showToast("Admin Key invalid! Please re-enter.", true);
            clearAdminKey();
            openAuthModal();
            return null;
        }
        return await res.json();
    } catch (err) {
        showToast("Connection error to Intra Server.", true);
        return null;
    }
}
