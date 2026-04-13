import time
import socket
import threading
import pyperclip # type: ignore
from http.server import HTTPServer, BaseHTTPRequestHandler
from pystray import Icon, Menu, MenuItem # type: ignore
from PIL import Image, ImageDraw # type: ignore
import tkinter as tk
from tkinter import font as tkfont

HOST = "0.0.0.0"
PORT = 5765
last_clip = ""
server_instance = None
server_thread = None
is_running = False
tray_icon = None

def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    finally:
        s.close()

def create_tray_icon():
    img = Image.new("RGB", (64, 64), color=(30, 144, 255))
    draw = ImageDraw.Draw(img)
    draw.rectangle([16, 20, 48, 44], fill="white")
    draw.polygon([(24, 44), (40, 44), (32, 56)], fill="white")
    return img

class ClipHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/clip":
            content = last_clip.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(content)))
            self.end_headers()
            self.wfile.write(content)
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path == "/clip":
            length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(length).decode("utf-8")
            pyperclip.copy(body)
            self.send_response(200)
            self.end_headers()
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        pass

def clipboard_monitor():
    global last_clip
    while True:
        if is_running:
            try:
                current = pyperclip.paste()
                if current != last_clip:
                    last_clip = current
            except Exception:
                pass
        time.sleep(0.5)

def start_server():
    global server_instance
    server_instance = HTTPServer((HOST, PORT), ClipHandler)
    server_instance.serve_forever()

def do_start():
    global server_thread, is_running
    if not is_running:
        is_running = True
        server_thread = threading.Thread(target=start_server, daemon=True)
        server_thread.start()
        update_tray_menu()

def do_stop():
    global is_running, server_instance
    if is_running:
        is_running = False
        if server_instance:
            threading.Thread(target=server_instance.shutdown, daemon=True).start()
            server_instance = None
        update_tray_menu()

def update_tray_menu():
    if tray_icon:
        local_ip = get_local_ip()
        status = "● Running" if is_running else "○ Stopped"
        toggle_label = "Stop Sharing" if is_running else "Start Sharing"
        tray_icon.menu = Menu(
            MenuItem(f"ClipSync  —  {status}", None, enabled=False),
            MenuItem(f"Address: {local_ip}", None, enabled=False),
            MenuItem("Open ClipSync", on_open_window, default=True),
            MenuItem(toggle_label, on_tray_toggle),
            MenuItem("Quit", on_quit),
        )

def on_tray_toggle(icon, item):
    if is_running:
        do_stop()
    else:
        do_start()

def on_quit(icon, item):
    do_stop()
    icon.stop()
    import os
    os._exit(0)

window_instance = None

def on_open_window(icon=None, item=None):
    threading.Thread(target=_open_window_thread, daemon=True).start()

def _open_window_thread():
    global window_instance
    try:
        if window_instance and window_instance.winfo_exists():
            window_instance.lift()
            window_instance.focus_force()
            return
    except Exception:
        pass
    window_instance = tk.Tk()
    window = window_instance

    window.title("ClipSync")
    window.geometry("360x220")
    window.resizable(False, False)
    window.configure(bg="#1a1a2e")

    local_ip = get_local_ip()

    title_font = tkfont.Font(family="Segoe UI", size=16, weight="bold")
    label_font = tkfont.Font(family="Segoe UI", size=10)
    btn_font = tkfont.Font(family="Segoe UI", size=11, weight="bold")

    tk.Label(window, text="ClipSync", font=title_font,
             bg="#1a1a2e", fg="white").pack(pady=(20, 4))

    tk.Label(window, text=f"Connect Android to:", font=label_font,
             bg="#1a1a2e", fg="#aaaaaa").pack()
    
    tk.Label(window, text=f"{local_ip}", font=label_font,
             bg="#1a1a2e", fg="#1e90ff").pack(pady=(2, 16))

    toggle_btn = tk.Button(window, width=18, font=btn_font,
                           bd=0, cursor="hand2", relief="flat")

    def refresh_button():
        if is_running:
            toggle_btn.config(text="⏹️  Stop Sharing", bg="#e74c3c",
                              fg="white", command=lambda: [do_stop(), refresh_button()])
        else:
            toggle_btn.config(text="▶️  Start Sharing", bg="#1e90ff",
                              fg="white", command=lambda: [do_start(), refresh_button()])

    refresh_button()
    toggle_btn.pack(pady=4)

    tk.Label(window, text="Tray icon stays active when window is closed",
             font=tkfont.Font(family="Segoe UI", size=8),
             bg="#1a1a2e", fg="#555555").pack(pady=(16, 0))

    window.mainloop()

if __name__ == "__main__":
    monitor_thread = threading.Thread(target=clipboard_monitor, daemon=True)
    monitor_thread.start()

    local_ip = get_local_ip()
    ip_label = MenuItem(f"Address: {local_ip}", None, enabled=False)
    toggle_item = MenuItem("Start Sharing", on_tray_toggle)
    quit_item = MenuItem("Quit", on_quit)

    tray_icon = Icon("ClipSync", create_tray_icon(), "ClipSync",
                     Menu(
                         ip_label,
                         MenuItem("Open ClipSync", on_open_window, default=True),
                         toggle_item,
                         quit_item
                     ))

    # Open window on first launch
    tray_icon.run_detached()
on_open_window()