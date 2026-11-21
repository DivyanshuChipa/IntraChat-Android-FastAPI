import { fetchAdmin } from "../api.js";
import { showToast } from "../ui.js";

export async function loadLocation() {
    const locData = await fetchAdmin("/admin/weather_location");
    if (locData && locData.success) {
        document.getElementById("geo-lat").value = locData.default_lat ?? "";
        document.getElementById("geo-lon").value = locData.default_lon ?? "";
    }
    await loadEnvironmentSettings();
}

export async function searchCityCoordinates() {
    const city = document.getElementById("city-search-input").value.trim();
    const statusEl = document.getElementById("city-search-status");
    if (!city) {
        showToast("Please enter a city name to search.", true);
        return;
    }

    statusEl.innerHTML = `<i class="fas fa-spinner fa-spin"></i> Searching Open-Meteo geocoding...`;
    try {
        const res = await fetch(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(city)}&count=1`);
        if (res.ok) {
            const data = await res.json();
            const results = data.results || [];
            if (results.length > 0) {
                const top = results[0];
                document.getElementById("geo-lat").value = top.latitude;
                document.getElementById("geo-lon").value = top.longitude;
                const parts = [top.name, top.admin1, top.country].filter(Boolean);
                statusEl.innerHTML = `<span style="color:var(--success);"><svg class="admin-icon" style="width:14px;height:14px;color:var(--success);" aria-hidden="true"><use href="#admin-check"></use></svg> Found: ${parts.join(", ")} (${top.latitude}, ${top.longitude})</span>`;
                showToast(`Found ${top.name}! Coordinates filled.`);
            } else {
                statusEl.innerHTML = `<span style="color:var(--danger);"><svg class="admin-icon" style="width:14px;height:14px;color:var(--danger);" aria-hidden="true"><use href="#admin-ban"></use></svg> City not found.</span>`;
                showToast("City not found.", true);
            }
        } else {
            statusEl.innerHTML = `<span style="color:var(--danger);"><i class="fas fa-exclamation-triangle"></i> Geocoding API returned status ${res.status}</span>`;
        }
    } catch (err) {
        statusEl.innerHTML = `<span style="color:var(--danger);"><i class="fas fa-exclamation-triangle"></i> Network error during city search.</span>`;
    }
}

export async function saveLocation() {
    const lat = parseFloat(document.getElementById("geo-lat").value);
    const lon = parseFloat(document.getElementById("geo-lon").value);
    if (isNaN(lat) || isNaN(lon)) {
        showToast("Please enter valid numeric coordinates.", true);
        return;
    }
    const res = await fetchAdmin(`/admin/weather_location?lat=${lat}&lon=${lon}`, { method: "POST" });
    if (res && res.success) showToast("Weather location coordinates saved!");
    else showToast(res ? res.message : "Failed to save location coordinates.", true);
}

export async function loadEnvironmentSettings() {
    const data = await fetchAdmin("/admin/environment_settings");
    if (data && data.success && data.settings) {
        const s = data.settings;
        document.getElementById("sentinel-enabled").checked = s.sentinel_enabled === "1" || s.sentinel_enabled === true || s.sentinel_enabled === 1;
        document.getElementById("env-mode").value = s.env_mode || "auto";
        document.getElementById("alert-temp").value = s.alert_temp ?? 40.0;
        document.getElementById("alert-wind").value = s.alert_wind ?? 40.0;
        document.getElementById("alert-rain").value = s.alert_rain ?? 5.0;
    }
}

export async function saveEnvironmentSettings() {
    const enabled = document.getElementById("sentinel-enabled").checked;
    const mode = document.getElementById("env-mode").value;
    const temp = parseFloat(document.getElementById("alert-temp").value);
    const wind = parseFloat(document.getElementById("alert-wind").value);
    const rain = parseFloat(document.getElementById("alert-rain").value);

    if (isNaN(temp) || isNaN(wind) || isNaN(rain)) {
        showToast("Threshold limits must be valid numbers.", true);
        return;
    }

    const endpoint = `/admin/environment_settings?enabled=${enabled}&mode=${encodeURIComponent(mode)}&temp=${temp}&wind=${wind}&rain=${rain}`;
    const res = await fetchAdmin(endpoint, { method: "POST" });
    if (res && res.success) {
        showToast("Smart Environment settings saved successfully!");
        document.getElementById("env-status-msg").innerText = "✅ Saved!";
        setTimeout(() => { document.getElementById("env-status-msg").innerText = ""; }, 4000);
    } else {
        showToast(res ? res.message : "Failed to save Sentinel settings.", true);
    }
}

export async function testWeatherSync() {
    showToast("Triggering weather sync...");
    const res = await fetchAdmin("/admin/test_weather_sync", { method: "POST" });
    if (res && res.success) {
        showToast(res.message || "Weather cache updated!");
    } else {
        showToast(res ? res.message : "Weather sync failed.", true);
    }
}

export async function testSentinelAlert() {
    showToast("Running Sentinel hazard check...");
    const res = await fetchAdmin("/admin/test_sentinel_alert", { method: "POST" });
    if (res && res.success) {
        showToast(res.message || "Sentinel hazard check completed!");
    } else {
        showToast(res ? res.message : "Sentinel check failed.", true);
    }
}

export function initWeatherTab() {
    document.getElementById("btn-save-env")?.addEventListener("click", saveEnvironmentSettings);
    document.getElementById("btn-test-weather-sync")?.addEventListener("click", testWeatherSync);
    document.getElementById("btn-test-sentinel-alert")?.addEventListener("click", testSentinelAlert);
    document.getElementById("btn-search-city")?.addEventListener("click", searchCityCoordinates);
    document.getElementById("btn-save-location")?.addEventListener("click", saveLocation);
}
