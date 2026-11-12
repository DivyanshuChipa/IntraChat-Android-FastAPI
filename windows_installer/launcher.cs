using System;
using System.Diagnostics;
using System.IO;

class Program {
    static void Main(string[] args) {
        string baseDir = AppDomain.CurrentDomain.BaseDirectory;
        
        // Priority 1: Isolated Embedded Python Runtime (dist/runtime/python.exe)
        string pythonExe = Path.Combine(baseDir, "runtime", "python.exe");
        
        // Priority 2: Project venv (dist/backend/venv/Scripts/python.exe)
        if (!File.Exists(pythonExe)) {
            pythonExe = Path.Combine(baseDir, "backend", "venv", "Scripts", "python.exe");
        }
        
        // Priority 3: System Python
        if (!File.Exists(pythonExe)) {
            pythonExe = "python.exe";
        }
        
        string scriptPath = Path.Combine(baseDir, "systemctlmanger", "server_gui_windows.py");
        
        ProcessStartInfo psi = new ProcessStartInfo();
        psi.FileName = pythonExe;
        psi.Arguments = "\"" + scriptPath + "\"";
        psi.WorkingDirectory = baseDir;
        psi.UseShellExecute = false;
        psi.CreateNoWindow = true; // Completely silent - no black command prompt window!
        
        try {
            Process.Start(psi);
        } catch (Exception) {
            psi.UseShellExecute = true;
            Process.Start(psi);
        }
    }
}
