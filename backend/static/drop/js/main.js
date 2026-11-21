import { initTheme, toggleTheme } from "./utils.js";
import { loadFiles, initFilesTab } from "./files.js";
import { loadSnippets, initClipboardTab } from "./clipboard.js";
import { initQrModal } from "./qr.js";

initTheme();

function switchTab(tab) {
  const filesTab = document.getElementById("tab-files");
  const clipTab = document.getElementById("tab-clipboard");
  const buttons = document.querySelectorAll(".nav-tab");

  if (tab === "files") {
    filesTab.style.display = "flex";
    clipTab.style.display = "none";
    buttons[0]?.classList.add("active");
    buttons[1]?.classList.remove("active");
  } else {
    filesTab.style.display = "none";
    clipTab.style.display = "flex";
    buttons[0]?.classList.remove("active");
    buttons[1]?.classList.add("active");
    loadSnippets();
  }
}

window.addEventListener("DOMContentLoaded", () => {
  document.getElementById("btn-refresh-files")?.addEventListener("click", loadFiles);
  document.getElementById("btn-theme-toggle")?.addEventListener("click", toggleTheme);

  document.getElementById("drop-nav-tabs")?.addEventListener("click", (e) => {
    const tabBtn = e.target.closest(".nav-tab[data-tab]");
    if (!tabBtn) return;
    switchTab(tabBtn.dataset.tab);
  });

  initFilesTab();
  initClipboardTab();
  initQrModal();

  loadFiles();

  setInterval(() => {
    loadFiles();
    if (document.getElementById("tab-clipboard").style.display !== "none") {
      loadSnippets();
    }
  }, 6000);
});
