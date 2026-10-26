# 📺 IntraPlay: Upcoming Features & Roadmap

This document outlines the phased expansion of **IntraPlay** from a basic Android TV IPTV Hub into the ultimate Living Room Local Media Hub.

---

## 🎯 Phase 1: Android TV Core & IPTV Hub [CURRENT PHASE]
- [x] Dedicated Android TV module (`:intra_play`) with Leanback launcher intent.
- [x] 16:9 Landscape Layout tailored for TV screens.
- [x] D-Pad Remote Navigation with animated scale (1.08x) and glow focus highlights.
- [x] Free-to-Air HLS Live IPTV player via Google Media3 / ExoPlayer.
- [x] Fast category filtering (News, Music, Sports, Entertainment) and channel switching.

---

## 🚀 Phase 2: Inbuilt SMB & IntraDrive 4K LAN Cinema
- **Direct PC/NAS Streaming:** Browse SMB/Samba folders on home PCs, Linux Mint server, or phone IntraDrive nodes using `jcifs-ng`.
- **Zero Buffering / Zero Internet:** Stream 1080p and 4K MKV/MP4 files over 5GHz local Wi-Fi with instant seeking.
- **Subtitle & Multi-Audio:** Auto-detection of `.srt` subtitles and audio track selection via remote.
- **Resume Watching:** Persist playback progress in local room database.

---

## 📲 Phase 3: Local 1-Tap "Cast to TV" (Phone ➔ TV Bridge)
- **Local Casting Receiver:** IntraPlay listens on port `8008` (discoverable on LAN).
- **Phone Trigger:** IntraChat Android app shows a "Cast to TV 📺" icon when viewing media or WebDrop files.
- **Instant Playback:** Tapping cast immediately routes video, photo, or music to the big screen.

---

## 🔔 Phase 4: Smart Living Room Caller ID HUD
- **Non-Intrusive Overlay:** When an incoming call or urgent announcement occurs on IntraChat, a translucent heads-up notification pill slides into the top-right corner of the TV screen without interrupting playback.
- **Callers & Alerts:** Displays caller name (e.g., *"📞 Incoming Call from Rahul"* or *"💬 Mummy: Dinner is ready"*).

---

## 🖼️ Phase 5: Ambient Photo Frame & Living Room Jukebox
- **Ambient Screen Saver:** Automatically displays high-resolution photos and family memories from shared LAN folders when idle for 5 minutes.
- **Background Music:** Plays MP3/FLAC playlists in the background while ambient photos transition with subtle Ken Burns zoom animations.
