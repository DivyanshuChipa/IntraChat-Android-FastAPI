import { fetchAdmin } from "../api.js";
import { showToast } from "../ui.js";

function updateProviderContext() {
    const provider = document.getElementById("ai-provider")?.value || "auto";
    const cloudStatus = document.getElementById("ai-cloud-status");
    const cloudProvider = provider === "gemini" || provider === "sarvam";

    if (!cloudStatus) return;
    if (provider === "auto") {
        cloudStatus.textContent = "Auto mode uses available cloud credentials, then local fallback.";
        cloudStatus.dataset.state = "";
        return;
    }
    if (!cloudProvider) {
        cloudStatus.textContent = "Local inference selected.";
        cloudStatus.dataset.state = "";
        return;
    }

    const ready = cloudStatus.dataset[provider] === "true";
    cloudStatus.textContent = ready
        ? `${provider === "gemini" ? "Gemini" : "Sarvam"} credential is ready.`
        : `${provider === "gemini" ? "Gemini" : "Sarvam"} API key is not configured. Add it in Cloud AI Credentials below.`;
    cloudStatus.dataset.state = ready ? "ready" : "missing";
}

function updateVoiceNodeFields() {
    const el = document.getElementById("voice-node-enabled");
    if (!el) return;
    const enabled = Boolean(el.checked);
    const fields = document.querySelector(".voice-node-fields");
    fields?.classList.toggle("is-hidden", !enabled);
    fields?.querySelectorAll("input, button").forEach((btn) => { btn.disabled = !enabled; });
}

export async function loadAIConfig({ quiet = false } = {}) {
    const data = await fetchAdmin("/admin/ai_settings");
    if (data && data.success && data.config) {
        const c = data.config;
        document.getElementById("ai-enabled").checked = Boolean(c.ai_enabled);

        const prov = (c.ai_provider || "auto").toLowerCase();
        if (["sarvam", "gemini", "ollama", "llamacpp"].includes(prov)) {
            document.getElementById("ai-provider").value = prov;
        } else {
            document.getElementById("ai-provider").value = "auto";
        }

        // 1. Cloud Credentials Badges (Dedicated Card)
        const badgeGemini = document.getElementById("badge-gemini");
        if (badgeGemini) {
            if (c.has_gemini) {
                badgeGemini.className = "badge badge-success";
                badgeGemini.innerHTML = `● Active (${c.gemini_masked || "Ready"})`;
            } else {
                badgeGemini.className = "badge badge-muted";
                badgeGemini.innerHTML = `○ Not Set`;
            }
        }

        const badgeSarvam = document.getElementById("badge-sarvam");
        if (badgeSarvam) {
            if (c.has_sarvam) {
                badgeSarvam.className = "badge badge-success";
                badgeSarvam.innerHTML = `● Active (${c.sarvam_masked || "Ready"})`;
            } else {
                badgeSarvam.className = "badge badge-muted";
                badgeSarvam.innerHTML = `○ Not Set`;
            }
        }

        // Store provider credential state for a compact selected-provider hint.
        const cloudStatusEl = document.getElementById("ai-cloud-status");
        if (cloudStatusEl) {
            cloudStatusEl.dataset.gemini = String(Boolean(c.has_gemini));
            cloudStatusEl.dataset.sarvam = String(Boolean(c.has_sarvam));
        }

        // Masked key placeholders
        const sarvamInput = document.getElementById("ai-sarvam-key");
        if (sarvamInput) {
            sarvamInput.value = "";
            sarvamInput.placeholder = c.sarvam_masked 
                ? `Active in Server (${c.sarvam_masked}) - Type to override`
                : "Paste sk_... key to save/override";
        }
        const geminiInput = document.getElementById("ai-gemini-key");
        if (geminiInput) {
            geminiInput.value = "";
            geminiInput.placeholder = c.gemini_masked 
                ? `Active in Server (${c.gemini_masked}) - Type to override`
                : "Paste AIzaSy... key to save/override";
        }

        document.getElementById("ai-url").value = c.ollama_url || "http://localhost:11434";
        document.getElementById("ai-model").value = c.ai_model || "gemini-2.0-flash";
        document.getElementById("ai-vision").value = c.ai_vision_model || "gemma:27b-cloud";
        document.getElementById("ai-fallback").value = c.ai_fallback || "gemma3:270m";
        document.getElementById("ai-smart-models").value = c.ai_smart_models || "gpt-oss:20b-cloud";

        // Distributed Node 2 / Auxiliary
        const voiceNodeEnabledEl = document.getElementById("voice-node-enabled");
        if (voiceNodeEnabledEl) voiceNodeEnabledEl.checked = Boolean(c.voice_node_enabled);
        const voiceNodeUrlEl = document.getElementById("voice-node-url");
        if (voiceNodeUrlEl) voiceNodeUrlEl.value = c.voice_node_url || "http://192.168.31.94:8005";
        updateProviderContext();
        updateVoiceNodeFields();
        
        const node2LlamaInput = document.getElementById("ai-node2-llama-url");
        if (node2LlamaInput) {
            node2LlamaInput.value = c.node2_llama_url || "";
        }

        // Voice Engine settings
        const voiceProv = (c.voice_provider || "auto").toLowerCase();
        const voiceProvSelect = document.getElementById("voice-provider");
        if (voiceProvSelect) voiceProvSelect.value = voiceProv;

        const voiceSpeaker = c.voice_speaker || "simran_enhi_companion";
        const voiceSpeakerSelect = document.getElementById("voice-speaker");
        if (voiceSpeakerSelect) voiceSpeakerSelect.value = voiceSpeaker;

        if (!quiet) document.getElementById("ai-status-msg").innerText = "Settings loaded.";

        // Initial model fetch
        fetchAvailableModels(prov, false);
        return true;
    }
    return false;
}

export async function fetchAvailableModels(providerOverride = null, notify = true) {
    const provider = providerOverride || document.getElementById("ai-provider")?.value || "auto";
    const url = document.getElementById("ai-url")?.value?.trim() || "";
    const selectEl = document.getElementById("ai-model-select");
    const modelInput = document.getElementById("ai-model");
    const statusEl = document.getElementById("ai-model-status");

    if (notify && statusEl) {
        statusEl.innerHTML = `<span style="color:var(--text-muted);"><i class="fas fa-spinner fa-spin"></i> Discovering ${provider} models...</span>`;
    }

    try {
        const res = await fetchAdmin(`/admin/fetch_models?provider=${encodeURIComponent(provider)}&url=${encodeURIComponent(url)}`);
        if (res && res.success && res.models && res.models.length > 0) {
            const currentModel = modelInput ? modelInput.value.trim() : "";
            
            if (selectEl) {
                selectEl.innerHTML = `<option value="">-- Click to choose from ${res.models.length} Discovered Models --</option>`;
                let matched = false;
                res.models.forEach(m => {
                    const opt = document.createElement("option");
                    opt.value = m.id;
                    opt.textContent = m.name;
                    if (m.id === currentModel) {
                        opt.selected = true;
                        matched = true;
                    }
                    selectEl.appendChild(opt);
                });
                // If model not in list or blank, and list has items, suggest the first recommended one
                if (!currentModel && res.models.length > 0 && modelInput) {
                    modelInput.value = res.models[0].id;
                    selectEl.value = res.models[0].id;
                }
            }

            if (statusEl) {
                statusEl.innerHTML = `<span style="color:#10b981;">✅ Discovered ${res.models.length} ${res.provider} model(s). Pick from dropdown above or edit ID!</span>`;
            }
            if (notify) showToast(`Found ${res.models.length} ${res.provider} models!`);
        } else {
            if (selectEl) {
                selectEl.innerHTML = `<option value="">-- No dynamic models discovered --</option>`;
            }
            if (statusEl) {
                statusEl.innerHTML = `<span style="color:#f59e0b;">ℹ️ ${res ? res.message : "No dynamic models discovered."}</span>`;
            }
        }
    } catch (e) {
        if (statusEl) statusEl.innerHTML = `<span style="color:#ef4444;">Failed to discover models: ${e.message}</span>`;
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
    const voiceEnabled = Boolean(document.getElementById("voice-node-enabled")?.checked ?? true);
    const voiceUrl = encodeURIComponent(document.getElementById("voice-node-url")?.value?.trim() || "http://192.168.31.94:8005");
    const node2LlamaUrl = encodeURIComponent(document.getElementById("ai-node2-llama-url")?.value?.trim() || "");

    // Cloud Keys (if user typed new ones)
    const sarvamKey = encodeURIComponent(document.getElementById("ai-sarvam-key")?.value?.trim() || "");
    const geminiKey = encodeURIComponent(document.getElementById("ai-gemini-key")?.value?.trim() || "");

    // Voice Provider & Speaker
    const voiceProvider = encodeURIComponent(document.getElementById("voice-provider")?.value || "auto");
    const voiceSpeaker = encodeURIComponent(document.getElementById("voice-speaker")?.value || "simran_enhi_companion");

    const endpoint = `/admin/ai_settings?enabled=${enabled}&model=${model}&url=${url}&vision=${vision}&fallback=${fallback}&smart_models=${smartModels}&voice_node_url=${voiceUrl}&voice_node_enabled=${voiceEnabled}&ai_provider=${provider}&node2_llama_url=${node2LlamaUrl}&sarvam_key=${sarvamKey}&gemini_key=${geminiKey}&voice_provider=${voiceProvider}&voice_speaker=${voiceSpeaker}`;

    const saveButton = document.getElementById("btn-save-ai");
    const statusMessage = document.getElementById("ai-status-msg");
    if (saveButton) saveButton.disabled = true;
    if (statusMessage) statusMessage.textContent = "Saving configuration…";

    try {
        const res = await fetchAdmin(endpoint, { method: "POST" });
        if (res && res.success) {
            const reloaded = await loadAIConfig({ quiet: true });
            if (statusMessage) {
                statusMessage.textContent = reloaded
                    ? "Saved and verified ✓"
                    : "Saved on server; could not reload the settings.";
            }
            showToast(reloaded ? "Lumir settings saved and verified." : "Lumir settings saved.");
        } else {
            const message = res ? res.message : "Save failed. Check the connection and try again.";
            if (statusMessage) statusMessage.textContent = message;
            showToast(message, true);
        }
    } finally {
        if (saveButton) saveButton.disabled = false;
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

export async function testCognitiveNode() {
    const url = document.getElementById("ai-node2-llama-url")?.value?.trim();
    const statusEl = document.getElementById("cognitive-node-status");
    if (!url) {
        showToast("Please enter llama.cpp Server URL first!", true);
        return;
    }

    statusEl.innerHTML = `<span style="color:var(--text-muted);"><i class="fas fa-spinner fa-spin"></i> Probing llama.cpp endpoint...</span>`;

    const res = await fetchAdmin(`/admin/test_cognitive_node?url=${encodeURIComponent(url)}`);
    if (res && res.success) {
        const latency = res.latency_ms || 0;
        const modelName = res.model || "Active";
        statusEl.innerHTML = `<span style="color:var(--success);"><svg class="admin-icon" style="width:14px;height:14px;color:var(--success);" aria-hidden="true"><use href="#admin-check"></use></svg> Online (${latency} ms) | Model: ${modelName}</span>`;
        showToast(`Cognitive Node reachable in ${latency} ms!`);
    } else {
        statusEl.innerHTML = `<span style="color:var(--danger);"><i class="fas fa-times-circle"></i> ${res ? res.message : "Connection failed"}</span>`;
        showToast(res ? res.message : "Cognitive Node unreachable.", true);
    }
}

export async function testSarvamVoice() {
    const speaker = document.getElementById("voice-speaker")?.value || "simran_enhi_companion";
    const statusEl = document.getElementById("sarvam-voice-status");
    if (statusEl) {
        statusEl.innerHTML = `<span style="color:var(--text-muted);"><i class="fas fa-spinner fa-spin"></i> Synthesizing Sarvam Bulbul sample (${speaker})...</span>`;
    }

    const res = await fetchAdmin(`/admin/test_sarvam_voice?speaker=${encodeURIComponent(speaker)}`);
    if (res && res.success && res.audio_base64) {
        try {
            const audio = new Audio("data:audio/wav;base64," + res.audio_base64);
            audio.play().catch(e => console.warn("Audio autoplay blocked by browser policy:", e));
        } catch (e) {
            console.warn("Audio error:", e);
        }
        if (statusEl) {
            statusEl.innerHTML = `<span style="color:var(--success);"><svg class="admin-icon" style="width:14px;height:14px;color:var(--success);" aria-hidden="true"><use href="#admin-check"></use></svg> Playing sample (${res.speaker || speaker})!</span>`;
        }
        showToast(`Voice sample generated (${speaker})!`);
    } else {
        if (statusEl) {
            statusEl.innerHTML = `<span style="color:var(--danger);"><i class="fas fa-times-circle"></i> ${res ? res.message : "Voice synthesis failed"}</span>`;
        }
        showToast(res ? res.message : "Voice test failed", true);
    }
}

export function initLumirTab() {
    document.getElementById("btn-test-voice")?.addEventListener("click", testVoiceNode);
    document.getElementById("btn-test-cognitive")?.addEventListener("click", testCognitiveNode);
    document.getElementById("btn-test-sarvam-voice")?.addEventListener("click", testSarvamVoice);
    document.getElementById("btn-save-ai")?.addEventListener("click", saveAIConfig);
    document.getElementById("btn-fetch-models")?.addEventListener("click", () => fetchAvailableModels(null, true));
    
    // When provider is changed, auto-fetch models
    document.getElementById("ai-provider")?.addEventListener("change", (e) => {
        updateProviderContext();
        fetchAvailableModels(e.target.value, false);
    });

    document.getElementById("voice-node-enabled")?.addEventListener("change", updateVoiceNodeFields);

    // When a model is chosen from dropdown, sync into text box
    document.getElementById("ai-model-select")?.addEventListener("change", (e) => {
        if (e.target.value) {
            document.getElementById("ai-model").value = e.target.value;
        }
    });

    // When user types in text box, update dropdown if matching option exists
    document.getElementById("ai-model")?.addEventListener("input", (e) => {
        const val = e.target.value.trim();
        const selectEl = document.getElementById("ai-model-select");
        if (selectEl) {
            const hasOption = Array.from(selectEl.options).some(opt => opt.value === val);
            if (hasOption) {
                selectEl.value = val;
            } else {
                selectEl.value = "";
            }
        }
    });
}
