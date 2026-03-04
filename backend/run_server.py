import os
import socket
import subprocess
import sys

def get_ip():
    """Computer ka local IP address nikalne ke liye"""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('8.8.8.8', 1))
        IP = s.getsockname()[0]
    except Exception:
        IP = '127.0.0.1'
    finally:
        s.close()
    return IP

def main():
    print("=======================================")
    print("   INTRA - BACKEND SERVER STARTER      ")
    print("=======================================")
    
    local_ip = get_ip()
    port = 8000
    
    print(f"\n🚀 Server starting on: http://{local_ip}:{port}")
    print(f"📱 Android App me ye IP daalein: {local_ip}")
    print("---------------------------------------")

    # Check if uvicorn is installed
    try:
        import uvicorn
    except ImportError:
        print("📦 Installing missing libraries... please wait.")
        subprocess.check_call([sys.executable, "-m", "pip", "install", "-r", "requirements.txt"])
        print("✅ Libraries installed successfully!\n")

    # Run the server
    import uvicorn
    uvicorn.run("server:app", host="0.0.0.0", port=port, reload=True)

if __name__ == "__main__":
    main()
