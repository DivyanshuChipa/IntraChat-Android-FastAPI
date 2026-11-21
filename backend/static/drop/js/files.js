import { escapeHtml } from "./utils.js";

let allFiles = [];
let currentCategory = "all";

const categorySvgs = {
  image: `<svg viewBox="0 0 24 24" fill="none"><path d="M4 8a2 2 0 0 1 2-2h2l1.3-2h5.4L16 6h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8Z" stroke="url(#drop-grad)" stroke-width="1.8" stroke-linejoin="round"/><circle cx="12" cy="12.5" r="3.2" stroke="url(#drop-grad)" stroke-width="1.8"/></svg>`,
  video: `<svg viewBox="0 0 24 24" fill="none"><rect x="3" y="5" width="18" height="14" rx="2.5" stroke="url(#drop-grad)" stroke-width="1.8"/><path d="m10 9 5 3-5 3V9Z" stroke="url(#drop-grad)" stroke-width="1.8" stroke-linejoin="round"/></svg>`,
  document: `<svg viewBox="0 0 24 24" fill="none"><path d="M6 3.5h8l4 4V20a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 6 20V3.5Z" stroke="url(#drop-grad)" stroke-width="1.8" stroke-linejoin="round"/><path d="M14 3.8V8h4M9 12h6M9 15.5h6" stroke="var(--brand)" stroke-width="1.6" stroke-linecap="round"/></svg>`,
  audio: `<svg viewBox="0 0 24 24" fill="none"><path d="M10 18V5l10-2v13M10 8l10-2" stroke="url(#drop-grad)" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><ellipse cx="7" cy="18" rx="3" ry="2" stroke="url(#drop-grad)" stroke-width="1.8"/><ellipse cx="17" cy="16" rx="3" ry="2" stroke="url(#drop-grad)" stroke-width="1.8"/></svg>`,
  archive: `<svg viewBox="0 0 24 24" fill="none"><path d="M4 7.5 12 3l8 4.5v9L12 21l-8-4.5v-9Z" stroke="url(#drop-grad)" stroke-width="1.8" stroke-linejoin="round"/><path d="m4.5 7.8 7.5 4.3 7.5-4.3M12 12v8.5M11 5.2h2v2h-2zM11 8.3h2v2h-2z" stroke="url(#drop-grad)" stroke-width="1.5" stroke-linejoin="round"/></svg>`,
  other: `<svg viewBox="0 0 24 24" fill="none"><path d="m9 12.8 6.4-6.4a3.2 3.2 0 0 1 4.5 4.5l-8.1 8.1a5 5 0 0 1-7.1-7.1l8.1-8.1" stroke="url(#drop-grad)" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>`
};

export async function loadFiles() {
  try {
    const res = await fetch("/api/drop/files");
    const data = await res.json();
    if (data.success) {
      allFiles = data.files;
      document.getElementById("file-count").innerText = allFiles.length;
      renderFiles();
    }
  } catch (e) {
    console.error("Error loading files:", e);
  }
}

export function renderFiles() {
  const grid = document.getElementById("file-grid");
  const query = document.getElementById("search-input").value.toLowerCase().trim();

  const filtered = allFiles.filter((f) => {
    const matchesCategory = currentCategory === "all" || f.category === currentCategory;
    const matchesQuery = !query || f.name.toLowerCase().includes(query);
    return matchesCategory && matchesQuery;
  });

  if (filtered.length === 0) {
    grid.innerHTML = `<div class="empty-state">No files found matching criteria.</div>`;
    return;
  }

  grid.innerHTML = filtered.map((f) => {
    const iconSvg = categorySvgs[f.category] || categorySvgs.other;
    const dateStr = new Date(f.uploaded_at).toLocaleDateString([], {
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit"
    });
    const safeName = escapeHtml(f.name);
    return `
      <div class="file-card">
        <div class="file-icon">${iconSvg}</div>
        <div class="file-info">
          <div class="file-name" title="${safeName}">${safeName}</div>
          <div class="file-meta">${f.formatted_size} • ${dateStr}</div>
        </div>
        <div class="file-actions">
          <a href="${f.url}" download class="action-btn" title="Download">
            <svg viewBox="0 0 24 24" fill="none"><path d="M4 15v4a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4M12 3v12m-4-4 4 4 4-4" stroke="url(#drop-grad)" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
          </a>
          <button class="action-btn delete" data-action="delete-file" data-filename="${safeName}" title="Delete">
            <svg viewBox="0 0 24 24" fill="none"><path d="M4 7h16M9 7V4h6v3m3 0-.8 13H6.8L6 7m4 4v5m4-5v5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
          </button>
        </div>
      </div>
    `;
  }).join("");
}

function uploadFiles(fileList) {
  const formData = new FormData();
  for (let i = 0; i < fileList.length; i++) {
    formData.append("files", fileList[i]);
  }

  const progressContainer = document.getElementById("progress-container");
  const progressFill = document.getElementById("progress-fill");
  const progressStatus = document.getElementById("progress-status");

  progressContainer.style.display = "block";
  progressFill.style.width = "0%";
  progressStatus.innerText = `Uploading ${fileList.length} file(s)...`;

  const xhr = new XMLHttpRequest();
  xhr.open("POST", "/api/drop/upload");

  xhr.upload.onprogress = (e) => {
    if (e.lengthComputable) {
      const percent = Math.round((e.loaded / e.total) * 100);
      progressFill.style.width = percent + "%";
      progressStatus.innerText = `Uploading: ${percent}%`;
    }
  };

  xhr.onload = () => {
    if (xhr.status >= 200 && xhr.status < 300) {
      progressStatus.innerText = "✅ Upload complete!";
      setTimeout(() => {
        progressContainer.style.display = "none";
      }, 1500);
      loadFiles();
    } else {
      progressStatus.innerText = "❌ Upload failed!";
    }
    document.getElementById("file-input").value = "";
  };

  xhr.onerror = () => {
    progressStatus.innerText = "❌ Network error";
  };

  xhr.send(formData);
}

async function deleteFile(filename) {
  if (!confirm(`Delete "${filename}" from WebDrop?`)) return;
  try {
    let res = await fetch(`/api/drop/file/${encodeURIComponent(filename)}`, { method: "DELETE" });
    if (!res.ok) {
      res = await fetch("/api/drop/delete", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ filename })
      });
    }
    const data = await res.json();
    if (data.success) {
      loadFiles();
    } else {
      alert(data.message || "Delete failed");
    }
  } catch (e) {
    console.error("Delete failed, trying fallback:", e);
    try {
      const res = await fetch("/api/drop/delete", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ filename })
      });
      const data = await res.json();
      if (data.success) {
        loadFiles();
        return;
      }
    } catch (_) {}
    alert("Failed to delete file: " + e.message);
  }
}

export function initFilesTab() {
  const dropZone = document.getElementById("drop-zone");
  const fileInput = document.getElementById("file-input");

  dropZone?.addEventListener("click", () => fileInput?.click());

  fileInput?.addEventListener("change", (e) => {
    if (e.target.files && e.target.files.length > 0) {
      uploadFiles(e.target.files);
    }
  });

  ["dragenter", "dragover"].forEach((eventName) => {
    dropZone?.addEventListener(eventName, (e) => {
      e.preventDefault();
      dropZone.classList.add("dragover");
    });
  });

  ["dragleave", "drop"].forEach((eventName) => {
    dropZone?.addEventListener(eventName, (e) => {
      e.preventDefault();
      dropZone.classList.remove("dragover");
    });
  });

  dropZone?.addEventListener("drop", (e) => {
    if (e.dataTransfer && e.dataTransfer.files.length > 0) {
      uploadFiles(e.dataTransfer.files);
    }
  });

  document.getElementById("search-input")?.addEventListener("input", renderFiles);

  document.getElementById("category-pills")?.addEventListener("click", (e) => {
    const pill = e.target.closest(".pill[data-category]");
    if (!pill) return;
    currentCategory = pill.dataset.category;
    document.querySelectorAll(".pill").forEach((p) => p.classList.remove("active"));
    pill.classList.add("active");
    renderFiles();
  });

  document.getElementById("file-grid")?.addEventListener("click", (e) => {
    const btn = e.target.closest('button[data-action="delete-file"]');
    if (!btn) return;
    deleteFile(btn.dataset.filename);
  });
}
