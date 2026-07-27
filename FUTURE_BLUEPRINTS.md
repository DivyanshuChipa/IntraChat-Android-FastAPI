# Future UI/UX Ideas & Blueprints

This document serves as a repository for visionary ideas and future feature plans for the application.

## ContactsScreen Top Bar Redesign (The "Hub" Concept) [COMPLETED]

**Concept:**
Replace the traditional and boring 3-dot settings menu in the `ContactsScreen` top bar with a highly interactive, futuristic "Hub" or "Grid" (Dice-like) icon. (Successfully implemented with slide/fade animations, Settings, SMB Network Browser, and Smart Home links).

## SMB Network Browser [COMPLETED]

**Concept:**
Integrate local network storage (Samba/SMB) sharing directly inside the app, allowing users to scan local IPs, connect (guest/registered), navigate folders, create directories, upload, and download files directly from/to their devices. (Successfully implemented using `jcifs-ng` with network scanning, FAB menu options, image/video local preview dialogs, and cache management).
- **Scalability:** It leaves room for integrating powerful features like IoT and Local Network management directly from the main hub of the app.

## Lumir Addon System (Backend/Bot Utilities)

**Concept:**
Instead of hardcoding every utility into Lumir, introduce a modular "Addon" system. Users/Admins can enable specific modules to give Lumir new capabilities. This keeps the core lightweight and adds immense value.

**Top Priority Addon Ideas:**

### 1. YouTube Downloader Addon (`/yt`) [COMPLETED]
- **How it works:** A user sends a YouTube link with a command like `/yt [link]`.
- **Backend Logic:** Lumir triggers an addon utilizing tools like `yt-dlp` to download the video or extract the audio.
- **Delivery:** Once downloaded, Lumir sends the file directly in the chat or saves it in the custom local download folder. (Successfully implemented and integrated on the backend!)
- **Why it's great:** Highly requested utility, saves users from visiting ad-ridden downloader websites.

### 2. Personal Financial Tracker Addon (`/money`)
- **How it works:** Users log expenses or income via natural language or simple commands. E.g., `/money -150 tea and snacks` or `/money +5000 freelance work`.
- **Backend Logic:** Lumir parses the amount and category. It saves this data into an isolated SQLite table (or exports to CSV).
- **Reporting:** Sending `/money report` triggers Lumir to summarize the month's spending, perhaps even generating a small chart or a neat tabular summary in the chat.
- **Future Expansion:** Smart OCR where users send a photo of a receipt, and Lumir automatically extracts the total and asks, "Add $15 to expenses?"

### 3. Tier 2 Memory Summary System (ChromaDB Optimization)
- **Current State:** Lumir uses ChromaDB to recall past facts.
- **The Problem:** Over time, exact chat logs get messy and context limits are hit.
- **The Upgrade:** Introduce a background worker (Summarizer). It periodically reads older chats, condenses them into solid, summarized facts (e.g., "User bought a new bike in Jan 2024"), and updates ChromaDB.
- **Benefit:** Gives Lumir a much sharper long-term memory without overloading the LLM's prompt window with raw, unstructured past conversations.
