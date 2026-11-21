import { escapeHtml } from "./utils.js";

const snippetTextMap = new Map();

export async function loadSnippets() {
  try {
    const res = await fetch("/api/drop/texts");
    const data = await res.json();
    if (!data.success) return;

    const list = document.getElementById("snippets-list");
    const clipActions = document.getElementById("clipboard-header-actions");
    if (clipActions) {
      clipActions.style.display = data.texts && data.texts.length > 0 ? "flex" : "none";
    }

    snippetTextMap.clear();

    if (!data.texts || data.texts.length === 0) {
      list.innerHTML = `<div class="empty-state">No shared text snippets yet.</div>`;
      return;
    }

    list.innerHTML = data.texts.map((s) => {
      snippetTextMap.set(String(s.id), s.text);
      const dateStr = new Date(s.timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
      const isUrl = /^https?:\/\//i.test(s.text.trim());
      const urlButton = isUrl
        ? `<a href="${escapeHtml(s.text.trim())}" target="_blank" rel="noopener noreferrer" class="link-btn">🔗 Open</a>`
        : "";
      return `
        <div class="snippet-card">
          <div style="flex: 1; min-width: 0;">
            <div class="snippet-text">${escapeHtml(s.text)}</div>
            <div class="snippet-meta">From ${escapeHtml(s.sender || "Guest")} • ${dateStr}</div>
          </div>
          <div style="display: flex; gap: 6px; align-items: center; flex-shrink: 0;">
            ${urlButton}
            <button class="copy-btn" data-action="copy-snippet" data-id="${s.id}">
              <svg viewBox="0 0 24 24" fill="none"><rect x="8" y="7" width="12" height="14" rx="2" stroke="url(#drop-grad)" stroke-width="1.8"/><path d="M16 7V5a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h2" stroke="url(#drop-grad)" stroke-width="1.8" stroke-linecap="round"/></svg>
              <span>Copy</span>
            </button>
            <button class="del-snippet-btn" data-action="delete-snippet" data-id="${s.id}" title="Delete Snippet">
              <svg viewBox="0 0 24 24" fill="none"><path d="M4 7h16M9 7V4h6v3m3 0-.8 13H6.8L6 7m4 4v5m4-5v5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </button>
          </div>
        </div>
      `;
    }).join("");
  } catch (e) {
    console.error("Error loading texts:", e);
  }
}

export async function shareText() {
  const input = document.getElementById("clipboard-input");
  const text = input.value.trim();
  if (!text) return;

  try {
    const res = await fetch("/api/drop/text", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ text, sender: "Guest" })
    });
    const data = await res.json();
    if (data.success) {
      input.value = "";
      loadSnippets();
    }
  } catch (e) {
    console.error("Error sharing text:", e);
  }
}

async function deleteSnippet(id) {
  if (!id) return;
  try {
    const res = await fetch(`/api/drop/text/${id}`, { method: "DELETE" });
    if (res.ok) {
      loadSnippets();
      return;
    }
  } catch (_) {}
  try {
    await fetch("/api/drop/text/delete", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ id: Number(id) })
    });
    loadSnippets();
  } catch (e) {
    console.error("Failed to delete snippet", e);
  }
}

async function clearSnippets() {
  if (!confirm("Are you sure you want to clear all shared snippets?")) return;
  try {
    await fetch("/api/drop/text/clear", { method: "POST" });
    loadSnippets();
  } catch (e) {
    console.error("Failed to clear snippets", e);
  }
}

function copySnippet(btn, text) {
  navigator.clipboard.writeText(text).then(() => {
    if (btn) {
      const orig = btn.innerHTML;
      btn.innerHTML = "✅ Copied!";
      btn.style.background = "var(--brand)";
      btn.style.color = "#fff";
      setTimeout(() => {
        btn.innerHTML = orig;
        btn.style.background = "";
        btn.style.color = "";
      }, 1800);
    }
  }).catch(() => {
    alert("Copied to clipboard!");
  });
}

export function initClipboardTab() {
  document.getElementById("btn-share-text")?.addEventListener("click", shareText);
  document.getElementById("btn-clear-snippets")?.addEventListener("click", clearSnippets);

  document.getElementById("clipboard-input")?.addEventListener("keydown", (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
      e.preventDefault();
      shareText();
    }
  });

  document.getElementById("snippets-list")?.addEventListener("click", (e) => {
    const btn = e.target.closest("button[data-action]");
    if (!btn) return;
    const { action, id } = btn.dataset;
    if (action === "copy-snippet") {
      const text = snippetTextMap.get(String(id)) ?? "";
      copySnippet(btn, text);
    }
    if (action === "delete-snippet") {
      deleteSnippet(id);
    }
  });
}
