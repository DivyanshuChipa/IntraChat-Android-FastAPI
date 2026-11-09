import sys
import os
import socket
import sqlite3
import json
from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QPushButton, QLabel, QTextEdit, QMessageBox,
    QDialog, QFormLayout, QLineEdit, QFileDialog, QDialogButtonBox,
    QProgressBar, QScrollArea, QFrame
)
from PySide6.QtCore import QProcess, Qt, QTimer, QThread, Signal
from PySide6.QtGui import QIcon

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

QPushButton#FixDBBtn { background-color: #fab387; color: #11111b; font-weight: bold; font-size: 14px; padding: 8px; border-radius: 8px; }
QPushButton#FixDBBtn:hover { background-color: #fcc6a1; }

QPushButton#CapabilityBtn { background-color: #89b4fa; color: #11111b; font-weight: bold; font-size: 14px; padding: 12px; border-radius: 8px; }
QPushButton#CapabilityBtn:hover { background-color: #b4befe; }

QTextEdit { background-color: #181825; color: #a6adc8; border: 1px solid #313244; border-radius: 8px; padding: 10px; font-family: 'Consolas', monospace; }
"""

# ================= PATHS =================
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)
BACKEND_DIR = os.path.join(REPO_ROOT, "backend")
CONFIG_FILE = os.path.join(os.path.expanduser("~"), ".intra_server_config.json")

# Ensure capability manager & admin modules can be imported
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)
INTRA_ADMIN_DIR = os.path.join(REPO_ROOT, "intra_admin")
if INTRA_ADMIN_DIR not in sys.path:
    sys.path.insert(0, INTRA_ADMIN_DIR)

try:
    from capability_manager import (
        CAPABILITIES,
        detect_capability,
        download_and_install_capability,
        uninstall_local_capability,
        inject_tools_to_path,
        get_intra_tools_dir
    )
except ImportError:
    CAPABILITIES = {}
    detect_capability = None
    download_and_install_capability = None
    uninstall_local_capability = None
    inject_tools_to_path = lambda: os.environ.get("PATH", "")
    get_intra_tools_dir = lambda: os.path.expanduser("~")

try:
    from admin import AdminWindow
except ImportError:
    AdminWindow = None

# DB candidates
DB_CANDIDATES = [
    os.path.join(REPO_ROOT, "backend", "chat_users.db"),
    os.path.join(REPO_ROOT, "backend", "chat_user.db"),
    os.path.join(REPO_ROOT, "chat_users.db"),
    os.path.join(REPO_ROOT, "chat_user.db"),
    os.path.join(SCRIPT_DIR, "chat_users.db"),
    os.path.join(SCRIPT_DIR, "chat_user.db"),
]


# ================= DOWNLOAD WORKER THREAD =================
class CapabilityDownloadWorker(QThread):
    progress = Signal(int, str)
    finished_task = Signal(bool, str)

    def __init__(self, tool_key: str):
        super().__init__()
        self.tool_key = tool_key

    def run(self):
        if not download_and_install_capability:
            self.finished_task.emit(False, "Capability manager module not found.")
            return

        def _report(pct, msg):
            self.progress.emit(pct, msg)

        res = download_and_install_capability(self.tool_key, progress_callback=_report)
        self.finished_task.emit(res.get("success", False), res.get("message", ""))


# ================= CAPABILITY MANAGER DIALOG =================
class CapabilitiesDialog(QDialog):
    """Modular capabilities hub to view, download, install and uninstall external tools."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("🧩 Intra Capabilities & Addons Hub")
        self.setMinimumSize(640, 520)
        self.setStyleSheet("""
            QDialog { background-color: #181825; color: #cdd6f4; }
            QLabel  { color: #cdd6f4; font-size: 13px; }
            QScrollArea { border: none; background: transparent; }
            QFrame.CapabilityCard {
                background-color: #1e1e2e;
                border: 1px solid #313244;
                border-radius: 10px;
                padding: 12px;
            }
            QPushButton.InstallBtn {
                background-color: #a6e3a1; color: #11111b;
                font-weight: bold; padding: 8px 14px;
                border-radius: 6px; font-size: 13px;
            }
            QPushButton.InstallBtn:hover { background-color: #94e28f; }
            QPushButton.UninstallBtn {
                background-color: #f38ba8; color: #11111b;
                font-weight: bold; padding: 8px 14px;
                border-radius: 6px; font-size: 13px;
            }
            QPushButton.UninstallBtn:hover { background-color: #eba0ac; }
            QProgressBar {
                border: 1px solid #313244;
                border-radius: 4px;
                text-align: center;
                background-color: #11111b;
                color: #cdd6f4;
            }
            QProgressBar::chunk {
                background-color: #89b4fa;
            }
        """)

        self.cards = {}
        self.active_workers = {}

        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(14)

        # Header
        title = QLabel("🧩 Modular Addons & Capabilities")
        title.setStyleSheet("font-size: 20px; font-weight: bold; color: #cba6f7;")
        layout.addWidget(title)

        desc = QLabel(
            "Manage modular tools (FFmpeg, Tesseract OCR, yt-dlp) on-demand.\n"
            "Pre-existing system tools are automatically detected. Downloaded tools install to Intra's local directory."
        )
        desc.setStyleSheet("font-size: 13px; color: #a6adc8;")
        desc.setWordWrap(True)
        layout.addWidget(desc)

        # Scroll area with cards
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll_content = QWidget()
        self.scroll_layout = QVBoxLayout(scroll_content)
        self.scroll_layout.setSpacing(12)

        for tool_key, info in CAPABILITIES.items():
            card = self._create_card(tool_key, info)
            self.cards[tool_key] = card
            self.scroll_layout.addWidget(card["frame"])

        self.scroll_layout.addStretch()
        scroll.setWidget(scroll_content)
        layout.addWidget(scroll)

        # Close button
        btn_box = QDialogButtonBox()
        close_btn = btn_box.addButton("Close", QDialogButtonBox.RejectRole)
        close_btn.setStyleSheet("""
            QPushButton {
                background-color: #45475a; color: #cdd6f4;
                font-weight: bold; padding: 8px 20px;
                border-radius: 6px; font-size: 13px;
            }
            QPushButton:hover { background-color: #585b70; }
        """)
        btn_box.rejected.connect(self.accept)
        layout.addWidget(btn_box)

        # Initial refresh
        self.refresh_all_statuses()

    def _create_card(self, tool_key: str, info: dict) -> dict:
        frame = QFrame()
        frame.setProperty("class", "CapabilityCard")
        frame.setObjectName(f"Card_{tool_key}")

        v_layout = QVBoxLayout(frame)
        v_layout.setSpacing(8)

        # Top row: Title and Status Badge
        top_row = QHBoxLayout()
        title_label = QLabel(info.get("title", tool_key))
        title_label.setStyleSheet("font-size: 15px; font-weight: bold; color: #fab387;")

        status_badge = QLabel("Checking...")
        status_badge.setStyleSheet("font-size: 13px; font-weight: bold;")
        top_row.addWidget(title_label)
        top_row.addStretch()
        top_row.addWidget(status_badge)
        v_layout.addLayout(top_row)

        # Description
        desc_label = QLabel(info.get("description", ""))
        desc_label.setStyleSheet("font-size: 12px; color: #a6adc8;")
        desc_label.setWordWrap(True)
        v_layout.addWidget(desc_label)

        # Path label
        path_label = QLabel("")
        path_label.setStyleSheet("font-size: 11px; color: #585b70; font-family: monospace;")
        path_label.setTextInteractionFlags(Qt.TextSelectableByMouse)
        v_layout.addWidget(path_label)

        # Progress bar
        pbar = QProgressBar()
        pbar.setRange(0, 100)
        pbar.setValue(0)
        pbar.setVisible(False)
        v_layout.addWidget(pbar)

        # Action Buttons
        btn_row = QHBoxLayout()
        install_btn = QPushButton("📥 Download & Install")
        install_btn.setProperty("class", "InstallBtn")
        install_btn.setCursor(Qt.PointingHandCursor)
        install_btn.clicked.connect(lambda: self.start_install(tool_key))

        uninstall_btn = QPushButton("🗑️ Uninstall")
        uninstall_btn.setProperty("class", "UninstallBtn")
        uninstall_btn.setCursor(Qt.PointingHandCursor)
        uninstall_btn.clicked.connect(lambda: self.start_uninstall(tool_key))

        btn_row.addWidget(install_btn)
        btn_row.addWidget(uninstall_btn)
        btn_row.addStretch()
        v_layout.addLayout(btn_row)

        return {
            "frame": frame,
            "status_badge": status_badge,
            "path_label": path_label,
            "pbar": pbar,
            "install_btn": install_btn,
            "uninstall_btn": uninstall_btn
        }

    def refresh_all_statuses(self):
        if not detect_capability:
            return
        for tool_key in CAPABILITIES:
            self.refresh_tool_status(tool_key)

    def refresh_tool_status(self, tool_key: str):
        card = self.cards.get(tool_key)
        if not card or not detect_capability:
            return

        detection = detect_capability(tool_key)
        installed = detection.get("installed", False)
        source = detection.get("source", "none")
        path = detection.get("path")
        can_uninstall = detection.get("can_uninstall", False)

        if installed:
            if source == "system":
                card["status_badge"].setText("🟢 Installed (System)")
                card["status_badge"].setStyleSheet("color: #a6e3a1; font-weight: bold;")
                card["install_btn"].setEnabled(False)
                card["uninstall_btn"].setEnabled(False)
                card["uninstall_btn"].setToolTip("Cannot uninstall system-wide software via Intra.")
            else:
                card["status_badge"].setText("🟢 Installed (Intra Local)")
                card["status_badge"].setStyleSheet("color: #a6e3a1; font-weight: bold;")
                card["install_btn"].setEnabled(False)
                card["uninstall_btn"].setEnabled(True)
                card["uninstall_btn"].setToolTip("Remove local tool from %LOCALAPPDATA%/IntraServer/bin")
            card["path_label"].setText(f"Path: {path}")
            card["path_label"].setVisible(True)
        else:
            card["status_badge"].setText("🔴 Not Installed")
            card["status_badge"].setStyleSheet("color: #f38ba8; font-weight: bold;")
            card["install_btn"].setEnabled(True)
            card["uninstall_btn"].setEnabled(False)
            card["path_label"].setVisible(False)

    def start_install(self, tool_key: str):
        card = self.cards.get(tool_key)
        if not card:
            return

        card["install_btn"].setEnabled(False)
        card["pbar"].setVisible(True)
        card["pbar"].setValue(0)
        card["status_badge"].setText("⏳ Downloading...")
        card["status_badge"].setStyleSheet("color: #89b4fa; font-weight: bold;")

        worker = CapabilityDownloadWorker(tool_key)
        worker.progress.connect(lambda pct, msg: self._on_download_progress(tool_key, pct, msg))
        worker.finished_task.connect(lambda ok, msg: self._on_download_finished(tool_key, ok, msg))
        self.active_workers[tool_key] = worker
        worker.start()

    def _on_download_progress(self, tool_key: str, pct: int, msg: str):
        card = self.cards.get(tool_key)
        if card:
            card["pbar"].setValue(pct)
            card["status_badge"].setText(f"⏳ {pct}%")

    def _on_download_finished(self, tool_key: str, success: bool, msg: str):
        card = self.cards.get(tool_key)
        if card:
            card["pbar"].setVisible(False)

        if success:
            QMessageBox.information(self, "Success", f"Capability '{tool_key}' installed successfully!")
        else:
            QMessageBox.critical(self, "Installation Failed", f"Failed to install '{tool_key}':\n{msg}")

        self.refresh_tool_status(tool_key)
        if tool_key in self.active_workers:
            del self.active_workers[tool_key]

    def start_uninstall(self, tool_key: str):
        reply = QMessageBox.question(
            self,
            "Confirm Uninstall",
            f"Are you sure you want to uninstall '{tool_key}' from Intra local storage?",
            QMessageBox.Yes | QMessageBox.No
        )
        if reply == QMessageBox.Yes and uninstall_local_capability:
            uninstall_local_capability(tool_key)
            QMessageBox.information(self, "Removed", f"'{tool_key}' uninstalled successfully from Intra tools.")
            self.refresh_tool_status(tool_key)


class ServerController(QMainWindow):
    def __init__(self):
        super().__init__()
        self.current_admin_key = ""
        self.server_process = None  # QProcess

        # Ensure tools directory is in PATH for any backend launches
        inject_tools_to_path()

        self.setWindowTitle("Intra Service Monitor 🖥️ (Windows)")
        self.resize(850, 600)
        self.setStyleSheet(STYLESHEET)

        # Icon
        icon_path = os.path.join(SCRIPT_DIR, "icon.ico")
        if not os.path.exists(icon_path):
            icon_path = os.path.join(REPO_ROOT, "icon.ico")
        if os.path.exists(icon_path):
            self.setWindowIcon(QIcon(icon_path))

        # Main Layout
        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        layout = QVBoxLayout(central_widget)
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(15)

        # 1. Header
        header_layout = QVBoxLayout()
        title = QLabel("Intra Server Manager")
        title.setObjectName("Title")
        subtitle = QLabel("Windows — Live monitoring, addons & control for IntraChat backend server.")
        subtitle.setObjectName("Subtitle")
        header_layout.addWidget(title)
        header_layout.addWidget(subtitle)
        layout.addLayout(header_layout)

        # 2. Info Box (IP & Password)
        self.info_box = QLabel()
        self.info_box.setObjectName("InfoBox")
        self.info_box.setTextInteractionFlags(Qt.TextSelectableByMouse)
        layout.addWidget(self.info_box)

        # Key Management & Capabilities Layout
        action_layout = QHBoxLayout()
        self.copy_key_btn = QPushButton("📋 COPY ADMIN KEY")
        self.copy_key_btn.setObjectName("StartBtn")
        self.copy_key_btn.setCursor(Qt.PointingHandCursor)
        self.copy_key_btn.clicked.connect(self.copy_admin_key)

        self.fix_db_btn = QPushButton("📂 FIX DB PATH")
        self.fix_db_btn.setObjectName("FixDBBtn")
        self.fix_db_btn.setCursor(Qt.PointingHandCursor)
        self.fix_db_btn.clicked.connect(self.fix_db_location)

        self.capability_btn = QPushButton("🧩 CAPABILITIES / ADDONS")
        self.capability_btn.setObjectName("CapabilityBtn")
        self.capability_btn.setCursor(Qt.PointingHandCursor)
        self.capability_btn.clicked.connect(self.open_capabilities_dialog)

        self.open_admin_btn = QPushButton("⚙️ OPEN ADMIN PANEL")
        self.open_admin_btn.setStyleSheet("""
            QPushButton { background-color: #cba6f7; color: #11111b; font-weight: bold; font-size: 16px; padding: 12px; border-radius: 8px; }
            QPushButton:hover { background-color: #b4befe; }
            QPushButton:disabled { background-color: #45475a; color: #a6adc8; }
        """)
        self.open_admin_btn.setCursor(Qt.PointingHandCursor)
        self.open_admin_btn.clicked.connect(self.open_admin_panel)

        action_layout.addWidget(self.copy_key_btn)
        action_layout.addWidget(self.fix_db_btn)
        action_layout.addWidget(self.capability_btn)
        action_layout.addWidget(self.open_admin_btn)
        layout.addLayout(action_layout)

        # 3. Start / Stop buttons
        btn_layout = QHBoxLayout()
        self.start_btn = QPushButton("▶ START SERVER")
        self.start_btn.setObjectName("StartBtn")
        self.start_btn.setCursor(Qt.PointingHandCursor)
        self.start_btn.clicked.connect(self.start_service)

        self.stop_btn = QPushButton("🛑 STOP SERVER")
        self.stop_btn.setObjectName("StopBtn")
        self.stop_btn.setCursor(Qt.PointingHandCursor)
        self.stop_btn.clicked.connect(self.stop_service)
        self.stop_btn.setEnabled(False)

        btn_layout.addWidget(self.start_btn)
        btn_layout.addWidget(self.stop_btn)
        layout.addLayout(btn_layout)

        # 4. Live Console Logs
        log_label = QLabel("Live Server Logs:")
        log_label.setStyleSheet("font-weight: bold; color: #fab387; margin-top: 10px;")
        layout.addWidget(log_label)

        self.console = QTextEdit()
        self.console.setReadOnly(True)
        layout.addWidget(self.console)

        # ================= ENGINE ROOM =================
        self.status_timer = QTimer(self)
        self.status_timer.timeout.connect(self.check_service_status)
        self.status_timer.start(2000)

        # Initial state
        self.update_info_box("Stopped 🔴")
        self.admin_window = None
        self.capabilities_dialog = None
        self._log_info("Intra Server Manager started. Click ▶ START SERVER to begin.")

    # ─────────────────────────────────────────────────────────────
    # Helpers
    # ─────────────────────────────────────────────────────────────
    def get_local_ip(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.connect(('8.8.8.8', 80))
            ip = s.getsockname()[0]
        except Exception:
            ip = '127.0.0.1'
        finally:
            s.close()
        return ip

    def get_admin_password(self):
        last_error = None

        if os.path.exists(CONFIG_FILE):
            try:
                with open(CONFIG_FILE, 'r') as f:
                    config = json.load(f)
                    saved_path = config.get("db_path")
                    if saved_path and os.path.exists(saved_path):
                        conn = sqlite3.connect(saved_path)
                        cursor = conn.cursor()
                        cursor.execute("SELECT value FROM config WHERE key='admin_key'")
                        row = cursor.fetchone()
                        conn.close()
                        if row and row[0]:
                            return row[0]
            except Exception as e:
                last_error = f"Config Error: {str(e)}"

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
        self.current_admin_key = (
            password
            if password not in ("Not Generated Yet", "Database not found")
            and not password.startswith("Error:")
            else ""
        )
        status_color = "#a6e3a1" if "Running" in status else "#f38ba8"
        self.copy_key_btn.setEnabled(bool(self.current_admin_key))
        self.fix_db_btn.setVisible(not bool(self.current_admin_key))

        info_html = f"""
        <table width="100%">
            <tr><td width="150"><b>Server Status:</b></td><td><span style='color: {status_color};'>{status}</span></td></tr>
            <tr><td><b>Android App IP:</b></td><td><span style='color: #89b4fa;'>http://{ip}:8000</span></td></tr>
            <tr><td><b>Admin Key:</b></td><td><span style='color: #f9e2af;'>{password}</span></td></tr>
        </table>
        """
        self.info_box.setText(info_html)

    def _log_info(self, msg):
        self.console.append(f"<span style='color: #a6e3a1;'>[INFO] {msg}</span>")

    def _log_error(self, msg):
        self.console.append(f"<span style='color: #f38ba8;'>[ERROR] {msg}</span>")

    def _log_server(self, msg):
        self.console.append(f"<span style='color: #cdd6f4;'>{msg}</span>")
        self.console.ensureCursorVisible()

    # ─────────────────────────────────────────────────────────────
    # Server Start / Stop
    # ─────────────────────────────────────────────────────────────
    def start_service(self):
        if self.server_process and self.server_process.state() == QProcess.Running:
            self._log_info("Server pehle se chal raha hai.")
            return

        venv_python = os.path.join(BACKEND_DIR, "venv", "Scripts", "python.exe")
        python_exe = venv_python if os.path.exists(venv_python) else sys.executable

        self._log_info(f"Python: {python_exe}")
        self._log_info(f"Backend: {BACKEND_DIR}")

        self.server_process = QProcess(self)
        self.server_process.setWorkingDirectory(BACKEND_DIR)

        # Inject Intra Tools directory into QProcess environment
        process_env = QProcess.systemEnvironment()
        tools_dir = get_intra_tools_dir()
        updated_env = []
        path_found = False
        for entry in process_env:
            if entry.upper().startswith("PATH="):
                key, val = entry.split("=", 1)
                updated_env.append(f"{key}={tools_dir}{os.pathsep}{val}")
                path_found = True
            else:
                updated_env.append(entry)
        if not path_found:
            updated_env.append(f"PATH={tools_dir}")
        self.server_process.setEnvironment(updated_env)

        self.server_process.setProcessChannelMode(QProcess.MergedChannels)
        self.server_process.readyReadStandardOutput.connect(self._read_server_logs)
        self.server_process.finished.connect(self._server_stopped)

        self.server_process.start(python_exe, ["run_server.py", "--no-reload"])

        if not self.server_process.waitForStarted(3000):
            self._log_error("Server process start nahi hua! Python path check karo.")
            return

        self._log_info("Server process launch ho gaya! Boot hone do...")
        self.start_btn.setEnabled(False)
        self.stop_btn.setEnabled(True)
        self.update_info_box("Starting... ⏳")

    def stop_service(self):
        if self.server_process and self.server_process.state() == QProcess.Running:
            self._log_info("Server band kar rahe hain...")
            self.server_process.terminate()
            QTimer.singleShot(3000, self._force_kill)
        else:
            self._log_info("Server pehle se band hai.")

    def _force_kill(self):
        if self.server_process and self.server_process.state() == QProcess.Running:
            self._log_error("Graceful stop nahi hua. Force kill kar rahe hain...")
            self.server_process.kill()

    def _server_stopped(self, exit_code, exit_status):
        self._log_info(f"Server band ho gaya (exit code: {exit_code}).")
        self.start_btn.setEnabled(True)
        self.stop_btn.setEnabled(False)
        self.update_info_box("Stopped 🔴")

    def _read_server_logs(self):
        data = self.server_process.readAllStandardOutput().data()
        try:
            text = data.decode('utf-8', errors='replace')
        except Exception:
            text = str(data)
        if text.strip():
            for line in text.splitlines():
                if line.strip():
                    self._log_server(line)

    def check_service_status(self):
        is_running = (
            self.server_process is not None
            and self.server_process.state() == QProcess.Running
        )
        if is_running:
            self.update_info_box("Running 🟢")
            self.start_btn.setEnabled(False)
            self.stop_btn.setEnabled(True)
        else:
            if self.stop_btn.isEnabled():
                self.update_info_box("Stopped 🔴")
                self.start_btn.setEnabled(True)
                self.stop_btn.setEnabled(False)

    # ─────────────────────────────────────────────────────────────
    # Actions & Dialogs
    # ─────────────────────────────────────────────────────────────
    def open_capabilities_dialog(self):
        self.capabilities_dialog = CapabilitiesDialog(self)
        self.capabilities_dialog.exec()

    def copy_admin_key(self):
        if not self.current_admin_key:
            self._log_info("Admin key abhi available nahi hai.")
            return
        QApplication.clipboard().setText(self.current_admin_key)
        self._log_info("Admin key clipboard mein copy ho gaya! ✅")

    def fix_db_location(self):
        dialog = AdminUnlockDialog(self)
        if dialog.exec() == QDialog.Accepted:
            custom_db = dialog.get_db_path()
            entered_code = dialog.get_code()

            real_key = None
            if custom_db and os.path.exists(custom_db):
                real_key = self._read_key_from_db(custom_db)
                try:
                    with open(CONFIG_FILE, 'w') as f:
                        json.dump({"db_path": custom_db}, f)
                    self._log_info(f"DB Path saved to config: {custom_db}")
                except Exception as e:
                    self._log_error(f"Failed to save config: {e}")
            elif entered_code:
                real_key = entered_code

            if real_key:
                self.current_admin_key = real_key
                self._log_info(f"Key successfully updated! Key: {real_key[:5]}...")
                is_running = bool(self.server_process and self.server_process.state() == QProcess.Running)
                self.update_info_box("Running 🟢" if is_running else "Stopped 🔴")
            else:
                QMessageBox.warning(self, "Error", "Sahi DB path select karein ya valid key daalein.")

    def open_admin_panel(self):
        if AdminWindow is None:
            self._log_error("AdminWindow import nahi ho saka from intra_admin / systemctlmanger.")
            return

        if not self.current_admin_key:
            QMessageBox.warning(self, "Key Required", "Admin Key missing! Please click 'FIX DB PATH' first.")
            self._log_error("Admin key missing. Use FIX DB PATH.")
            return

        url = "http://127.0.0.1:8000"
        try:
            self.admin_window = AdminWindow(url, self.current_admin_key)
            self.admin_window.show()
            self._log_info(f"Admin Panel opened: {url} ✅")
        except Exception as e:
            self._log_error(f"Failed to open Admin Panel: {str(e)}")

    def _read_key_from_db(self, db_path):
        try:
            conn = sqlite3.connect(db_path)
            cursor = conn.cursor()
            cursor.execute("SELECT value FROM config WHERE key='admin_key'")
            row = cursor.fetchone()
            conn.close()
            return row[0] if row and row[0] else None
        except Exception as e:
            self._log_error(f"DB read error: {e}")
            return None

    def closeEvent(self, event):
        if self.server_process and self.server_process.state() == QProcess.Running:
            reply = QMessageBox.question(
                self,
                "Server Chal Raha Hai",
                "Server abhi chal raha hai.\nBand karein?",
                QMessageBox.Yes | QMessageBox.No
            )
            if reply == QMessageBox.Yes:
                self.server_process.terminate()
                self.server_process.waitForFinished(3000)
                event.accept()
            else:
                event.ignore()
        else:
            event.accept()


# ================= ADMIN UNLOCK DIALOG =================
class AdminUnlockDialog(QDialog):
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

        title = QLabel("📂  Database & Admin Key")
        title.setStyleSheet("font-size: 16px; font-weight: bold; color: #cba6f7;")
        layout.addWidget(title)

        desc = QLabel("Database auto-detect nahi hui. DB file browse karein ya key daalein:")
        desc.setStyleSheet("font-size: 12px; color: #a6adc8;")
        desc.setWordWrap(True)
        layout.addWidget(desc)

        form = QFormLayout()
        form.setSpacing(10)

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

        self.code_input = QLineEdit()
        self.code_input.setEchoMode(QLineEdit.Password)
        self.code_input.setPlaceholderText("Admin key daalo (optional)")
        form.addRow("Admin Key:", self.code_input)

        layout.addLayout(form)

        btn_box = QDialogButtonBox()
        unlock_btn = btn_box.addButton("Update Key", QDialogButtonBox.AcceptRole)
        cancel_btn = btn_box.addButton("Cancel", QDialogButtonBox.RejectRole)
        btn_box.accepted.connect(self.accept)
        btn_box.rejected.connect(self.reject)
        layout.addWidget(btn_box)

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
