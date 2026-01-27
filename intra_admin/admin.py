import sys
import requests

from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget,
    QVBoxLayout, QHBoxLayout, QLabel,
    QPushButton, QTabWidget, QTableWidget,
    QTableWidgetItem, QMessageBox
)
from PySide6.QtGui import QPixmap
from PySide6.QtCore import Qt


# ================= CONFIG =================
#SERVER_URL = "http://127.0.0.1:8000"   # agar Windows se run -> LAN IP daal
SERVER_URL = "http://192.168.31.104:8000"   # agar Windows se run -> LAN IP daal
ADMIN_KEY = "INTRA_ADMIN_123"


class AdminWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Intra Admin Panel")
        self.resize(1000, 600)

        tabs = QTabWidget()

        # ================= USERS TAB =================
        self.users_tab = QWidget()
        users_layout = QVBoxLayout()

        title = QLabel("👥 User Management")
        title.setStyleSheet("font-size:18px; font-weight:bold;")

        self.users_table = QTableWidget()
        self.users_table.setColumnCount(2)
        self.users_table.setHorizontalHeaderLabels(["Profile", "Username"])
        self.users_table.setColumnWidth(0, 80)
        self.users_table.horizontalHeader().setStretchLastSection(True)
        self.users_table.setSelectionBehavior(QTableWidget.SelectRows)
        self.users_table.setEditTriggers(QTableWidget.NoEditTriggers)

        btn_layout = QHBoxLayout()
        load_btn = QPushButton("🔄 Load Users")
        delete_btn = QPushButton("❌ Delete Selected User")

        load_btn.clicked.connect(self.load_users)
        delete_btn.clicked.connect(self.delete_user)

        btn_layout.addWidget(load_btn)
        btn_layout.addWidget(delete_btn)
        btn_layout.addStretch()

        users_layout.addWidget(title)
        users_layout.addWidget(self.users_table)
        users_layout.addLayout(btn_layout)

        self.users_tab.setLayout(users_layout)

        # ================= CLEANUP TAB =================
        cleanup_tab = QWidget()
        cleanup_layout = QVBoxLayout()
        cleanup_layout.addWidget(QLabel("🧹 Cleanup (next step)"))
        cleanup_tab.setLayout(cleanup_layout)

        # ================= SERVER TAB =================
        server_tab = QWidget()
        server_layout = QVBoxLayout()
        server_layout.addWidget(QLabel("⚙️ Server Controls (next step)"))
        server_tab.setLayout(server_layout)

        tabs.addTab(self.users_tab, "Users")
        tabs.addTab(cleanup_tab, "Cleanup")
        tabs.addTab(server_tab, "Server")

        self.setCentralWidget(tabs)

        # auto load
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
                # Profile photo
                photo_label = QLabel()
                photo_label.setFixedSize(48, 48)
                photo_label.setAlignment(Qt.AlignCenter)

                if user.get("profile_photo"):
                    pixmap = QPixmap(SERVER_URL + user["profile_photo"])
                    photo_label.setPixmap(
                        pixmap.scaled(
                            48, 48,
                            Qt.KeepAspectRatio,
                            Qt.SmoothTransformation
                        )
                    )

                self.users_table.setCellWidget(row, 0, photo_label)

                # Username
                self.users_table.setItem(
                    row, 1,
                    QTableWidgetItem(user["username"])
                )

        except Exception as e:
            QMessageBox.critical(self, "Error", str(e))

    # ================= DELETE USER =================
    def delete_user(self):
        row = self.users_table.currentRow()
        if row < 0:
            QMessageBox.warning(self, "Select User", "Please select a user first.")
            return

        username = self.users_table.item(row, 1).text()

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


# ================= MAIN =================
if __name__ == "__main__":
    app = QApplication(sys.argv)
    win = AdminWindow()
    win.show()
    sys.exit(app.exec())
