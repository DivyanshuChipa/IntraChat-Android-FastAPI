# ===== File: admin.py =====
import sys
import os
import requests
from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QPushButton, QTableWidget, QTableWidgetItem, QMessageBox,
    QInputDialog, QLineEdit, QFrame, QHeaderView, QCheckBox, QDialog,
    QDialogButtonBox, QFormLayout, QStackedWidget, QComboBox # 👈 YE ADD KARO
)
from PySide6.QtGui import QPixmap, QFont, QIcon, QColor
from PySide6.QtCore import Qt, QSize, QSettings, QTimer

# Global Config (Will be set via Dialog)
SERVER_URL = ""
ADMIN_KEY = ""

# ================= MODERN STYLESHEET (Dark Theme) =================
STYLESHEET = """
QMainWindow { background-color: #1e1e2e; }
QWidget { color: #cdd6f4; font-family: 'Segoe UI', sans-serif; }

/* Tabs (Sidebar style) */
QPushButton#TabBtn {
    background-color: transparent;
    color: #a6adc8;
    text-align: left;
    padding: 12px 20px;
    font-size: 16px;
    border-radius: 8px;
    border: none;
}
QPushButton#TabBtn:hover { background-color: #313244; color: #fff; }
QPushButton#TabBtn[active="true"] {
    background-color: #cba6f7;
    color: #1e1e2e;
    font-weight: bold;
}

/* Table */
QTableWidget {
    background-color: #181825;
    border: 1px solid #313244;
    gridline-color: #313244;
    border-radius: 8px;
    selection-background-color: #45475a;
}
QHeaderView::section {
    background-color: #1e1e2e;
    padding: 8px;
    border: none;
    font-weight: bold;
    color: #cba6f7;
}

/* Action Buttons */
QPushButton.actionBtn {
    padding: 8px 15px;
    border-radius: 6px;
    font-weight: bold;
    color: white;
}
QPushButton#RefreshBtn { background-color: #89b4fa; border: none; color: #1e1e2e;}
QPushButton#ApproveBtn { background-color: #a6e3a1; border: none; color: #1e1e2e;}
QPushButton#ResetBtn { background-color: #f9e2af; border: none; color: #1e1e2e;}
QPushButton#DeleteBtn { background-color: #f38ba8; border: none; color: #1e1e2e;}
QPushButton#SaveBtn { background-color: #cba6f7; border: none; color: #1e1e2e; padding: 10px;}

QPushButton.actionBtn:hover { margin-top: -2px; }

/* Inputs */
QLineEdit {
    background-color: #313244;
    border: 1px solid #45475a;
    border-radius: 6px;
    padding: 8px;
    color: white;
}
"""

# ================= CONNECTION DIALOG =================
class ConnectDialog(QDialog):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Connect to Intra Server")
        self.setFixedSize(400, 250)
        self.setStyleSheet("""
            QDialog { background-color: #1e1e2e; color: white; }
            QLineEdit { background-color: #313244; padding: 8px; border-radius: 5px; color: white; border: 1px solid #45475a;}
            QLabel { font-size: 14px; font-weight: bold; color: #cba6f7; }
            QPushButton { background-color: #cba6f7; color: #1e1e2e; padding: 8px; border-radius: 5px; font-weight: bold; }
        """)

        # Load saved settings
        self.settings = QSettings("Intra", "AdminPanel")
        saved_ip = self.settings.value("server_ip", "192.168.31.104")
        saved_port = self.settings.value("server_port", "8000")
        saved_key = self.settings.value("admin_key", "INTRA_ADMIN_123")

        layout = QVBoxLayout()
        form = QFormLayout()

        self.ip_input = QLineEdit(saved_ip)
        self.port_input = QLineEdit(saved_port)
        self.key_input = QLineEdit(saved_key)
        self.key_input.setEchoMode(QLineEdit.Password)

        form.addRow("Server IP:", self.ip_input)
        form.addRow("Port:", self.port_input)
        form.addRow("Admin Key:", self.key_input)

        layout.addLayout(form)

        self.btn_connect = QPushButton("Connect")
        self.btn_connect.clicked.connect(self.save_and_accept)
        layout.addWidget(self.btn_connect)

        self.setLayout(layout)

    def save_and_accept(self):
        self.settings.setValue("server_ip", self.ip_input.text())
        self.settings.setValue("server_port", self.port_input.text())
        self.settings.setValue("admin_key", self.key_input.text())
        self.accept()

# ================= MAIN ADMIN WINDOW =================
class AdminWindow(QMainWindow):
    def __init__(self, server_url, admin_key):
        super().__init__()
        self.server_url = server_url
        self.headers = {"X-ADMIN-KEY": admin_key}

        self.setWindowTitle("Intra Admin Panel (Pro)")
        self.resize(1100, 700)
        self.setStyleSheet(STYLESHEET)


        # Main Layout
        main_widget = QWidget()
        self.setCentralWidget(main_widget)
        main_layout = QHBoxLayout(main_widget)
        main_layout.setContentsMargins(0, 0, 0, 0)
        main_layout.setSpacing(0)

        # --- Sidebar ---
        sidebar = QFrame()
        sidebar.setFixedWidth(220)
        sidebar.setStyleSheet("background-color: #11111b; border-right: 1px solid #313244;")
        sidebar_layout = QVBoxLayout(sidebar)
        sidebar_layout.setContentsMargins(15, 30, 15, 30)
        sidebar_layout.setSpacing(10)

        # Title
        title_lbl = QLabel("INTRA\nADMIN")
        title_lbl.setAlignment(Qt.AlignCenter)
        title_lbl.setStyleSheet("font-size: 24px; font-weight: bold; color: #cba6f7; margin-bottom: 20px;")
        sidebar_layout.addWidget(title_lbl)

        # Nav Buttons
        self.btn_users = self.create_nav_btn("👥 Users")
        self.btn_settings = self.create_nav_btn("⚙️ Settings")
        self.btn_ai = self.create_nav_btn("🧠 AI & Bot")  # 👈 NAYA BUTTON
        self.btn_cleanup = self.create_nav_btn("🧹 Cleanup")

        # Clicks ko map karo (Dhyan rakhna index change honge)
        self.btn_users.clicked.connect(lambda: self.switch_tab(0))
        self.btn_settings.clicked.connect(lambda: self.switch_tab(1))
        self.btn_ai.clicked.connect(lambda: self.switch_tab(2))      # 👈 NAYA TAB INDEX 2
        self.btn_cleanup.clicked.connect(lambda: self.switch_tab(3)) # 👈 CLEANUP AB 3 PAR HAI

        sidebar_layout.addWidget(self.btn_users)
        sidebar_layout.addWidget(self.btn_settings)
        sidebar_layout.addWidget(self.btn_ai)        # 👈 SIDEBAR MEIN ADD KARO
        sidebar_layout.addWidget(self.btn_cleanup)
        sidebar_layout.addStretch()

        # --- Content Area ---
        self.stack = QStackedWidget()

        # Pages
        self.page_users = self.create_users_page()
        self.page_settings = self.create_settings_page()
        self.page_ai = self.create_ai_page()          # 👈 NAYI PAGE CALL
        self.page_cleanup = self.create_cleanup_page()

        self.stack.addWidget(self.page_users)
        self.stack.addWidget(self.page_settings)
        self.stack.addWidget(self.page_ai)             # 👈 STACK MEIN ADD (Index 2)
        self.stack.addWidget(self.page_cleanup)        # 👈 Index 3

        # Add to main layout
        main_layout.addWidget(sidebar)
        main_layout.addWidget(self.stack)

        # Refresh Timer
        self.refresh_timer = QTimer()
        self.refresh_timer.setInterval(30000) # 30 seconds
        self.refresh_timer.timeout.connect(self.load_users)

        # Initial Load
        self.switch_tab(0)

    def create_nav_btn(self, text):
        btn = QPushButton(text)
        btn.setObjectName("TabBtn")
        btn.setCursor(Qt.PointingHandCursor)
        return btn

    def switch_tab(self, index):
        # Reset styles
        self.btn_users.setProperty("active", "false")
        self.btn_settings.setProperty("active", "false")
        self.btn_ai.setProperty("active", "false")      # 👈 NAYA RESET
        self.btn_cleanup.setProperty("active", "false")

        self.stack.setCurrentIndex(index)

        # Timer management
        if index == 0:
            self.btn_users.setProperty("active", "true")
            self.load_users()
            self.refresh_timer.start()
        else:
            self.refresh_timer.stop()

        if index == 1:
            self.btn_settings.setProperty("active", "true")
            self.load_settings()
        elif index == 2:                                # 👈 NAYA INDEX LOGIC
            self.btn_ai.setProperty("active", "true")
            self.load_ai_settings()
        elif index == 3:                                # 👈 CLEANUP AB 3
            self.btn_cleanup.setProperty("active", "true")

        # Update styling
        self.btn_users.setStyle(self.btn_users.style())
        self.btn_settings.setStyle(self.btn_settings.style())
        self.btn_ai.setStyle(self.btn_ai.style())       # 👈 UPDATE STYLE
        self.btn_cleanup.setStyle(self.btn_cleanup.style())

    # ================= PAGE: USERS =================
    def create_users_page(self):
        page = QWidget()
        layout = QVBoxLayout(page)

        # Header
        header = QHBoxLayout()
        lbl = QLabel("User Management")
        lbl.setStyleSheet("font-size: 22px; font-weight: bold;")

        refresh_btn = QPushButton("🔄 Refresh")
        refresh_btn.setObjectName("RefreshBtn")
        refresh_btn.setFixedSize(100, 35)
        refresh_btn.setProperty("class", "actionBtn")
        refresh_btn.clicked.connect(self.load_users)

        header.addWidget(lbl)
        header.addStretch()
        header.addWidget(refresh_btn)

        # Table
        self.table = QTableWidget()
        self.table.setColumnCount(5) # Photo, Name, Status, Approved?, Actions
        self.table.setHorizontalHeaderLabels(["Photo", "Username", "Online", "Status", "Actions"])
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.Stretch)
        self.table.setEditTriggers(QTableWidget.NoEditTriggers)
        self.table.setFocusPolicy(Qt.NoFocus)
        self.table.verticalHeader().setDefaultSectionSize(70)
        self.table.setSelectionBehavior(QTableWidget.SelectRows)

        layout.addLayout(header)
        layout.addWidget(self.table)
        return page

    def load_users(self):
        try:
            res = requests.get(f"{self.server_url}/admin/users", headers=self.headers, timeout=3)
            data = res.json()
            if not data.get("success"): raise Exception("API Error")

            users = data.get("users", [])
            self.table.setRowCount(len(users))

            for row, user in enumerate(users):
                username = user.get("username", "Unknown")
                is_online = user.get("is_online", False)
                is_approved = user.get("is_approved", True)
                photo_url = user.get("profile_photo")

                # 1. Photo
                photo_lbl = QLabel()
                photo_lbl.setFixedSize(50, 50)
                photo_lbl.setAlignment(Qt.AlignCenter)
                photo_lbl.setStyleSheet("background-color: #45475a; border-radius: 25px;")
                if photo_url:
                    self.load_image_async(photo_lbl, photo_url)
                else:
                    photo_lbl.setText(username[0].upper())

                cell_photo = QWidget()
                p_layout = QHBoxLayout(cell_photo)
                p_layout.addWidget(photo_lbl)
                p_layout.setAlignment(Qt.AlignCenter)
                p_layout.setContentsMargins(0,0,0,0)
                self.table.setCellWidget(row, 0, cell_photo)

                # 2. Name
                self.table.setItem(row, 1, QTableWidgetItem(username))

                # 3. Online Status
                status_item = QTableWidgetItem("🟢 Online" if is_online else "⚫ Offline")
                status_item.setForeground(QColor("#a6e3a1") if is_online else QColor("#585b70"))
                self.table.setItem(row, 2, status_item)

                # 4. Approval Status
                app_text = "✅ Approved" if is_approved else "⏳ PENDING"
                app_item = QTableWidgetItem(app_text)
                app_item.setForeground(QColor("#a6e3a1") if is_approved else QColor("#f9e2af"))
                app_item.setFont(QFont("Segoe UI", 10, QFont.Bold))
                self.table.setItem(row, 3, app_item)

                # 5. Actions (Buttons)
                action_widget = QWidget()
                action_layout = QHBoxLayout(action_widget)
                action_layout.setContentsMargins(5, 5, 5, 5)

                if not is_approved:
                    btn_approve = QPushButton("Approve")
                    btn_approve.setObjectName("ApproveBtn")
                    btn_approve.setProperty("class", "actionBtn")
                    btn_approve.clicked.connect(lambda _, u=username: self.approve_user(u))
                    action_layout.addWidget(btn_approve)

                btn_reset = QPushButton("Reset Pass")
                btn_reset.setObjectName("ResetBtn")
                btn_reset.setProperty("class", "actionBtn")
                btn_reset.clicked.connect(lambda _, u=username: self.reset_pass(u))

                btn_del = QPushButton("Delete")
                btn_del.setObjectName("DeleteBtn")
                btn_del.setProperty("class", "actionBtn")
                btn_del.clicked.connect(lambda _, u=username: self.delete_user(u))

                action_layout.addWidget(btn_reset)
                action_layout.addWidget(btn_del)
                self.table.setCellWidget(row, 4, action_widget)

        except Exception as e:
            print(e)
            QMessageBox.critical(self, "Connection Error", f"Could not connect to {self.server_url}")

    def load_image_async(self, label, url):
        # Simply trying to fetch in main thread for simplicity (LAN is fast)
        try:
            r = requests.get(self.server_url + url, timeout=1)
            pix = QPixmap()
            pix.loadFromData(r.content)
            label.setPixmap(pix.scaled(50, 50, Qt.KeepAspectRatio, Qt.SmoothTransformation))
        except:
            pass

    def approve_user(self, username):
        try:
            res = requests.post(f"{self.server_url}/admin/approve_user", headers=self.headers, params={"username": username}, timeout=3)
            if res.status_code == 200:
                self.load_users()
                # Show a temporary status? For now QMessageBox is fine or just refresh.
            else:
                raise Exception(f"Server error: {res.status_code}")
        except Exception as e:
            QMessageBox.warning(self, "Error", str(e))

    def reset_pass(self, username):
        pwd, ok = QInputDialog.getText(self, "Reset Password", f"New password for {username}:")
        if ok and pwd:
            requests.post(f"{self.server_url}/admin/reset_password", headers=self.headers, params={"username": username, "new_pass": pwd})
            QMessageBox.information(self, "Success", "Password changed.")

    def delete_user(self, username):
        res = QMessageBox.question(self, "Confirm", f"Delete {username}?", QMessageBox.Yes | QMessageBox.No)
        if res == QMessageBox.Yes:
            requests.post(f"{self.server_url}/admin/delete_user", headers=self.headers, params={"username": username})
            self.load_users()

    # ================= PAGE: SETTINGS =================
    def create_settings_page(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(20, 20, 20, 20)

        lbl = QLabel("Global Settings")
        lbl.setStyleSheet("font-size: 24px; font-weight: bold; color: #cba6f7; margin-bottom: 10px;")

        # Toggle Card
        card = QFrame()
        card.setStyleSheet("background-color: #181825; border: 1px solid #313244; border-radius: 12px;")
        card_layout = QVBoxLayout(card)
        card_layout.setContentsMargins(30, 30, 30, 30)
        card_layout.setSpacing(15)

        self.chk_approval = QCheckBox("Require Admin Approval for New Users")
        self.chk_approval.setTristate(False)
        self.chk_approval.setChecked(False)
        self.chk_approval.setCursor(Qt.PointingHandCursor)
        self.chk_approval.setStyleSheet("""
            QCheckBox {
                font-size: 18px;
                color: #cdd6f4;
                font-weight: 500;
            }
            QCheckBox::indicator {
                width: 24px;
                height: 24px;
                border-radius: 6px;
                border: 2px solid #45475a;
                background-color: #313244;
            }
            QCheckBox::indicator:checked {
                background-color: #cba6f7;
                border-color: #cba6f7;
            }
            QCheckBox::indicator:hover {
                border-color: #cba6f7;
            }
        """)

        desc = QLabel("When enabled, new users must be manually approved by an admin before they can log in. If disabled, registration is automatic and immediate.")
        desc.setWordWrap(True)
        desc.setStyleSheet("color: #9399b2; font-size: 14px; line-height: 1.5;")

        # Status Label for feedback
        self.settings_status = QLabel("")
        self.settings_status.setStyleSheet("font-weight: bold; font-size: 14px;")
        self.settings_status.setAlignment(Qt.AlignCenter)

        # Big Save Button
        save_btn = QPushButton("💾 SAVE SETTINGS")
        save_btn.setObjectName("SaveBtn")
        save_btn.setMinimumHeight(55)
        save_btn.setCursor(Qt.PointingHandCursor)
        save_btn.setStyleSheet("""
            QPushButton#SaveBtn {
                background-color: #cba6f7;
                color: #11111b;
                font-size: 16px;
                font-weight: bold;
                border-radius: 8px;
                margin-top: 10px;
            }
            QPushButton#SaveBtn:hover {
                background-color: #b4befe;
            }
        """)
        save_btn.clicked.connect(self.save_settings)

        card_layout.addWidget(self.chk_approval)
        card_layout.addWidget(desc)
        card_layout.addSpacing(10)
        card_layout.addWidget(self.settings_status)
        card_layout.addWidget(save_btn)

        layout.addWidget(lbl)
        layout.addWidget(card)
        layout.addStretch()
        return page

    def load_settings(self):
        try:
            self.settings_status.setText("") # Clear previous status
            res = requests.get(f"{self.server_url}/admin/settings", headers=self.headers, timeout=3)
            data = res.json()
            if data["success"]:
                self.chk_approval.blockSignals(True)
                self.chk_approval.setChecked(bool(data["require_approval"]))
                self.chk_approval.blockSignals(False)
        except Exception as e:
            print("load_settings error:", e)

    def save_settings(self):
        try:
            self.settings_status.setText("Saving...")
            self.settings_status.setStyleSheet("color: #89b4fa;")

            val = self.chk_approval.isChecked()
            res = requests.post(f"{self.server_url}/admin/toggle_approval",
                              headers=self.headers,
                              params={"enabled": val},
                              timeout=3)

            if res.status_code == 200:
                self.settings_status.setText("✅ Settings Saved Successfully!")
                self.settings_status.setStyleSheet("color: #a6e3a1;")
            else:
                raise Exception(f"Server error: {res.status_code}")
        except Exception as e:
            self.settings_status.setText(f"❌ Error: {str(e)}")
            self.settings_status.setStyleSheet("color: #f38ba8;")


    # ================= PAGE: AI SETTINGS =================
    def create_ai_page(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(20, 20, 20, 20)

        lbl = QLabel("AI & Lumir Bot Configuration")
        lbl.setStyleSheet("font-size: 24px; font-weight: bold; color: #a6e3a1; margin-bottom: 10px;")

        card = QFrame()
        card.setStyleSheet("background-color: #181825; border: 1px solid #313244; border-radius: 12px;")
        card_layout = QVBoxLayout(card)
        card_layout.setContentsMargins(30, 30, 30, 30)
        card_layout.setSpacing(15)

        # 1. Enable AI Toggle
        self.chk_ai_enable = QCheckBox("Enable AI Engine (Local/Tailscale Ollama)")
        self.chk_ai_enable.setCursor(Qt.PointingHandCursor)
        self.chk_ai_enable.setStyleSheet("""
            QCheckBox { font-size: 18px; color: #cdd6f4; font-weight: 500;} 
            QCheckBox::indicator { width: 24px; height: 24px; border-radius: 6px; border: 2px solid #45475a; background-color: #313244;}
            QCheckBox::indicator:checked { background-color: #cba6f7; border-color: #cba6f7;}
        """)

        desc = QLabel("When enabled, Lumir will fallback to AI for unrecognized commands. Requires Ollama running.")
        desc.setWordWrap(True)
        desc.setStyleSheet("color: #9399b2; font-size: 14px; line-height: 1.5;")

        # 2. Model & URL Selection
        model_layout = QHBoxLayout()

        # Model Dropdown
        model_lbl = QLabel("Select Model:")
        model_lbl.setStyleSheet("font-size: 16px; font-weight: bold; color: #cdd6f4;")
        self.model_dropdown = QComboBox()
        self.model_dropdown.addItems(["llama3:8b", "mistral", "gemma", "deepseek-coder, llama2", "gemma3:270m"])
        self.model_dropdown.setStyleSheet("background-color: #313244; color: white; border-radius: 6px; padding: 8px;")

        # Ollama URL Input (Tailscale Support)
        url_lbl = QLabel("  Ollama URL:")
        url_lbl.setStyleSheet("font-size: 16px; font-weight: bold; color: #cdd6f4;")
        self.url_input = QLineEdit()
        self.url_input.setPlaceholderText("http://100.x.x.x:11434") # Tailscale example
        self.url_input.setStyleSheet("background-color: #313244; color: white; border-radius: 6px; padding: 8px; border: 1px solid #45475a;")

        model_layout.addWidget(model_lbl)
        model_layout.addWidget(self.model_dropdown)
        model_layout.addWidget(url_lbl)
        model_layout.addWidget(self.url_input, 1) # 1 adds stretch factor

        # 3. Status Display
        self.ai_status_lbl = QLabel("Status: Waiting...")
        self.ai_status_lbl.setStyleSheet("font-weight: bold; font-size: 14px; color: #f9e2af;")
        self.ai_status_lbl.setAlignment(Qt.AlignCenter)

        # 4. Save Button
        save_btn = QPushButton("💾 SAVE AI CONFIGURATION")
        save_btn.setMinimumHeight(55)
        save_btn.setCursor(Qt.PointingHandCursor)
        save_btn.setStyleSheet("""
            QPushButton { background-color: #a6e3a1; color: #11111b; font-size: 16px; font-weight: bold; border-radius: 8px;}
            QPushButton:hover { background-color: #94e2d5; }
        """)
        save_btn.clicked.connect(self.save_ai_settings)

        card_layout.addWidget(self.chk_ai_enable)
        card_layout.addWidget(desc)
        card_layout.addSpacing(15)
        card_layout.addLayout(model_layout)
        card_layout.addSpacing(15)
        card_layout.addWidget(self.ai_status_lbl)
        card_layout.addWidget(save_btn)

        layout.addWidget(lbl)
        layout.addWidget(card)
        layout.addStretch()
        return page

    def load_ai_settings(self):
        try:
            self.ai_status_lbl.setText("Status: ⏳ Fetching from server...")
            self.ai_status_lbl.setStyleSheet("color: #f9e2af;")
            res = requests.get(f"{self.server_url}/admin/ai_settings", headers=self.headers, timeout=3)
            data = res.json()
            if data.get("success"):
                config = data.get("config", {})

                # Checkbox ko update karo bina trigger kiye
                self.chk_ai_enable.blockSignals(True)
                self.chk_ai_enable.setChecked(config.get("ai_enabled", False))
                self.chk_ai_enable.blockSignals(False)

                self.model_dropdown.setCurrentText(config.get("ai_model", "gemma3:270m"))
                self.url_input.setText(config.get("ollama_url", "http://localhost:11434"))

                self.ai_status_lbl.setText("Status: ✅ Loaded Successfully")
                self.ai_status_lbl.setStyleSheet("color: #a6e3a1;")
        except Exception as e:
            self.ai_status_lbl.setText(f"Status: ❌ Error: {str(e)}")
            self.ai_status_lbl.setStyleSheet("color: #f38ba8;")

    def save_ai_settings(self):
        try:
            self.ai_status_lbl.setText("Status: ⏳ Saving...")
            self.ai_status_lbl.setStyleSheet("color: #89b4fa;")

            enabled = self.chk_ai_enable.isChecked()
            model = self.model_dropdown.currentText()
            url = self.url_input.text().strip()

            res = requests.post(
                f"{self.server_url}/admin/ai_settings",
                headers=self.headers,
                params={"enabled": enabled, "model": model, "url": url},
                timeout=3
            )

            if res.status_code == 200:
                self.ai_status_lbl.setText("Status: ✅ Saved! Lumir is updated.")
                self.ai_status_lbl.setStyleSheet("color: #a6e3a1;")
            else:
                raise Exception(f"Server returned {res.status_code}")
        except Exception as e:
            self.ai_status_lbl.setText(f"Status: ❌ Failed to save: {str(e)}")
            self.ai_status_lbl.setStyleSheet("color: #f38ba8;")

    # ================= PAGE: CLEANUP =================
    def create_cleanup_page(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(20, 20, 20, 20)

        lbl = QLabel("Server Maintenance")
        lbl.setStyleSheet("font-size: 24px; font-weight: bold; color: #f38ba8; margin-bottom: 10px;")

        card = QFrame()
        card.setStyleSheet("background-color: #181825; border: 1px solid #313244; border-radius: 12px;")
        c_layout = QVBoxLayout(card)
        c_layout.setContentsMargins(30, 30, 30, 30)
        c_layout.setSpacing(15)

        title = QLabel("Cleanup Database")
        title.setStyleSheet("font-size: 18px; font-weight: bold; color: #cdd6f4;")

        desc = QLabel("Remove old messages from the database to save space. This action is irreversible.")
        desc.setStyleSheet("color: #9399b2; font-size: 14px;")

        self.days_input = QLineEdit()
        self.days_input.setPlaceholderText("Number of days (e.g., 30)")
        self.days_input.setMinimumHeight(45)

        btn = QPushButton("🗑️ DELETE MESSAGES")
        btn.setObjectName("DeleteBtn")
        btn.setMinimumHeight(50)
        btn.setCursor(Qt.PointingHandCursor)
        btn.setStyleSheet("""
            QPushButton#DeleteBtn {
                background-color: #f38ba8;
                color: #11111b;
                font-weight: bold;
                border-radius: 8px;
            }
            QPushButton#DeleteBtn:hover {
                background-color: #eba0ac;
            }
        """)
        btn.clicked.connect(self.run_cleanup)

        c_layout.addWidget(title)
        c_layout.addWidget(desc)
        c_layout.addWidget(self.days_input)
        c_layout.addSpacing(10)
        c_layout.addWidget(btn)

        layout.addWidget(lbl)
        layout.addWidget(card)
        layout.addStretch()
        return page

    def run_cleanup(self):
        d = self.days_input.text()
        if not d.isdigit(): return
        requests.post(f"{self.server_url}/admin/cleanup", headers=self.headers, params={"days": int(d)})
        QMessageBox.information(self, "Done", "Cleanup complete.")

if __name__ == "__main__":
    app = QApplication(sys.argv)

    # 1. Show Connection Dialog first
    conn_dialog = ConnectDialog()
    if conn_dialog.exec() == QDialog.Accepted:
        ip = conn_dialog.ip_input.text()
        port = conn_dialog.port_input.text()
        key = conn_dialog.key_input.text()

        url = f"http://{ip}:{port}"

        # 2. Launch Main Admin Panel
        window = AdminWindow(url, key)
        window.show()
        sys.exit(app.exec())
    else:
        sys.exit()
