"""
bred - YouTube to MP3 desktop app.
Only download audio you own or have permission to use.
"""
import os
import shutil
import sys
import threading
import tkinter as tk
from tkinter import filedialog, ttk

from yt_dlp import YoutubeDL


def resource(name):
    base = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
    return os.path.join(base, name)


def find_ffmpeg():
    import imageio_ffmpeg
    try:
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        path = shutil.which("ffmpeg")
        if path:
            return path
        raise


class QuietLogger:
    # A windowed app has no console, so yt-dlp must not write to stdout/stderr.
    def debug(self, msg): pass
    def info(self, msg): pass
    def warning(self, msg): pass
    def error(self, msg): pass


BG, FG, MUTED, FIELD = "#ffffff", "#161616", "#8a8a8a", "#f4f4f4"
ACCENT = "#b9772f"


class Bred(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("bred")
        self.geometry("460x360")
        self.resizable(False, False)
        self.configure(bg=BG)
        try:
            self.iconbitmap(resource("bred.ico"))
        except Exception:
            pass
        self.folder = os.path.join(os.path.expanduser("~"), "Music", "bred")

        try:
            self.logo = tk.PhotoImage(file=resource("bred.png")).subsample(3, 3)
            tk.Label(self, image=self.logo, bg=BG).pack(pady=(14, 0))
        except Exception:
            pass
        tk.Label(self, text="bred", bg=BG, fg=FG,
                 font=("Segoe UI", 24, "bold")).pack()
        tk.Label(self, text="paste a link, get the mp3", bg=BG, fg=MUTED,
                 font=("Segoe UI", 10)).pack(pady=(0, 12))

        self.url = tk.Entry(self, bg=FIELD, fg=FG, insertbackground=FG,
                            relief="flat", font=("Segoe UI", 12))
        self.url.pack(fill="x", padx=28, ipady=8)
        self.url.bind("<Return>", lambda e: self.start())
        self.url.focus()

        row = tk.Frame(self, bg=BG)
        row.pack(fill="x", padx=28, pady=12)
        self.q = tk.StringVar(value="192")
        ttk.Combobox(row, textvariable=self.q, values=["128", "192", "256", "320"],
                     width=5, state="readonly").pack(side="left")
        tk.Label(row, text="kbps", bg=BG, fg=MUTED).pack(side="left", padx=(4, 12))
        tk.Button(row, text="Folder", command=self.pick_folder, bg=FIELD, fg=FG,
                  relief="flat", padx=10).pack(side="left")
        self.go = tk.Button(row, text="Convert", command=self.start, bg=ACCENT, fg="white",
                            relief="flat", font=("Segoe UI", 11, "bold"), padx=18)
        self.go.pack(side="right")

        self.bar = ttk.Progressbar(self, mode="determinate", maximum=100)
        self.bar.pack(fill="x", padx=28, pady=(4, 8))
        self.status = tk.Label(self, text=f"Saves to {self.folder}", bg=BG, fg=MUTED,
                               wraplength=400, font=("Segoe UI", 9))
        self.status.pack()

    def pick_folder(self):
        d = filedialog.askdirectory(initialdir=self.folder)
        if d:
            self.folder = d
            self.say(f"Saves to {d}")

    def say(self, text, err=False):
        self.status.config(text=text, fg="#d33333" if err else MUTED)

    def start(self):
        url = self.url.get().strip()
        if not url.startswith(("http://", "https://")):
            self.say("Paste a valid link first.", err=True)
            return
        self.go.config(state="disabled")
        self.bar["value"] = 0
        self.say("Starting...")
        threading.Thread(target=self.work, args=(url, self.q.get()), daemon=True).start()

    def work(self, url, quality):
        try:
            ffmpeg = find_ffmpeg()
            os.makedirs(self.folder, exist_ok=True)

            def hook(d):
                if d["status"] == "downloading":
                    total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
                    if total:
                        self.after(0, self.bar.configure, {"value": d["downloaded_bytes"] / total * 90})
                    self.after(0, self.say, "Downloading...")
                elif d["status"] == "finished":
                    self.after(0, self.bar.configure, {"value": 95})
                    self.after(0, self.say, "Converting to mp3...")

            opts = {
                "format": "bestaudio/best",
                "outtmpl": os.path.join(self.folder, "%(title)s.%(ext)s"),
                "noplaylist": True,
                "quiet": True,
                "no_warnings": True,
                "logger": QuietLogger(),
                "ffmpeg_location": ffmpeg,
                "progress_hooks": [hook],
                "postprocessors": [
                    {"key": "FFmpegExtractAudio", "preferredcodec": "mp3",
                     "preferredquality": quality},
                    {"key": "FFmpegMetadata"},
                ],
            }
            with YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=True)
            self.after(0, self.bar.configure, {"value": 100})
            self.after(0, self.say, f"Done: {info.get('title', 'audio')}.mp3")
            self.after(0, self.url.delete, 0, "end")
        except Exception as e:
            msg = str(e).replace("ERROR: ", "")[:180] or "Something went wrong."
            self.after(0, self.say, msg, True)
        finally:
            self.after(0, self.go.config, {"state": "normal"})


if __name__ == "__main__":
    Bred().mainloop()
