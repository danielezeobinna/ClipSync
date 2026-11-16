import time
import socket
import threading
import pyperclip
import asyncio
import websockets
from pystray import Icon, Menu, MenuItem
from PIL import Image
import tkinter as tk
from tkinter import font as tkfont

HOST = "0.0.0.0"
PORT = 5765
last_clip = ""
ws_loop = None
ws_stop = None
clients = set()
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
    import sys
    import os
    if hasattr(sys, '_MEIPASS'):
        path = os.path.join(sys._MEIPASS, "logo.png")
    else:
        path = "logo.png"
    return Image.open(path)

async def ws_handler(ws):
    global last_clip
    clients.add(ws)
    try:
        if last_clip:
            await ws.send(last_clip)
        async for msg in ws:
            if isinstance(msg, str) and msg and msg != last_clip:
                last_clip = msg
                pyperclip.copy(msg)
    except websockets.ConnectionClosed:
        pass
    finally:
        clients.discard(ws)

async def broadcast(text):
    for ws in list(clients):
        try:
            await ws.send(text)
        except Exception:
            pass

def clipboard_monitor():
    global last_clip
    while True:
        if is_running:
            try:
                current = pyperclip.paste()
                if current != last_clip:
                    last_clip = current
                    if current and ws_loop and clients:
                        asyncio.run_coroutine_threadsafe(broadcast(current), ws_loop)
            except Exception:
                pass
        time.sleep(0.5)

async def _serve():
    global ws_stop
    ws_stop = asyncio.get_running_loop().create_future()
    async with websockets.serve(ws_handler, HOST, PORT, ping_interval=30, ping_timeout=15):
        await ws_stop

def start_server():
    global ws_loop
    ws_loop = asyncio.new_event_loop()
    asyncio.set_event_loop(ws_loop)
    ws_loop.run_until_complete(_serve())
    ws_loop = None

def do_start():
    global server_thread, is_running
    if not is_running:
        is_running = True
        server_thread = threading.Thread(target=start_server, daemon=True)
        server_thread.start()
        update_tray_menu()

def do_stop():
    global is_running
    if is_running:
        is_running = False
        if ws_loop and ws_stop:
            ws_loop.call_soon_threadsafe(lambda: ws_stop.done() or ws_stop.set_result(None))
        update_tray_menu()

def update_tray_menu():
    if tray_icon:
        local_ip = get_local_ip()
        status = "● Running" if is_running else "○ Stopped"
        toggle_label = "Stop Connection" if is_running else "Start Connection"
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

    import os, sys, ctypes
    if hasattr(sys, '_MEIPASS'):
        icon_path = os.path.join(sys._MEIPASS, "logo.ico")
    else:
        icon_path = "logo.ico"
    if os.path.exists(icon_path):
        window.iconbitmap(icon_path)
    ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID('danieleze.clipsync.1.0')

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
            toggle_btn.config(text="⏹️  Stop Connection", bg="#e74c3c",
                              fg="white", command=lambda: [do_stop(), refresh_button()])
        else:
            toggle_btn.config(text="▶️  Start Connection", bg="#1e90ff",
                              fg="white", command=lambda: [do_start(), refresh_button()])

    refresh_button()
    toggle_btn.pack(pady=4)

    tk.Label(window, text="Tray icon stays active when window is closed",
             font=tkfont.Font(family="Segoe UI", size=8),
             bg="#1a1a2e", fg="#555555").pack(pady=(16, 0))

    window.mainloop()

if __name__ == "__main__":
    import socket
    lock_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        lock_socket.bind(("localhost", 5766))
    except OSError:
        print("[ClipSync] Already running!")
        import sys
        sys.exit(0)
    
    import ctypes
    myappid = 'danieleze.clipsync.1.0'
    ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID(myappid)
    
    monitor_thread = threading.Thread(target=clipboard_monitor, daemon=True)
    monitor_thread.start()

    local_ip = get_local_ip()
    ip_label = MenuItem(f"Address: {local_ip}", None, enabled=False)
    toggle_item = MenuItem("Start Connection", on_tray_toggle)
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