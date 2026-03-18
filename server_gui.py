import sys
import os
import socket
import sqlite3
import subprocess
from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QPushButton, QLabel, QTextEdit, QFrame
)
from PySide6.QtCore import QProcess, Qt, QTimer

# ================= MODERN DARK THEME =================
STYLESHEET = """
QMainWindow { background-color: #11111b; }
QWidget { color: #cdd6f4; font-family: 'Segoe UI', sans-serif; }
QLabel#Title { font-size: 26px; font-weight: bold; color: #cba6f7; }
QLabel#Subtitle { font-size: 14px; color: #a6adc8; }
QLabel#InfoBox { background-color: #1e1e2e; padding: 15px; border-radius: 8px; border: 1px solid #313244; font-size: 15px; }

QPushButton#StartBtn { background-color: #a6e3a1; color: #11111b; font-weight: bold; font-size: 16px; padding: 12px; border-radius: 8px; }
QPushButton#StartBtn:hover { background-color: #94e28f; }
QPushButton#StartBtn:disabled { background-color: #45475a; color: #a6adc8; }

QPushButton#StopBtn { background-color: #f38ba8; color: #11111b; font-weight: bold; font-size: 16px; padding: 12px; border-radius: 8px; }
QPushButton#StopBtn:hover { background-color: #eba0ac; }
QPushButton#StopBtn:disabled { background-color: #45475a; color: #a6adc8; }

QTextEdit { background-color: #181825; color: #a6adc8; border: 1px solid #313244; border-radius: 8px; padding: 10px; font-family: 'Consolas', monospace; }
"""

# Path to the backend database
DB_PATH = os.path.join(os.path.dirname(__file__), "backend", "chat_users.db")

class ServerController(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Intra Service Monitor 🚜 (Systemd)")
        self.resize(800, 550)
        self.setStyleSheet(STYLESHEET)

        # Main Layout
        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        layout = QVBoxLayout(central_widget)
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(15)

        # 1. Header
        header_layout = QVBoxLayout()
        title = QLabel("Intra Systemd Manager")
        title.setObjectName("Title")
        subtitle = QLabel("Live monitoring & control for 'lanserver' background service.")
        subtitle.setObjectName("Subtitle")
        header_layout.addWidget(title)
        header_layout.addWidget(subtitle)
        layout.addLayout(header_layout)

        # 2. Info Box (IP & Password)
        self.info_box = QLabel()
        self.info_box.setObjectName("InfoBox")
        self.info_box.setTextInteractionFlags(Qt.TextSelectableByMouse)
        layout.addWidget(self.info_box)

        # 3. Buttons
        btn_layout = QHBoxLayout()
        self.start_btn = QPushButton("▶ START SERVICE")
        self.start_btn.setObjectName("StartBtn")
        self.start_btn.setCursor(Qt.PointingHandCursor)
        self.start_btn.clicked.connect(self.start_service)

        self.stop_btn = QPushButton("🛑 STOP SERVICE")
        self.stop_btn.setObjectName("StopBtn")
        self.stop_btn.setCursor(Qt.PointingHandCursor)
        self.stop_btn.clicked.connect(self.stop_service)

        btn_layout.addWidget(self.start_btn)
        btn_layout.addWidget(self.stop_btn)
        layout.addLayout(btn_layout)

        # 4. Console Log Output
        log_label = QLabel("Live Journalctl Logs:")
        log_label.setStyleSheet("font-weight: bold; color: #fab387; margin-top: 10px;")
        layout.addWidget(log_label)

        self.console = QTextEdit()
        self.console.setReadOnly(True)
        layout.addWidget(self.console)

        # ================= ENGINE ROOM =================
        # A. Setup QProcess to read live logs from systemd
        self.log_process = QProcess(self)
        self.log_process.readyReadStandardOutput.connect(self.handle_log_output)
        self.start_log_stream()

        # B. Setup Timer to check service status every 2 seconds
        self.status_timer = QTimer(self)
        self.status_timer.timeout.connect(self.check_service_status)
        self.status_timer.start(2000)

        # Initial trigger
        self.check_service_status()

    def get_local_ip(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.connect(('8.8.8.8', 80))
            IP = s.getsockname()[0]
        except Exception:
            IP = '127.0.0.1'
        finally:
            s.close()
        return IP

    def get_admin_password(self):
        try:
            if not os.path.exists(DB_PATH):
                return "Database not found"
            conn = sqlite3.connect(DB_PATH)
            cursor = conn.cursor()
            cursor.execute("SELECT value FROM config WHERE key='admin_key'")
            row = cursor.fetchone()
            conn.close()
            return row[0] if row else "Not Generated Yet"
        except Exception as e:
            return f"Error: {str(e)}"

    def update_info_box(self, status):
        ip = self.get_local_ip()
        password = self.get_admin_password()
        status_color = "#a6e3a1" if status == "Running 🟢" else "#f38ba8"

        info_html = f"""
        <table width="100%">
            <tr><td width="150"><b>Service Status:</b></td><td><span style='color: {status_color};'>{status}</span></td></tr>
            <tr><td><b>Android App IP:</b></td><td><span style='color: #89b4fa;'>http://{ip}:8000</span></td></tr>
            <tr><td><b>Admin Password:</b></td><td><span style='color: #f9e2af;'>{password}</span></td></tr>
        </table>
        """
        self.info_box.setText(info_html)

    def check_service_status(self):
        """Runs 'systemctl is-active lanserver' silently to check status"""
        try:
            result = subprocess.run(["systemctl", "is-active", "lanserver"], capture_output=True, text=True)
            is_running = (result.stdout.strip() == "active")

            if is_running:
                self.start_btn.setEnabled(False)
                self.stop_btn.setEnabled(True)
                self.update_info_box("Running 🟢")
            else:
                self.start_btn.setEnabled(True)
                self.stop_btn.setEnabled(False)
                self.update_info_box("Stopped 🔴")
        except Exception as e:
            self.update_info_box("Error checking status ⚠️")

    def start_service(self):
        self.console.append("<span style='color: #a6e3a1;'>[SYSTEM] Requesting systemctl to START lanserver...</span>")
        # subprocess.Popen sends the command to OS. Linux will automatically ask for password GUI popup if needed.
        subprocess.Popen(["systemctl", "start", "lanserver"])

    def stop_service(self):
        self.console.append("<span style='color: #f38ba8;'>[SYSTEM] Requesting systemctl to STOP lanserver...</span>")
        subprocess.Popen(["systemctl", "stop", "lanserver"])

    def start_log_stream(self):
        """Tails the journalctl logs live"""
        if self.log_process.state() == QProcess.Running:
            self.log_process.kill()

        # -u: unit name, -f: follow live, -n 50: last 50 lines, -o cat: clean output without timestamps
        self.log_process.start("journalctl", ["-u", "lanserver", "-f", "-n", "50", "-o", "cat"])

    def handle_log_output(self):
        """Reads live logs from journalctl and adds to GUI"""
        data = self.log_process.readAllStandardOutput().data().decode('utf-8')
        if data.strip():
            self.console.insertPlainText(data)
            self.console.ensureCursorVisible()

if __name__ == "__main__":
    app = QApplication(sys.argv)
    window = ServerController()
    window.show()
    sys.exit(app.exec())
