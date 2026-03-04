# 🚀 Intra Backend - Setup Guide (Windows & Linux)

Bhai, ye guide unke liye hai jo backend server setup karna chahte hain. Isse aapka **Intra App** poore ghar ya office ke Wi-Fi (LAN) par kaam karega.

---

## 🛠️ Sabse Pehle Kya Chahiye? (Pre-requisites)

1. **Python 3.x:** Agar nahi hai, toh [python.org](https://www.python.org/downloads/) se download kar lo.
2. **Wi-Fi/LAN:** Aapka mobile aur laptop same Wi-Fi par hona chahiye.

---

## 💻 Windows Setup (Sabse Aasaan)

Agar aap Windows computer use kar rahe hain:

1.  Root folder mein **`Start_Server.bat`** file par **Double-Click** karo.
2.  Ek kaali screen (Terminal) khulegi. Agar libraries missing hain, toh ye apne aap install kar degi.
3.  Screen par ek IP address dikhega (Jaise: `192.168.1.10`). **Usko note kar lo!**

---

## 🐧 Linux Setup (Doston ke liye)

### Option A: Manual Start (Jab tak terminal khula hai)
1.  Root folder mein Terminal kholo.
2.  File ko execution permission do (Sirf pehli baar):
    ```bash
    chmod +x start_server.sh
    ```
3.  Server start karo:
    ```bash
    ./start_server.sh
    ```

### Option B: Systemctl Setup (Background Service)
Agar aap chahte hain ki PC chalu hote hi server apne aap start ho jaye:
1.  Terminal kholo aur chalao:
    ```bash
    sudo ./setup_systemd.sh
    ```
2.  Ab aapka server hamesha background mein chalta rahega.

---

## 📱 Android App Me Kaise Connect Karein?

1.  Apne phone mein **Intra App** open karein.
2.  Login ya Connection screen par aapse **Server IP** pucha jayega.
3.  Wahan wahi IP daalein jo computer ki screen par dikh raha hai (e.g., `192.168.1.10`).
4.  **Connect** par click karein aur CHAT chalu! 🎉

---

## ⚠️ Troubleshooting
*   **Firewall:** Agar connection nahi ho raha, toh Port 8000 allow karein.
*   **Same Wi-Fi:** Phone aur Laptop ek hi network par hona chahiye.

---
**Made with ❤️ for Intra Users**
