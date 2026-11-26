using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Windows.Forms;

namespace IntraServerControlCenter {
    public class MainForm : Form {
        // Controls
        private NotifyIcon trayIcon;
        private ContextMenuStrip trayMenu;
        private Label lblStatusBadge;
        private Label lblIpInfo;
        private Label lblUptime;
        private Button btnCopyIp;
        private Button btnStart;
        private Button btnStop;
        private Button btnRestart;
        private Button btnAdmin;
        private Button btnChat;
        private Button btnUploads;
        private RichTextBox rtbConsole;
        private System.Windows.Forms.Timer uptimeTimer;

        // Process & Server state
        private Process serverProcess;
        private StreamWriter logWriter;
        private string baseDir;
        private string pythonExe;
        private string logFilePath;
        private string serverPort = "8000";
        private string cachedLanIp = "127.0.0.1";
        private DateTime serverStartTime;
        private bool isServerRunning = false;
        private bool isExplicitExit = false;

        public MainForm() {
            baseDir = AppDomain.CurrentDomain.BaseDirectory;
            cachedLanIp = GetLocalIpAddress();

            // Setup Log File in %LOCALAPPDATA%/IntraServer/logs/server.log
            string localApp = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string logDir = Path.Combine(localApp, "IntraServer", "logs");
            try { Directory.CreateDirectory(logDir); } catch {}
            logFilePath = Path.Combine(logDir, "server.log");

            pythonExe = ResolvePythonPath();

            InitializeComponent();
            InitializeTray();

            // Auto-start server on launch
            StartServer();
        }

        private string GetLocalIpAddress() {
            try {
                using (var socket = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, 0)) {
                    socket.Connect("8.8.8.8", 65530);
                    var endPoint = socket.LocalEndPoint as IPEndPoint;
                    if (endPoint != null) return endPoint.Address.ToString();
                }
            } catch {
                try {
                    var host = Dns.GetHostEntry(Dns.GetHostName());
                    foreach (var ip in host.AddressList) {
                        if (ip.AddressFamily == AddressFamily.InterNetwork && !ip.ToString().StartsWith("127.")) {
                            return ip.ToString();
                        }
                    }
                } catch {}
            }
            return "127.0.0.1";
        }

        private string ResolvePythonPath() {
            string p1 = Path.Combine(baseDir, "runtime", "python.exe");
            if (File.Exists(p1)) return p1;

            string p2 = Path.Combine(baseDir, "backend", "venv", "Scripts", "python.exe");
            if (File.Exists(p2)) return p2;

            return "python.exe";
        }

        private void InitializeComponent() {
            this.Text = "Intra Server - Cyber Deck Control Center";
            this.Size = new Size(760, 560);
            this.MinimumSize = new Size(680, 480);
            this.StartPosition = FormStartPosition.CenterScreen;
            this.BackColor = Color.FromArgb(16, 18, 26);
            this.ForeColor = Color.FromArgb(240, 240, 245);
            this.Font = new Font("Segoe UI", 9F, FontStyle.Regular);

            // Icon extraction
            string iconPath = Path.Combine(baseDir, "assets", "intraserver-256x256.ico");
            if (!File.Exists(iconPath)) iconPath = Path.Combine(baseDir, "icon.ico");
            if (File.Exists(iconPath)) {
                try { this.Icon = new Icon(iconPath); } catch {}
            } else {
                try { this.Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch {}
            }

            // 1. TOP STATUS PANEL
            Panel pnlTop = new Panel {
                Dock = DockStyle.Top,
                Height = 72,
                BackColor = Color.FromArgb(24, 27, 38),
                Padding = new Padding(16, 12, 16, 12)
            };

            lblStatusBadge = new Label {
                Text = "🟡 INITIALIZING...",
                Font = new Font("Segoe UI", 11F, FontStyle.Bold),
                ForeColor = Color.FromArgb(245, 158, 11),
                BackColor = Color.FromArgb(40, 32, 16),
                TextAlign = ContentAlignment.MiddleCenter,
                Size = new Size(190, 44),
                Location = new Point(16, 14)
            };

            lblIpInfo = new Label {
                Text = "📱 LAN: http://" + cachedLanIp + ":" + serverPort,
                Font = new Font("Consolas", 10.5F, FontStyle.Bold),
                ForeColor = Color.FromArgb(137, 180, 250),
                AutoSize = true,
                Location = new Point(220, 16)
            };

            lblUptime = new Label {
                Text = "⏱ Uptime: 00h : 00m : 00s",
                Font = new Font("Segoe UI", 9F, FontStyle.Regular),
                ForeColor = Color.FromArgb(166, 173, 200),
                AutoSize = true,
                Location = new Point(222, 38)
            };

            btnCopyIp = CreateToolbarButton("📋 Copy IP", Color.FromArgb(49, 50, 68), Color.White, (s, e) => {
                Clipboard.SetText("http://" + cachedLanIp + ":" + serverPort);
                ShowNotification("Copied!", "URL copied: http://" + cachedLanIp + ":" + serverPort);
            });
            btnCopyIp.Size = new Size(86, 26);
            btnCopyIp.Font = new Font("Segoe UI", 8.5F, FontStyle.Bold);
            btnCopyIp.Location = new Point(480, 14);

            pnlTop.Controls.Add(lblStatusBadge);
            pnlTop.Controls.Add(lblIpInfo);
            pnlTop.Controls.Add(lblUptime);
            pnlTop.Controls.Add(btnCopyIp);

            // 2. MIDDLE ACTION TOOLBAR
            Panel pnlToolbar = new Panel {
                Dock = DockStyle.Top,
                Height = 52,
                BackColor = Color.FromArgb(20, 23, 34),
                Padding = new Padding(12, 8, 12, 8)
            };

            FlowLayoutPanel flowButtons = new FlowLayoutPanel {
                Dock = DockStyle.Fill,
                FlowDirection = FlowDirection.LeftToRight,
                WrapContents = false,
                AutoScroll = false
            };

            btnStart = CreateToolbarButton("▶ Start", Color.FromArgb(16, 185, 129), Color.Black, (s, e) => StartServer());
            btnStop = CreateToolbarButton("⏹ Stop", Color.FromArgb(239, 68, 68), Color.White, (s, e) => StopServer());
            btnRestart = CreateToolbarButton("🔄 Restart", Color.FromArgb(245, 158, 11), Color.Black, (s, e) => RestartServer());
            btnAdmin = CreateToolbarButton("🌐 Web Admin", Color.FromArgb(124, 77, 255), Color.White, (s, e) => OpenUrl("http://localhost:" + serverPort + "/admin.html"));
            btnChat = CreateToolbarButton("💬 Web Chat", Color.FromArgb(59, 130, 246), Color.White, (s, e) => OpenUrl("http://localhost:" + serverPort + "/"));
            btnUploads = CreateToolbarButton("📁 Uploads", Color.FromArgb(69, 71, 90), Color.White, (s, e) => OpenUploadsFolder());

            flowButtons.Controls.Add(btnStart);
            flowButtons.Controls.Add(btnStop);
            flowButtons.Controls.Add(btnRestart);
            flowButtons.Controls.Add(btnAdmin);
            flowButtons.Controls.Add(btnChat);
            flowButtons.Controls.Add(btnUploads);
            pnlToolbar.Controls.Add(flowButtons);

            // 3. BOTTOM TERMINAL SCREEN (CRT Matrix Green)
            Panel pnlTerminalHeader = new Panel {
                Dock = DockStyle.Top,
                Height = 28,
                BackColor = Color.FromArgb(12, 14, 20),
                Padding = new Padding(12, 4, 12, 4)
            };

            Label lblTermTitle = new Label {
                Text = ">_ LIVE SERVER ACTIVITY (FASTAPI & UVICORN)",
                Font = new Font("Consolas", 8.5F, FontStyle.Bold),
                ForeColor = Color.FromArgb(0, 255, 102),
                AutoSize = true,
                Location = new Point(12, 6)
            };

            Button btnClearTerm = new Button {
                Text = "🧹 Clear",
                Font = new Font("Segoe UI", 7.5F),
                Size = new Size(60, 20),
                Location = new Point(pnlTerminalHeader.Width - 75, 4),
                Anchor = AnchorStyles.Top | AnchorStyles.Right,
                FlatStyle = FlatStyle.Flat,
                BackColor = Color.FromArgb(30, 30, 46),
                ForeColor = Color.FromArgb(166, 173, 200),
                Cursor = Cursors.Hand
            };
            btnClearTerm.FlatAppearance.BorderSize = 0;
            btnClearTerm.Click += (s, e) => rtbConsole.Clear();

            pnlTerminalHeader.Controls.Add(lblTermTitle);
            pnlTerminalHeader.Controls.Add(btnClearTerm);

            rtbConsole = new RichTextBox {
                Dock = DockStyle.Fill,
                BackColor = Color.FromArgb(8, 9, 13),
                ForeColor = Color.FromArgb(0, 255, 102),
                Font = new Font("Consolas", 9.5F, FontStyle.Regular),
                BorderStyle = BorderStyle.None,
                ReadOnly = true,
                WordWrap = false,
                ScrollBars = RichTextBoxScrollBars.Both
            };

            // Main Layout assembly (Reverse order for Dock)
            this.Controls.Add(rtbConsole);
            this.Controls.Add(pnlTerminalHeader);
            this.Controls.Add(pnlToolbar);
            this.Controls.Add(pnlTop);

            // 4. UPTIME TIMER
            uptimeTimer = new System.Windows.Forms.Timer { Interval = 1000 };
            uptimeTimer.Tick += (s, e) => {
                if (isServerRunning) {
                    TimeSpan elapsed = DateTime.Now - serverStartTime;
                    lblUptime.Text = string.Format("⏱ Uptime: {0:D2}h : {1:D2}m : {2:D2}s",
                        (int)elapsed.TotalHours, elapsed.Minutes, elapsed.Seconds);
                } else {
                    lblUptime.Text = "⏱ Uptime: Server Offline";
                }
            };
            uptimeTimer.Start();

            // Intercept close to dock into System Tray
            this.FormClosing += MainForm_FormClosing;
        }

        private Button CreateToolbarButton(string text, Color bgColor, Color fgColor, EventHandler onClick) {
            Button btn = new Button {
                Text = text,
                Size = new Size(108, 36),
                Margin = new Padding(0, 0, 8, 0),
                FlatStyle = FlatStyle.Flat,
                BackColor = bgColor,
                ForeColor = fgColor,
                Font = new Font("Segoe UI", 9F, FontStyle.Bold),
                Cursor = Cursors.Hand
            };
            btn.FlatAppearance.BorderSize = 0;
            btn.Click += onClick;
            return btn;
        }

        private void InitializeTray() {
            trayMenu = new ContextMenuStrip();

            var miOpen = new ToolStripMenuItem("🖥️ Open Control Center", null, (s, e) => RestoreWindow());
            miOpen.Font = new Font(miOpen.Font, FontStyle.Bold);

            var miAdmin = new ToolStripMenuItem("🌐 Open Web Admin", null, (s, e) => OpenUrl("http://localhost:" + serverPort + "/admin.html"));
            var miChat = new ToolStripMenuItem("💬 Open Web Chat", null, (s, e) => OpenUrl("http://localhost:" + serverPort + "/"));
            var miCopy = new ToolStripMenuItem("📱 Copy LAN IP: " + cachedLanIp, null, (s, e) => {
                Clipboard.SetText("http://" + cachedLanIp + ":" + serverPort);
                ShowNotification("Copied!", "URL: http://" + cachedLanIp + ":" + serverPort);
            });

            var miToggle = new ToolStripMenuItem("⏹ Stop Server", null, (s, e) => {
                if (isServerRunning) StopServer(); else StartServer();
            });

            var miExit = new ToolStripMenuItem("🚪 Exit Intra Completely", null, (s, e) => {
                isExplicitExit = true;
                StopServer();
                trayIcon.Visible = false;
                Application.Exit();
            });

            trayMenu.Items.Add(miOpen);
            trayMenu.Items.Add(new ToolStripSeparator());
            trayMenu.Items.Add(miAdmin);
            trayMenu.Items.Add(miChat);
            trayMenu.Items.Add(miCopy);
            trayMenu.Items.Add(new ToolStripSeparator());
            trayMenu.Items.Add(miToggle);
            trayMenu.Items.Add(miExit);

            Icon appIcon = this.Icon ?? SystemIcons.Application;
            trayIcon = new NotifyIcon {
                Icon = appIcon,
                ContextMenuStrip = trayMenu,
                Visible = true,
                Text = "Intra Server Control Center"
            };

            trayIcon.DoubleClick += (s, e) => RestoreWindow();
        }

        private void RestoreWindow() {
            this.Show();
            this.WindowState = FormWindowState.Normal;
            this.ShowInTaskbar = true;
            this.BringToFront();
        }

        private void MainForm_FormClosing(object sender, FormClosingEventArgs e) {
            if (!isExplicitExit && e.CloseReason == CloseReason.UserClosing) {
                e.Cancel = true;
                this.Hide();
                this.ShowInTaskbar = false;
                ShowNotification("Intra Server Running in Tray", "Server is still active in the background. Double-click tray icon to open.");
            }
        }

        private void ShowNotification(string title, string msg) {
            if (trayIcon != null) {
                trayIcon.ShowBalloonTip(2500, title, msg, ToolTipIcon.Info);
            }
        }

        // ================= SERVER LIFECYCLE =================
        private void StartServer() {
            if (isServerRunning && serverProcess != null && !serverProcess.HasExited) {
                AppendTerminal("⚠️ Server is already running!\n", Color.FromArgb(245, 158, 11));
                return;
            }

            string backendDir = Path.Combine(baseDir, "backend");
            string runScript = Path.Combine(backendDir, "run_server.py");

            ProcessStartInfo psi = new ProcessStartInfo {
                FileName = pythonExe,
                Arguments = "-u \"" + runScript + "\" --no-reload --host 0.0.0.0 --port " + serverPort,
                WorkingDirectory = backendDir,
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };
            psi.EnvironmentVariables["PYTHONIOENCODING"] = "utf-8";
            psi.EnvironmentVariables["PYTHONUTF8"] = "1";
            psi.EnvironmentVariables["PYTHONUNBUFFERED"] = "1";

            try {
                logWriter = new StreamWriter(logFilePath, true) { AutoFlush = true };
                logWriter.WriteLine("\n========================================================");
                logWriter.WriteLine("🚀 [LAUNCHER] Starting Intra Server at " + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss"));
                logWriter.WriteLine("========================================================");

                serverProcess = new Process { StartInfo = psi, EnableRaisingEvents = true };

                serverProcess.OutputDataReceived += (s, e) => {
                    if (e.Data != null) {
                        try { logWriter.WriteLine(e.Data); } catch {}
                        this.BeginInvoke(new Action(() => AppendTerminal(e.Data + "\n", Color.FromArgb(0, 255, 102))));
                    }
                };

                serverProcess.ErrorDataReceived += (s, e) => {
                    if (e.Data != null) {
                        try { logWriter.WriteLine("[STDERR] " + e.Data); } catch {}
                        this.BeginInvoke(new Action(() => AppendTerminal("[ERR] " + e.Data + "\n", Color.FromArgb(239, 68, 68))));
                    }
                };

                serverProcess.Exited += (s, e) => {
                    int code = -1;
                    try { code = serverProcess.ExitCode; } catch {}
                    try {
                        logWriter.WriteLine("🛑 [LAUNCHER] Server exited with code " + code);
                        logWriter.Close();
                    } catch {}

                    this.BeginInvoke(new Action(() => {
                        SetServerState(false, code);
                    }));
                };

                serverProcess.Start();
                serverProcess.BeginOutputReadLine();
                serverProcess.BeginErrorReadLine();

                serverStartTime = DateTime.Now;
                SetServerState(true, 0);

                AppendTerminal("🚀 [SYSTEM] Intra Server launched successfully on http://" + cachedLanIp + ":" + serverPort + "\n", Color.FromArgb(137, 180, 250));
            } catch (Exception ex) {
                AppendTerminal("❌ [ERROR] Failed to start server: " + ex.Message + "\n", Color.FromArgb(239, 68, 68));
                SetServerState(false, -1);
            }
        }

        private void StopServer() {
            if (serverProcess != null && !serverProcess.HasExited) {
                try {
                    AppendTerminal("⏹ [SYSTEM] Stopping server process...\n", Color.FromArgb(245, 158, 11));
                    serverProcess.Kill();
                    serverProcess.WaitForExit(2000);
                } catch {}
            }
            serverProcess = null;
            SetServerState(false, 0);
        }

        private void RestartServer() {
            StopServer();
            System.Threading.Thread.Sleep(800);
            StartServer();
        }

        private void SetServerState(bool running, int exitCode) {
            isServerRunning = running;
            if (running) {
                lblStatusBadge.Text = "🟢 ONLINE";
                lblStatusBadge.ForeColor = Color.FromArgb(16, 185, 129);
                lblStatusBadge.BackColor = Color.FromArgb(19, 47, 38);
                btnStart.Enabled = false;
                btnStop.Enabled = true;
                btnRestart.Enabled = true;
                if (trayMenu != null && trayMenu.Items.Count > 6) {
                    trayMenu.Items[6].Text = "⏹ Stop Server";
                }
            } else {
                lblStatusBadge.Text = (exitCode == 0) ? "🔴 OFFLINE" : "⚠️ CRASHED (" + exitCode + ")";
                lblStatusBadge.ForeColor = Color.FromArgb(239, 68, 68);
                lblStatusBadge.BackColor = Color.FromArgb(58, 24, 24);
                btnStart.Enabled = true;
                btnStop.Enabled = false;
                btnRestart.Enabled = false;
                if (trayMenu != null && trayMenu.Items.Count > 6) {
                    trayMenu.Items[6].Text = "▶ Start Server";
                }
            }
        }

        private void AppendTerminal(string text, Color color) {
            try {
                if (rtbConsole.IsDisposed) return;
                // Buffer limit: Keep under 100,000 chars to avoid memory bloat
                if (rtbConsole.TextLength > 100000) {
                    rtbConsole.Clear();
                }
                rtbConsole.SelectionStart = rtbConsole.TextLength;
                rtbConsole.SelectionLength = 0;
                rtbConsole.SelectionColor = color;
                rtbConsole.AppendText(text);
                rtbConsole.SelectionColor = rtbConsole.ForeColor;
                rtbConsole.ScrollToCaret();
            } catch {}
        }

        private void OpenUploadsFolder() {
            string localApp = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string uploadsDir = Path.Combine(localApp, "IntraServer", "uploads");
            if (!Directory.Exists(uploadsDir)) {
                uploadsDir = Path.Combine(baseDir, "backend", "uploads");
            }
            try {
                Directory.CreateDirectory(uploadsDir);
                Process.Start("explorer.exe", uploadsDir);
            } catch {}
        }

        private void OpenUrl(string url) {
            try {
                Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
            } catch {}
        }
    }

    static class Program {
        [STAThread]
        static void Main() {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }
}
