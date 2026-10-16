import sys
import os
import socket
import sqlite3
import subprocess
from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QPushButton, QLabel, QTextEdit, QFrame, QMessageBox,
    QDialog, QFormLayout, QLineEdit, QFileDialog, QDialogButtonBox
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

# Possible database locations (different setups may use different filenames/working dirs)
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.expanduser("~")

# Add intra_admin to path so we can import AdminWindow
INTRA_ADMIN_DIR = os.path.join(REPO_ROOT, "intra_admin")
if INTRA_ADMIN_DIR not in sys.path:
    sys.path.insert(0, INTRA_ADMIN_DIR)
try:
    from admin import AdminWindow
except ImportError:
    AdminWindow = None
DB_CANDIDATES = [
    os.path.join(REPO_ROOT, "lan_server", "chat_users.db"),
    os.path.join(REPO_ROOT, "lan_server", "chat_user.db"),
    os.path.join(REPO_ROOT, "backend", "chat_users.db"),
    os.path.join(REPO_ROOT, "backend", "chat_user.db"),
    os.path.join(REPO_ROOT, "chat_users.db"),
    os.path.join(REPO_ROOT, "chat_user.db"),
    os.path.join(SCRIPT_DIR, "chat_users.db"),
    os.path.join(SCRIPT_DIR, "chat_user.db"),
]

class ServerController(QMainWindow):
    def __init__(self):
        super().__init__()
        self.current_admin_key = ""
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

        # Buttons next to each other
        copy_admin_layout = QHBoxLayout()
        self.copy_key_btn = QPushButton("📋 COPY ADMIN KEY")
        self.copy_key_btn.setObjectName("StartBtn")
        self.copy_key_btn.setCursor(Qt.PointingHandCursor)
        self.copy_key_btn.clicked.connect(self.copy_admin_key)

        self.open_admin_btn = QPushButton("⚙️ OPEN ADMIN PANEL")
        self.open_admin_btn.setStyleSheet("""
            QPushButton { background-color: #cba6f7; color: #11111b; font-weight: bold; font-size: 16px; padding: 12px; border-radius: 8px; }
            QPushButton:hover { background-color: #b4befe; }
            QPushButton:disabled { background-color: #45475a; color: #a6adc8; }
        """)
        self.open_admin_btn.setCursor(Qt.PointingHandCursor)
        self.open_admin_btn.clicked.connect(self.open_admin_panel)

        copy_admin_layout.addWidget(self.copy_key_btn)
        copy_admin_layout.addWidget(self.open_admin_btn)
        layout.addLayout(copy_admin_layout)

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

        # Keep reference to admin window
        self.admin_window = None

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
        last_error = None
        # Priority 1: Absolute path to known DB location
        absolute_db = "/home/htech/lan_server/chat_users.db"
        if os.path.exists(absolute_db):
            try:
                conn = sqlite3.connect(absolute_db)
                cursor = conn.cursor()
                cursor.execute("SELECT value FROM config WHERE key='admin_key'")
                row = cursor.fetchone()
                conn.close()
                if row and row[0]:
                    return row[0]
            except Exception as e:
                last_error = str(e)

        # Priority 2: Candidates
        for db_path in DB_CANDIDATES:
            if not os.path.exists(db_path):
                continue
            try:
                conn = sqlite3.connect(db_path)
                cursor = conn.cursor()
                cursor.execute("SELECT value FROM config WHERE key='admin_key'")
                row = cursor.fetchone()
                conn.close()
                if row and row[0]:
                    return row[0]
            except Exception as e:
                last_error = str(e)

        if last_error:
            return f"Error: {last_error}"
        return "Not Generated Yet"

    def update_info_box(self, status):
        ip = self.get_local_ip()
        password = self.get_admin_password()
        self.current_admin_key = password if password not in ("Not Generated Yet", "Database not found") and not password.startswith("Error:") else ""
        status_color = "#a6e3a1" if status == "Running 🟢" else "#f38ba8"
        self.copy_key_btn.setEnabled(bool(self.current_admin_key))

        info_html = f"""
        <table width="100%">
            <tr><td width="150"><b>Service Status:</b></td><td><span style='color: {status_color};'>{status}</span></td></tr>
            <tr><td><b>Android App IP:</b></td><td><span style='color: #89b4fa;'>http://{ip}:8000</span></td></tr>
            <tr><td><b>Admin Key:</b></td><td><span style='color: #f9e2af;'>{password}</span></td></tr>
        </table>
        """
        self.info_box.setText(info_html)

    def copy_admin_key(self):
        if not self.current_admin_key:
            self.console.append("<span style='color: #f38ba8;'>[INFO] Admin key abhi available nahi hai.</span>")
            return
        QApplication.clipboard().setText(self.current_admin_key)
        self.console.append("<span style='color: #a6e3a1;'>[INFO] Admin key clipboard me copy ho gaya.</span>")

    def open_admin_panel(self):
        if AdminWindow is None:
            self.console.append("<span style='color: #f38ba8;'>[ERROR] AdminWindow could not be imported from intra_admin.</span>")
            return

        url = "http://127.0.0.1:8000"

        # 1. Agar DB pehle se detect hai aur admin key pata hai -> Direct open!
        if self.current_admin_key:
            try:
                self.admin_window = AdminWindow(url, self.current_admin_key)
                self.admin_window.show()
                self.console.append(f"<span style='color: #a6e3a1;'>[INFO] Admin Panel direct khul gaya: {url} ✅</span>")
            except Exception as e:
                self.console.append(f"<span style='color: #f38ba8;'>[ERROR] Failed to open Admin Panel: {str(e)}</span>")
            return

        # 2. Agar DB detect nahi hui ya key nahi mili -> Tabhi Popup aayega
        dialog = AdminUnlockDialog(self)
        if dialog.exec() != QDialog.Accepted:
            return

        entered_code = dialog.get_code()
        custom_db   = dialog.get_db_path()

        real_key = None
        if custom_db and os.path.exists(custom_db):
            real_key = self._read_key_from_db(custom_db)
            if real_key:
                self.current_admin_key = real_key
                self.console.append(f"<span style='color: #a6e3a1;'>[INFO] Custom DB se key load ho gayi: {custom_db}</span>")
        elif entered_code:
            real_key = entered_code

        # Key nahi mili
        if not real_key:
            QMessageBox.warning(self, "Key Not Found", "❌ Admin key nahi mili. Sahi DB path select karein ya key daalein.")
            self.console.append("<span style='color: #f38ba8;'>[ERROR] Admin key nahi mili.</span>")
            return

        if entered_code and custom_db and entered_code != real_key:
            QMessageBox.warning(self, "Access Denied", "❌ Enter kiya hua code aur DB ki key match nahi hui.")
            self.console.append("<span style='color: #f38ba8;'>[ERROR] Admin Panel: code mismatch with DB key.</span>")
            return

        try:
            self.admin_window = AdminWindow(url, real_key)
            self.admin_window.show()
            self.console.append(f"<span style='color: #a6e3a1;'>[INFO] Admin Panel opened with URL: {url} ✅</span>")
        except Exception as e:
            self.console.append(f"<span style='color: #f38ba8;'>[ERROR] Failed to open Admin Panel: {str(e)}</span>")

    def _read_key_from_db(self, db_path):
        """Kisi bhi DB path se admin_key padhta hai"""
        try:
            conn = sqlite3.connect(db_path)
            cursor = conn.cursor()
            cursor.execute("SELECT value FROM config WHERE key='admin_key'")
            row = cursor.fetchone()
            conn.close()
            return row[0] if row and row[0] else None
        except Exception as e:
            self.console.append(f"<span style='color: #f38ba8;'>[ERROR] DB read error: {e}</span>")
            return None

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

# ================= ADMIN UNLOCK DIALOG =================
class AdminUnlockDialog(QDialog):
    """DB detect na hone par DB browse karne ya key enter karne ka dialog"""
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("📂 DB Path & Admin Key")
        self.setFixedSize(400, 220)
        self.setStyleSheet("""
            QDialog { background-color: #1e1e2e; color: #cdd6f4; }
            QLabel  { color: #cdd6f4; font-size: 13px; }
            QLineEdit {
                background: #181825; color: #cdd6f4;
                border: 1px solid #313244; border-radius: 6px;
                padding: 6px; font-size: 13px;
            }
            QPushButton {
                background-color: #cba6f7; color: #11111b;
                font-weight: bold; padding: 8px 20px;
                border-radius: 6px; font-size: 13px;
            }
            QPushButton:hover { background-color: #b4befe; }
        """)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(10)

        # Title
        title = QLabel("📂  Database & Admin Key")
        title.setStyleSheet("font-size: 16px; font-weight: bold; color: #cba6f7;")
        layout.addWidget(title)

        desc = QLabel("Database auto-detect nahi hui. DB file browse karein ya key daalein:")
        desc.setStyleSheet("font-size: 12px; color: #a6adc8;")
        desc.setWordWrap(True)
        layout.addWidget(desc)

        form = QFormLayout()
        form.setSpacing(10)

        # DB path row
        db_row = QHBoxLayout()
        self.db_input = QLineEdit()
        self.db_input.setPlaceholderText("Select chat_users.db file...")
        browse_btn = QPushButton("📂")
        browse_btn.setFixedWidth(36)
        browse_btn.setStyleSheet("padding: 6px; font-size: 14px;")
        browse_btn.clicked.connect(self._browse_db)
        db_row.addWidget(self.db_input)
        db_row.addWidget(browse_btn)
        form.addRow("DB File:", db_row)

        # Code input
        self.code_input = QLineEdit()
        self.code_input.setEchoMode(QLineEdit.Password)
        self.code_input.setPlaceholderText("Admin key daalo (optional)")
        form.addRow("Admin Key:", self.code_input)

        layout.addLayout(form)

        # Buttons
        btn_box = QDialogButtonBox()
        unlock_btn = btn_box.addButton("Open Admin", QDialogButtonBox.AcceptRole)
        cancel_btn = btn_box.addButton("Cancel", QDialogButtonBox.RejectRole)
        btn_box.accepted.connect(self.accept)
        btn_box.rejected.connect(self.reject)
        layout.addWidget(btn_box)

        # Enter key se bhi unlock ho
        self.code_input.returnPressed.connect(self.accept)
        self.db_input.returnPressed.connect(self.accept)

    def _browse_db(self):
        path, _ = QFileDialog.getOpenFileName(
            self, "Select DB File", "", "SQLite DB (*.db);;All Files (*)"
        )
        if path:
            self.db_input.setText(path)

    def get_code(self):
        return self.code_input.text().strip()

    def get_db_path(self):
        return self.db_input.text().strip()


if __name__ == "__main__":
    app = QApplication(sys.argv)
    window = ServerController()
    window.show()
    sys.exit(app.exec())

