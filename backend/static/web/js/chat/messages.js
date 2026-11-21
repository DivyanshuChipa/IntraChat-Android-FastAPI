import { state } from "./state.js";

function renderRetroBlocks(totalBlocks = 16, percent = 0) {
  const filledCount = Math.floor((percent / 100) * totalBlocks);
  let html = "";
  for (let i = 0; i < totalBlocks; i++) {
    const isFilled = i < filledCount;
    const isLeading = i === filledCount - 1 && filledCount > 0 && percent < 100;
    const classes = ["retro-progress-block"];
    if (isFilled) classes.push("filled");
    if (isLeading) classes.push("leading");
    html += `<div class="${classes.join(" ")}"></div>`;
  }
  return html;
}

function updateUploadProgress(msgElement, percent) {
  if (!msgElement) return;
  const clamped = Math.min(100, Math.max(0, percent));

  const percentEl = msgElement.querySelector(".loader-percent");
  if (percentEl) percentEl.innerText = `${clamped}%`;

  const retroPercentEl = msgElement.querySelector(".retro-progress-percent");
  if (retroPercentEl) retroPercentEl.innerText = `${clamped}%`;

  const track = msgElement.querySelector(".retro-progress-track");
  if (track) track.innerHTML = renderRetroBlocks(16, clamped);
}

function formatDateSeparator(ts) {
  if (!ts) return "Unknown";
  const date = new Date(ts);
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);

  const isToday =
    date.getDate() === today.getDate() &&
    date.getMonth() === today.getMonth() &&
    date.getFullYear() === today.getFullYear();
  const isYesterday =
    date.getDate() === yesterday.getDate() &&
    date.getMonth() === yesterday.getMonth() &&
    date.getFullYear() === yesterday.getFullYear();

  if (isToday) return "Today";
  if (isYesterday) return "Yesterday";

  return date.toLocaleDateString("en-US", { day: "numeric", month: "short", year: "numeric" });
}

function formatMessageText(text) {
  if (!text) return "";

  let formatted = text;
  let hasFallback = false;
  if (formatted.includes("*(Fallback Mode)*") || formatted.includes("(Fallback Mode)")) {
    hasFallback = true;
    formatted = formatted.replace(/\*?\(Fallback Mode\)\*?\s*:?\s*/gi, "");
  }

  formatted = formatted
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");

  formatted = formatted.replace(
    /`([^`]+)`/g,
    '<code style="background:rgba(var(--glow),0.18); padding:2px 6px; border-radius:6px; font-family:\'JetBrains Mono\',monospace; font-size:0.88em;">$1</code>'
  );
  formatted = formatted.replace(/\*\*([^*]+)\*\*/g, "<b>$1</b>");
  formatted = formatted.replace(/\*([^*]+)\*/g, "<i>$1</i>");

  const urlRegex = /(https?:\/\/[^\s<]+)/g;
  formatted = formatted.replace(
    urlRegex,
    (url) => `<a href="${url}" target="_blank" style="color: inherit; text-decoration: underline; word-break: break-all;">${url}</a>`
  );
  formatted = formatted.replace(/\n/g, "<br>");

  if (hasFallback) {
    formatted = `<div class="fallback-badge">⚡ Fallback Model</div>` + formatted;
  }

  return formatted;
}

function isImage(url, mimeType = "") {
  if (mimeType.startsWith("image/")) return true;
  if (mimeType.startsWith("video/")) return false;
  return /\.(jpg|jpeg|png|webp|gif|svg)$/i.test(url);
}

function isVideo(url, mimeType = "") {
  if (mimeType.startsWith("video/")) return true;
  return /\.(mp4|mov|webm|mkv|avi|m4v)$/i.test(url);
}

function createVideoThumbnail(videoUrl) {
  return new Promise((resolve) => {
    const video = document.createElement("video");
    video.src = videoUrl;
    video.muted = true;
    video.playsInline = true;
    video.preload = "metadata";

    const fallback = () => resolve(null);

    video.onloadeddata = () => {
      try {
        const canvas = document.createElement("canvas");
        canvas.width = 320;
        canvas.height = (video.videoHeight / video.videoWidth) * 320 || 180;
        const ctx = canvas.getContext("2d");
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL("image/jpeg", 0.75));
      } catch (e) {
        console.error("Thumbnail generation failed:", e);
        fallback();
      }
    };

    video.onerror = fallback;
  });
}

export function displayMessage(
  sender,
  text,
  type,
  fileUrl = null,
  msgId = null,
  isLoading = false,
  videoThumbnail = null,
  mimeType = "",
  options = null,
  timestamp = null
) {
  if (timestamp) {
    const currentDateStr = formatDateSeparator(timestamp);
    if (currentDateStr !== state.lastRenderedDateStr) {
      const dateRow = document.createElement("div");
      dateRow.className = "date-separator-wrapper";
      dateRow.innerHTML = `<span class="date-separator">${currentDateStr}</span>`;
      document.getElementById("messages").appendChild(dateRow);
      state.lastRenderedDateStr = currentDateStr;
    }
  }

  const row = document.createElement("div");
  row.className = `msg-row ${type}`;
  if (msgId) row.id = msgId;

  const bubble = document.createElement("div");
  bubble.className = "msg-bubble";
  if (isLoading) bubble.classList.add("loading");

  const displayName = sender === state.myUsername ? "Me" : sender;

  let content = "";
  if (state.currentReceiver === "Family Group" && sender !== state.myUsername) {
    const colors = ["#E53935", "#D81B60", "#8E24AA", "#3949AB", "#039BE5", "#00897B", "#43A047", "#F4511E"];
    let hash = 0;
    for (let i = 0; i < sender.length; i++) {
      hash = sender.charCodeAt(i) + ((hash << 5) - hash);
    }
    const senderColor = colors[Math.abs(hash) % colors.length];
    content += `<div style="color: ${senderColor}; font-weight: bold; font-size: 0.8em; margin-bottom: 4px;">${displayName}</div>`;
  } else if (sender !== state.myUsername) {
    content += `<div style="font-weight: bold; font-size: 0.8em; margin-bottom: 4px;">${displayName}</div>`;
  }

  if (fileUrl) {
    const squareLoaderHtml = `
      <div class="loader-square-container">
        <div class="loader-square-spinner"></div>
        <span class="loader-percent">0%</span>
      </div>`;

    const retroProgressHtml = `
      <div class="retro-progress-wrapper">
        <div class="retro-progress-track">
          ${renderRetroBlocks(16, 0)}
        </div>
        <div class="retro-progress-labels">
          <span>UPLOADING...</span>
          <span class="retro-progress-percent">0%</span>
        </div>
      </div>`;

    if (isImage(fileUrl, mimeType)) {
      content += `<br>
        <div class="media-preview-container">
            <img src="${fileUrl}" class="media-preview-image">
            ${isLoading ? squareLoaderHtml : ""}
        </div>
        ${isLoading ? retroProgressHtml : ""}
        <br>`;
    } else if (isVideo(fileUrl, mimeType)) {
      if (videoThumbnail) {
        content += `<br>
          <div class="media-preview-container">
            <div class="video-thumbnail-wrapper">
              <img src="${videoThumbnail}" class="media-preview-image video-thumbnail">
              <span class="video-play-icon">▶</span>
            </div>
            ${isLoading ? squareLoaderHtml : ""}
          </div>
          ${isLoading ? retroProgressHtml : ""}
          <br>`;
      } else {
        content += `<br>
          <div class="media-preview-container">
            <video src="${fileUrl}" class="media-preview-image video-thumbnail video-thumbnail-player" controls preload="metadata"></video>
            ${isLoading ? squareLoaderHtml : ""}
          </div>
          ${isLoading ? retroProgressHtml : ""}
          <br>`;
      }
    } else {
      content += `<div style="position: relative;">
          <a href="${fileUrl}" target="_blank" style="color: inherit;">${text}</a>
          ${isLoading ? retroProgressHtml : ""}
      </div>`;
    }
  } else {
    content += formatMessageText(text);
  }

  bubble.innerHTML = content;

  // Wire image/video preview click safely
  if (fileUrl && (isImage(fileUrl, mimeType) || (isVideo(fileUrl, mimeType) && videoThumbnail))) {
    const previewImg = bubble.querySelector(".media-preview-image");
    if (previewImg) {
      previewImg.addEventListener("click", () => window.open(fileUrl));
    }
  }

  if (options && Array.isArray(options) && options.length > 0) {
    const optionsContainer = document.createElement("div");
    optionsContainer.className = "msg-options";

    options.forEach((opt) => {
      const btn = document.createElement("button");
      btn.className = "option-btn";
      btn.innerText = opt;
      btn.addEventListener("click", () => handleOptionClick(opt));
      optionsContainer.appendChild(btn);
    });

    bubble.appendChild(optionsContainer);
  }

  row.appendChild(bubble);
  const messagesEl = document.getElementById("messages");
  messagesEl.appendChild(row);
  messagesEl.scrollTop = messagesEl.scrollHeight;
}

export function sendText(text) {
  if (!text || !state.ws) return;
  state.ws.send(JSON.stringify({
    type: "text",
    receiver: state.currentReceiver,
    text
  }));
  displayMessage(state.myUsername, text, "sent", null, null, false, null, "", null, Date.now());
}

export function sendMsg() {
  const input = document.getElementById("msg");
  const text = input.value.trim();
  if (!text || !state.ws) return;

  state.ws.send(JSON.stringify({
    type: "text",
    receiver: state.currentReceiver,
    text
  }));

  displayMessage(state.myUsername, text, "sent", null, null, false, null, "", null, Date.now());
  input.value = "";
}

export async function sendFile() {
  const fileInput = document.getElementById("file-input");
  if (!fileInput || fileInput.files.length === 0) return;

  const file = fileInput.files[0];
  const isVideoFile = file.type.startsWith("video/");
  const formData = new FormData();
  formData.append("file", file);

  const tempId = "msg-" + Date.now();
  const localUrl = URL.createObjectURL(file);
  const localThumbnail = isVideoFile ? await createVideoThumbnail(localUrl) : null;

  displayMessage(
    state.myUsername,
    "Uploading " + file.name + "...",
    "sent",
    localUrl,
    tempId,
    true,
    localThumbnail,
    file.type,
    null,
    Date.now()
  );

  const msgElement = document.getElementById(tempId);

  try {
    const data = await new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open("POST", "/upload");

      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable) {
          const percent = Math.round((event.loaded / event.total) * 100);
          updateUploadProgress(msgElement, percent);
        }
      };

      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          try {
            resolve(JSON.parse(xhr.responseText));
          } catch (err) {
            reject(err);
          }
        } else {
          reject(new Error("Upload failed with status: " + xhr.status));
        }
      };

      xhr.onerror = () => reject(new Error("Network upload error"));
      xhr.send(formData);
    });

    if (data.url) {
      if (msgElement) {
        msgElement.querySelector(".msg-bubble")?.classList.remove("loading");
        msgElement.querySelector(".loader-square-container")?.remove();
        msgElement.querySelector(".loader-overlay")?.remove();
        msgElement.querySelector(".retro-progress-wrapper")?.remove();

        const img = msgElement.querySelector("img");
        if (img) {
          img.src = data.url;
          img.onclick = () => window.open(data.url);
        }

        const video = msgElement.querySelector("video");
        if (video) {
          video.src = data.url;
          video.load();
        }

        const link = msgElement.querySelector("a");
        if (link) {
          link.href = data.url;
          link.innerText = "📎 Shared File: " + data.filename;
        }
      }

      state.ws.send(JSON.stringify({
        type: "file",
        receiver: state.currentReceiver,
        url: data.url,
        filename: data.filename
      }));
    } else {
      throw new Error("Upload failed");
    }
  } catch (e) {
    console.error("File upload error:", e);
    if (msgElement) {
      msgElement.querySelector(".msg-bubble")?.classList.remove("loading");
      msgElement.querySelector(".loader-square-container")?.remove();
      msgElement.querySelector(".loader-overlay")?.remove();
      const retroProgress = msgElement.querySelector(".retro-progress-wrapper");
      if (retroProgress) {
        retroProgress.innerHTML = `<span style="color:red; font-size: 11px; font-weight: bold;">❌ Upload Failed</span>`;
      }
    }
  } finally {
    URL.revokeObjectURL(localUrl);
    fileInput.value = "";
  }
}

function handleOptionClick(option) {
  if (!state.ws) return;

  let command = option;
  const commandMap = {
    "🛂 Passport A6 (6 Photos)": "###passport###",
    "🛂 Passport A6 (9 Photos)": "###passport9###",
    "📄 Extract Text (OCR)": "###ocr###",
    "🗜️ Compress Image": "PROMPT_SIZE",
    "📄 Convert to PDF": "###topdf###",
    "🧠 Analyze Image (AI)": "###analyzeimage###",
    "📄 Extract PDF Text": "###pdf2text###",
    "🔗 Merge PDFs": "###mergepdfs###",
    "🗜️ Compress PDF": "###compresspdf###",
    "🎵 Extract Audio (MP3)": "###extractaudio###",
    "🗜️ Compress Video": "###compressvideo:28:mp4###",
    "🔄 Rotate Video": "###rotatevideo:90###",
    "🎞️ Convert to MP4": "###convertmp4###",
    "🎵 YT MP3": "###ytdownload:mp3###",
    "🎬 YT MP4 - 480p": "###ytdownload:480p###",
    "🎬 YT MP4 - 720p": "###ytdownload:720p###",
    "🎬 YT MP4 - Highest": "###ytdownload:best###"
  };

  if (commandMap[option]) {
    command = commandMap[option];
  }

  if (command === "PROMPT_SIZE" || option === "Compress Image") {
    const size = prompt("Enter target size in KB (e.g. 500):");
    if (!size) return;
    command = `###compress<${size}>###`;
  } else if (
    option === "🛂 Master Passport" ||
    option === "Passport + Date" ||
    option === "📅 Passport + Date" ||
    option === "📅 Passport + Date/Name"
  ) {
    const pageInput = prompt("Page Size? (A6/A4)", "A6");
    if (!pageInput) return;
    const page = pageInput.trim().toUpperCase() === "A4" ? "a4" : "a6";

    const defaultLayout = page === "a4" ? "1x6" : "1x3";
    const layoutHelp = page === "a4"
      ? "Layout? (1x6 / 2x6 / 3x6)"
      : "Layout? (1x3 / 2x3 / 3x3)";
    const layoutInput = prompt(layoutHelp, defaultLayout);
    if (!layoutInput) return;
    const layout = layoutInput.trim().toLowerCase();

    const rawName = prompt("Name (optional):", "") || "";
    const safeName = rawName.trim().replace(/[<>]/g, "");

    const dateInput = document.createElement("input");
    dateInput.type = "date";
    dateInput.style.display = "none";
    document.body.appendChild(dateInput);

    const submitPassportCommand = (formattedDate = "") => {
      let finalCmd = `###passport### ###passportpage<${page}>### ###passportlayout<${layout}>###`;
      if (formattedDate) finalCmd += ` ###passportdate<${formattedDate}>###`;
      if (safeName) finalCmd += ` ###passportname<${safeName}>###`;
      sendText(finalCmd);
      dateInput.remove();
    };

    dateInput.onchange = () => {
      const val = dateInput.value;
      if (!val) {
        submitPassportCommand("");
        return;
      }
      const [y, m, d] = val.split("-");
      submitPassportCommand(`${d}/${m}/${y}`);
    };

    if (confirm("Add Date bhi chahiye?")) {
      if (dateInput.showPicker) dateInput.showPicker();
      else dateInput.click();
    } else {
      submitPassportCommand("");
    }
    return;
  }

  if (command.startsWith("###ytdownload:")) {
    displayMessage("System", "Downloading... Please wait ⏳", "received", null, null, false, null, "", null, Date.now());
  }

  sendText(command);
}

let typingTimeout;
export function showTypingIndicator(sender) {
  if (sender !== state.currentReceiver) return;
  const title = document.getElementById("chat-title");
  if (!title) return;

  const originalText = state.currentReceiver;
  title.innerText = sender + " is typing...";

  clearTimeout(typingTimeout);
  typingTimeout = setTimeout(() => {
    if (state.currentReceiver === originalText) {
      title.innerText = originalText;
    }
  }, 3000);
}

export function handleEnter(e) {
  const now = Date.now();
  if (now - state.lastTypingSent > 2000 && state.ws && state.currentReceiver !== "Family Group") {
    state.ws.send(JSON.stringify({ type: "typing", receiver: state.currentReceiver }));
    state.lastTypingSent = now;
  }
  if (e.key === "Enter") sendMsg();
}
