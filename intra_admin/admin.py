import sys
import os
import requests

from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget,
    QVBoxLayout, QHBoxLayout, QLabel,
    QPushButton, QTabWidget, QTableWidget,
    QTableWidgetItem, QMessageBox,
    QInputDialog, QLineEdit
)
from PySide6.QtGui import QPixmap, QFont
from PySide6.QtCore import Qt


# ================= CONFIG =================
SERVER_URL = "http://192.168.31.104:8000"
ADMIN_KEY = os.getenv("INTRA_ADMIN_KEY", "INTRA_ADMIN_123")


class AdminWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Intra Admin Panel")
        self.resize(1050, 650)

        tabs = QTabWidget()

        # ================= USERS TAB =================
        self.users_tab = QWidget()
        users_layout = QVBoxLayout()

        title = QLabel("👥 User Management")
        title.setStyleSheet("font-size:20px; font-weight:bold;")

        self.users_table = QTableWidget()
        self.users_table.setColumnCount(2)
        self.users_table.setHorizontalHeaderLabels(["Profile", "Username"])
        self.users_table.setColumnWidth(0, 90)
        self.users_table.horizontalHeader().setStretchLastSection(True)
        self.users_table.setSelectionBehavior(QTableWidget.SelectRows)
        self.users_table.setEditTriggers(QTableWidget.NoEditTriggers)
        self.users_table.setShowGrid(False)
        self.users_table.setAlternatingRowColors(True)
        self.users_table.verticalHeader().setDefaultSectionSize(80)

        self.users_table.setStyleSheet("""
        QTableWidget {
            background-color: #1e1e1e;
            color: white;
            font-size: 15px;
            alternate-background-color: #252525;
        }
        QHeaderView::section {
            background-color: #2d2d2d;
            padding: 8px;
            font-weight: bold;
            font-size: 14px;
        }
        QTableWidget::item:selected {
            background-color: #3a3a3a;
        }
        """)

        btn_layout = QHBoxLayout()

        load_btn = QPushButton("🔄 Load Users")
        reset_btn = QPushButton("🔐 Reset Password")
        delete_btn = QPushButton("❌ Delete User")

        load_btn.clicked.connect(self.load_users)
        reset_btn.clicked.connect(self.reset_password)
        delete_btn.clicked.connect(self.delete_user)

        btn_layout.addWidget(load_btn)
        btn_layout.addWidget(reset_btn)
        btn_layout.addWidget(delete_btn)
        btn_layout.addStretch()

        users_layout.addWidget(title)
        users_layout.addWidget(self.users_table)
        users_layout.addLayout(btn_layout)

        self.users_tab.setLayout(users_layout)

        # ================= CLEANUP TAB =================
        cleanup_tab = QWidget()
        cleanup_layout = QVBoxLayout()

        cleanup_title = QLabel("🧹 Server Maintenance")
        cleanup_title.setStyleSheet("font-size:20px; font-weight:bold;")

        cleanup_desc = QLabel("Delete messages older than X days")

        self.days_input = QLineEdit()
        self.days_input.setPlaceholderText("Enter days (e.g. 30)")

        cleanup_btn = QPushButton("🗑️ Run Cleanup")
        cleanup_btn.clicked.connect(self.run_cleanup)

        cleanup_layout.addWidget(cleanup_title)
        cleanup_layout.addWidget(cleanup_desc)
        cleanup_layout.addWidget(self.days_input)
        cleanup_layout.addWidget(cleanup_btn)
        cleanup_layout.addStretch()

        cleanup_tab.setLayout(cleanup_layout)

        # ================= SERVER TAB =================
        server_tab = QWidget()
        server_layout = QVBoxLayout()
        server_layout.addWidget(QLabel("⚙️ Server Controls (coming soon)"))
        server_tab.setLayout(server_layout)

        tabs.addTab(self.users_tab, "Users")
        tabs.addTab(cleanup_tab, "Cleanup")
        tabs.addTab(server_tab, "Server")

        self.setCentralWidget(tabs)

        self.load_users()

    # ================= LOAD USERS =================
    def load_users(self):
        try:
            res = requests.get(
                f"{SERVER_URL}/admin/users",
                headers={"X-ADMIN-KEY": ADMIN_KEY},
                timeout=5

            )
            data = res.json()

            if not data.get("success"):
                raise Exception("Admin API failed")

            users = data.get("users", [])
            self.users_table.setRowCount(len(users))
            for row, user in enumerate(users):
                is_online = user.get("is_online", False)
                # -------- PROFILE PHOTO --------
                photo_wrapper = QWidget()
                photo_wrapper.setFixedSize(70, 70)

                photo_label = QLabel(photo_wrapper)
                photo_label.setFixedSize(64, 64)
                photo_label.move(3, 3)
                photo_label.setAlignment(Qt.AlignCenter)

                pixmap = QPixmap()
                if user.get("profile_photo"):
                    try:
                        r = requests.get(SERVER_URL + user["profile_photo"], timeout=3)
                        if r.status_code == 200:
                            pixmap.loadFromData(r.content)
                    except:
                        pass

                if pixmap.isNull():
                    photo_label.setText("👤")
                    photo_label.setStyleSheet(
                        "font-size:26px; background:#444; border-radius:32px;"
                    )
                else:
                    photo_label.setPixmap(
                        pixmap.scaled(64, 64, Qt.KeepAspectRatio, Qt.SmoothTransformation)
                    )
                    photo_label.setStyleSheet("border-radius:32px;")

                # 🟢 GREEN DOT
                if is_online:
                    dot = QLabel(photo_wrapper)
                    dot.setFixedSize(14, 14)
                    dot.move(46, 46)
                    dot.setStyleSheet(
                        "background:#00ff88; border-radius:7px; border:2px solid #1e1e1e ( ;"
                    )

                self.users_table.setCellWidget(row, 0, photo_wrapper)

                # -------- USERNAME --------
                username = user.get("username", "unknown")
                item = QTableWidgetItem("  " + username)
                item.setFont(self.get_bold_font(17))
                item.setFlags(Qt.ItemIsSelectable | Qt.ItemIsEnabled)
                self.users_table.setItem(row, 1, item)

        except Exception as e:
            QMessageBox.critical(self, "Error", str(e))

    # ================= DELETE USER =================
    def delete_user(self):
        row = self.users_table.currentRow()
        if row < 0:
            QMessageBox.warning(self, "Select User", "Please select a user first.")
            return

        username = self.users_table.item(row, 1).text().strip()

        confirm = QMessageBox.question(
            self,
            "Confirm Delete",
            f"Delete user '{username}' permanently?",
            QMessageBox.Yes | QMessageBox.No
        )

        if confirm != QMessageBox.Yes:
            return

        try:
            res = requests.post(
                f"{SERVER_URL}/admin/delete_user",
                headers={"X-ADMIN-KEY": ADMIN_KEY},
                params={"username": username},
                timeout=5
            )
            data = res.json()

            if not data.get("success"):
                raise Exception(data.get("message", "Delete failed"))

            QMessageBox.information(self, "Deleted", f"User '{username}' deleted.")
            self.load_users()

        except Exception as e:
            QMessageBox.critical(self, "Error", str(e))

    # ================= RESET PASSWORD =================
    def reset_password(self):
        row = self.users_table.currentRow()
        if row < 0:
            QMessageBox.warning(self, "Select User", "Please select a user first.")
            return

        username = self.users_table.item(row, 1).text().strip()

        new_pass, ok = QInputDialog.getText(
            self,
            "Reset Password",
            f"Enter NEW password for '{username}':",
            QLineEdit.Password
        )

        if not ok or not new_pass:
            return

        try:
            res = requests.post(
                f"{SERVER_URL}/admin/reset_password",
                headers={"X-ADMIN-KEY": ADMIN_KEY},
                params={"username": username, "new_pass": new_pass},
                timeout=5
            )
            data = res.json()

            if not data.get("success"):
                raise Exception(data.get("message", "Reset failed"))

            QMessageBox.information(self, "Success", "Password reset successfully.")

        except Exception as e:
            QMessageBox.critical(self, "Error", str(e))

    # ================= CLEANUP =================
    def run_cleanup(self):
        days_str = self.days_input.text()

        if not days_str.isdigit():
            QMessageBox.warning(self, "Invalid Input", "Enter valid number of days.")
            return

        days = int(days_str)

        confirm = QMessageBox.question(
            self,
            "Confirm Cleanup",
            f"Delete messages older than {days} days?",
            QMessageBox.Yes | QMessageBox.No
        )

        if confirm != QMessageBox.Yes:
            return

        try:
            res = requests.post(
                f"{SERVER_URL}/admin/cleanup",
                headers={"X-ADMIN-KEY": ADMIN_KEY},
                params={"days": days},
                timeout=10
            )
            data = res.json()

            if not data.get("success"):
                raise Exception("Cleanup failed")

            QMessageBox.information(
                self,
                "Cleanup Done",
                f"Deleted {data.get('deleted_messages', 0)} messages."
            )

        except Exception as e:
            QMessageBox.critical(self, "Error", str(e))

    # ================= FONT HELPER =================
    def get_bold_font(self, size):
        font = QFont("Segoe UI", size)
        font.setBold(True)
        return font


# ================= MAIN =================
if __name__ == "__main__":
    app = QApplication(sys.argv)
    win = AdminWindow()
    win.show()
    sys.exit(app.exec())
