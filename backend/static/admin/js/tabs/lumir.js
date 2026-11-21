import { fetchAdmin } from "../api.js";
import { showToast } from "../ui.js";

export async function loadAIConfig() {
    const data = await fetchAdmin("/admin/ai_settings");
    if (data && data.success && data.config) {
        const c = data.config;
        document.getElementById("ai-enabled").checked = Boolean(c.ai_enabled);

        const prov = (c.ai_provider || "auto").toLowerCase();
        if (prov === "ollama") {
            document.getElementById("ai-provider").value = "ollama";
        } else if (prov === "llamacpp" || prov === "llama.cpp") {
            document.getElementById("ai-provider").value = "llamacpp";
        } else {
            document.getElementById("ai-provider").value = "auto";
        }

        document.getElementById("ai-url").value = c.ollama_url || "http://localhost:11434";
        document.getElementById("ai-model").value = c.ai_model || "gpt-oss:20b-cloud";
        document.getElementById("ai-vision").value = c.ai_vision_model || "gemma:27b-cloud";
        document.getElementById("ai-fallback").value = c.ai_fallback || "gemma3:270m";
        document.getElementById("ai-smart-models").value = c.ai_smart_models || "gpt-oss:20b-cloud";

        document.getElementById("voice-node-enabled").checked = Boolean(c.voice_node_enabled);
        document.getElementById("voice-node-url").value = c.voice_node_url || "http://192.168.31.94:8005";
        document.getElementById("ai-status-msg").innerText = "Settings loaded.";
    }
}

export async function saveAIConfig() {
    const enabled = document.getElementById("ai-enabled").checked;
    const provider = document.getElementById("ai-provider").value;
    const url = encodeURIComponent(document.getElementById("ai-url").value.trim());
    const model = encodeURIComponent(document.getElementById("ai-model").value.trim());
    const vision = encodeURIComponent(document.getElementById("ai-vision").value.trim());
    const fallback = encodeURIComponent(document.getElementById("ai-fallback").value.trim());
    const smartModels = encodeURIComponent(document.getElementById("ai-smart-models").value.trim());
    const voiceEnabled = document.getElementById("voice-node-enabled").checked;
    const voiceUrl = encodeURIComponent(document.getElementById("voice-node-url").value.trim());

    const endpoint = `/admin/ai_settings?enabled=${enabled}&model=${model}&url=${url}&vision=${vision}&fallback=${fallback}&smart_models=${smartModels}&voice_node_url=${voiceUrl}&voice_node_enabled=${voiceEnabled}&ai_provider=${provider}`;

    const res = await fetchAdmin(endpoint, { method: "POST" });
    if (res && res.success) {
        showToast("Lumir AI configuration updated!");
        document.getElementById("ai-status-msg").innerText = "✅ Saved successfully!";
        setTimeout(() => { document.getElementById("ai-status-msg").innerText = ""; }, 4000);
    } else {
        showToast(res ? res.message : "Failed to save AI configuration", true);
    }
}

export async function testVoiceNode() {
    const url = document.getElementById("voice-node-url").value.trim();
    const statusEl = document.getElementById("voice-node-status");
    if (!url) {
        showToast("Please enter Voice Server URL first!", true);
        return;
    }

    statusEl.innerHTML = `<span style="color:var(--text-muted);"><i class="fas fa-spinner fa-spin"></i> Probing Node 2 Voice Server...</span>`;

    const res = await fetchAdmin(`/admin/test_voice_node?url=${encodeURIComponent(url)}`);
    if (res && res.success) {
        const latency = res.latency_ms || 0;
        const nodeInfo = res.node_info || {};
        const caps = nodeInfo.capabilities || {};
        const stt = caps.stt || "ok";
        const tts = caps.tts_primary || "ok";
        statusEl.innerHTML = `<span style="color:var(--success);"><svg class="admin-icon" style="width:14px;height:14px;color:var(--success);" aria-hidden="true"><use href="#admin-check"></use></svg> Online (${latency} ms) | STT: ${stt} | TTS: ${tts}</span>`;
        showToast(`Voice Node reachable in ${latency} ms!`);
    } else {
        statusEl.innerHTML = `<span style="color:var(--danger);"><i class="fas fa-times-circle"></i> ${res ? res.message : "Connection failed"}</span>`;
        showToast("Voice Node unreachable.", true);
    }
}

export function initLumirTab() {
    document.getElementById("btn-test-voice")?.addEventListener("click", testVoiceNode);
    document.getElementById("btn-save-ai")?.addEventListener("click", saveAIConfig);
}
