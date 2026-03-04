# 🚀 Intra Backend - Beginner Guide (Windows + Linux)

Ye guide bilkul beginners ke liye hai. Isme aapko step-by-step milega:
- virtual environment banega,
- dependencies install hongi,
- backend start hoga,
- Linux me systemd service install/uninstall,
- Windows me background me chalana + band karna.

---

## 📦 Required cheezein

1. **Python 3.x** installed ho (Windows ya Linux).
2. Phone + computer **same Wi-Fi/LAN** pe hon.
3. Repo ka folder open ho: `IntraChat-Android-FastAPI`.

---

## 🪟 Windows (easy mode)

### 1) Normal start
Root folder me `Start_Server.bat` par double-click karo.

Ye script automatically karegi:
- Python check,
- `backend/venv` create (agar nahi hai),
- `requirements.txt` se install/update,
- server run.

### 2) Background me start
CMD me root folder khol ke run karo:
```bat
Start_Server.bat --background
```
Isse server ek minimized background window me start ho jayega.

### 3) Stop kaise karein
- **Task Manager** open karo,
- `python.exe` process dhundo (jo backend run kar raha ho),
- **End Task** karo.

> Tip: Agar doubt ho, pehle app me connection check kar lo, phir wrong process mat kill karo.

---

## 🐧 Linux

### Option A: Manual start (terminal based)

```bash
chmod +x start_server.sh
./start_server.sh
```

Ye bhi Windows script jaisa hi kaam karta hai:
- venv create,
- dependencies install/update,
- backend run.

### Option B: systemd service (auto-start on boot)

#### Install service
```bash
chmod +x setup_systemd.sh
sudo ./setup_systemd.sh install
```

Useful commands:
```bash
sudo systemctl status intra_backend.service
sudo systemctl restart intra_backend.service
sudo journalctl -u intra_backend.service -f
```

#### Uninstall service (important)
Agar service hatani ho:
```bash
sudo ./setup_systemd.sh uninstall
```
Ye command:
- service stop karegi,
- disable karegi,
- service file remove karegi,
- daemon reload karegi.

---

## 📱 Android App me IP kaise daalein

1. Backend run hone ke baad terminal me IP dikhega (example: `192.168.1.10`).
2. Intra Android app open karo.
3. Server IP field me woh IP enter karo.
4. Connect karo.

---

## 🛠 Troubleshooting (common problems)

- **Connection fail:** Firewall me port `8000` allow karo.
- **Server nahi chal raha:** script dubara run karo aur errors check karo.
- **pip issue:** internet on rakho, fir script rerun karo.
- **Wrong IP:** ensure phone/computer same Wi-Fi pe ho.

---

## ✅ Quick command cheat sheet

### Linux
```bash
./start_server.sh
sudo ./setup_systemd.sh install
sudo ./setup_systemd.sh uninstall
```

### Windows
```bat
Start_Server.bat
Start_Server.bat --background
```

---
Made with ❤️ for Intra users (especially beginners).
