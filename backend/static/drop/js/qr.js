export function openQrModal() {
  const currentUrl = window.location.origin + "/drop";
  document.getElementById("qr-url-text").innerText = currentUrl;
  const qrApiUrl = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(currentUrl)}`;
  document.getElementById("qr-img").src = qrApiUrl;
  document.getElementById("qr-modal").style.display = "flex";
}

export function closeQrModal(e) {
  if (e.target.id === "qr-modal") {
    document.getElementById("qr-modal").style.display = "none";
  }
}

export function copyDropUrl() {
  const url = window.location.origin + "/drop";
  navigator.clipboard.writeText(url).then(() => {
    alert("WebDrop URL copied!");
  });
}

export function initQrModal() {
  document.getElementById("btn-open-qr")?.addEventListener("click", openQrModal);
  document.getElementById("qr-modal")?.addEventListener("click", closeQrModal);
  document.getElementById("btn-copy-drop-url")?.addEventListener("click", copyDropUrl);
}
