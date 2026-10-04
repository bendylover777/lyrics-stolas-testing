"""Stolas Lyrics bridge: сам видит, что играет в Spotify, и пишет это в bridge.json для мода.

Тексты песен берутся автоматически с бесплатного сервиса LRCLIB (ключи не нужны)
и сохраняются в lyrics.json с таймингами. Нужен интернет.

Установка (один раз):  pip install winsdk
Запуск: двойной клик по start_bridge.bat (или: python bridge.py)

В окне cmd показывается панель: статус, что играет, статистика и журнал.
"""
import asyncio
import collections
import datetime
import json
import os
import re
import sys
import threading
import time
import urllib.parse
import urllib.request

# Папка конфига мода. Если у тебя другая сборка/лаунчер, поменяй путь.
CONFIG_DIR = os.path.join(os.environ.get("APPDATA", "."), ".minecraft", "config", "evolyrics")
BRIDGE_FILE = os.path.join(CONFIG_DIR, "bridge.json")
SONGS_DIR = os.path.join(CONFIG_DIR, "songs")
LOG_FILE = os.path.join(CONFIG_DIR, "bridge.log")
SETTINGS_FILE = os.path.join(CONFIG_DIR, "settings.json")  # настройки мода (для статуса)
DASHBOARD = True          # красивая панель в cmd (False = обычный текст)
INTERVAL = 0.2          # как часто обновлять файл, секунды
AUTO_CREATE_SONGS = True  # создавать папку песни, когда она играет впервые
AUTO_LYRICS = True        # сами скачивать текст с LRCLIB
WORDS_PER_CHUNK = 2       # сколько слов показывать за раз (1 = по одному слову)
MAX_LINE_SECONDS = 5.0    # максимум времени на одну строку текста
EFFECTS = ["FADE", "RISE", "SCALE_IN", "SLIDE_LEFT", "SLIDE_RIGHT", "POP", "FLOAT", "ROTATE"]

from winsdk.windows.media.control import (
    GlobalSystemMediaTransportControlsSessionManager as SessionManager,
    GlobalSystemMediaTransportControlsSessionPlaybackStatus as PlaybackStatus,
)


_last_log = [None]
DASH_ON = [False]
APP_VERSION = "v3.2"
START = time.time()
EVENTS = collections.deque(maxlen=6)
LYRICS_STATUS = {}  # (title, artist) -> ("search" | "ok" | "have" | "none" | "off", кол-во строк)
STATE = {
    "spotify": "none",  # none / playing / paused
    "title": "", "artist": "", "pos": 0.0, "length": 0.0,
    "tracks": 0, "downloaded": 0, "had_own": 0, "not_found": 0, "write_errors": 0,
}


def log(*args):
    """Пишет событие в журнал панели и в bridge.log (нужно, когда окна нет, например в .exe)."""
    msg = " ".join(str(a) for a in args)
    if msg == _last_log[0]:
        return  # одинаковые подряд не пишем, чтобы не раздувать лог
    _last_log[0] = msg
    EVENTS.append((time.strftime("%H:%M:%S"), msg))
    if not DASH_ON[0]:
        try:
            print(msg)
        except Exception:
            pass
    try:
        os.makedirs(CONFIG_DIR, exist_ok=True)
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            f.write(time.strftime("%H:%M:%S ") + msg + "\n")
    except OSError:
        pass


# ---------- панель ----------
_C = {"g": "\033[92m", "r": "\033[91m", "y": "\033[93m", "c": "\033[96m",
      "d": "\033[90m", "b": "\033[1m", "m": "\033[95m", "0": "\033[0m"}


def col(code, text):
    return _C[code] + text + _C["0"]


LOGO = [
    "███████╗████████╗ ██████╗ ██╗      █████╗ ███████╗",
    "██╔════╝╚══██╔══╝██╔═══██╗██║     ██╔══██╗██╔════╝",
    "███████╗   ██║   ██║   ██║██║     ███████║███████╗",
    "╚════██║   ██║   ██║   ██║██║     ██╔══██║╚════██║",
    "███████║   ██║   ╚██████╔╝███████╗██║  ██║███████║",
    "╚══════╝   ╚═╝    ╚═════╝ ╚══════╝╚═╝  ╚═╝╚══════╝",
]


def c256(n, text):
    return f"\033[38;5;{n}m{text}\033[0m"


_lines_cache = {"key": None, "rows": []}


def current_line():
    """Строка текста, которая сейчас должна быть на экране (по lyrics.json песни)."""
    key = (STATE["title"], STATE["artist"], LYRICS_STATUS.get((STATE["title"], STATE["artist"])))
    if key != _lines_cache["key"]:
        rows = []
        try:
            with open(os.path.join(song_folder(STATE["title"], STATE["artist"]), "lyrics.json"), encoding="utf-8") as f:
                rows = [(float(x["time"]), x["text"]) for x in json.load(f).get("lyrics", [])]
            rows.sort(key=lambda r: r[0])
        except Exception:
            rows = []
        _lines_cache["key"], _lines_cache["rows"] = key, rows
    cur = ""
    for t, text in _lines_cache["rows"]:
        if t <= STATE["pos"]:
            cur = text
        else:
            break
    return cur


def clip(text, n):
    return text if len(text) <= n else text[: n - 1] + "…"


def fmt_time(sec):
    sec = int(max(0, sec))
    return f"{sec // 60:02d}:{sec % 60:02d}"


def fmt_uptime(sec):
    sec = int(sec)
    return f"{sec // 3600:02d}:{sec % 3600 // 60:02d}:{sec % 60:02d}"


def bar(pos, length, width=30):
    if length <= 0:
        return "░" * width
    filled = int(width * min(1.0, pos / length))
    return "█" * filled + "░" * (width - filled)


def onoff(value):
    if value is True:
        return col("g", "● ВКЛЮЧЕНО")
    if value is False:
        return col("r", "● ОТКЛЮЧЕНО")
    return col("d", "● нет данных (открой меню K в игре)")


_mod_cache = [0.0, {}]
_songs_cache = [0.0, 0]


def mod_settings():
    """Читает settings.json мода, чтобы показать, включено ли отображение в игре."""
    now = time.time()
    if now - _mod_cache[0] > 1.0:
        _mod_cache[0] = now
        try:
            with open(SETTINGS_FILE, encoding="utf-8") as f:
                _mod_cache[1] = json.load(f)
        except Exception:
            _mod_cache[1] = {}
    return _mod_cache[1]


def songs_count():
    now = time.time()
    if now - _songs_cache[0] > 5.0:
        _songs_cache[0] = now
        try:
            _songs_cache[1] = sum(1 for d in os.scandir(SONGS_DIR) if d.is_dir())
        except OSError:
            _songs_cache[1] = 0
    return _songs_cache[1]


def lyrics_status_text():
    st = LYRICS_STATUS.get((STATE["title"], STATE["artist"]))
    if not STATE["title"]:
        return col("d", "—")
    if st is None:
        return col("d", "—")
    kind, n = st
    if kind == "search":
        return col("y", "● ищу текст...")
    if kind == "ok":
        return col("g", f"● скачан, строк: {n}")
    if kind == "have":
        return col("g", f"● уже есть, строк: {n}")
    if kind == "none":
        return col("r", "● не найден (будет только остров)")
    return col("d", "● автозагрузка выключена")


def build_frame():
    inner = 52
    s = mod_settings()
    sp = STATE["spotify"]
    sp_text = {"playing": col("g", "● играет"), "paused": col("y", "● пауза")}.get(sp, col("r", "● не найден (запусти Spotify)"))
    src = s.get("useBridge")
    if src is True:
        src_text = col("g", "Bridge")
    elif src is False:
        src_text = col("y", "Timer — в меню K выбери Source: Bridge")
    else:
        src_text = col("d", "нет данных")

    L = []
    L.append("")
    for row, shade in zip(LOGO, (93, 129, 165, 171, 207, 213)):
        L.append("  " + c256(shade, row))
    L.append("  " + col("d", f"L Y R I C S   ·   bridge {APP_VERSION}   ·   by Stolas"))
    L.append("")
    L.append(" " + col("c", "СТАТУС"))
    L.append(f"   Мост              {col('g', '● ВКЛЮЧЁН')}")
    L.append(f"   Spotify           {sp_text}")
    L.append(f"   Lyrics в игре     {onoff(s.get('enabled'))}")
    L.append(f"   Остров в игре     {onoff(s.get('island'))}")
    L.append(f"   Источник в моде   {src_text}")
    L.append("")
    L.append(" " + col("c", "СЕЙЧАС ИГРАЕТ"))
    if STATE["title"]:
        name = f"{STATE['artist']} — {STATE['title']}" if STATE["artist"] else STATE["title"]
        L.append("   " + col("b", clip(name, 70)))
        L.append(f"   {col('m', bar(STATE['pos'], STATE['length']))}  {fmt_time(STATE['pos'])} / {fmt_time(STATE['length'])}")
        L.append(f"   Текст             {lyrics_status_text()}")
        cur = current_line() if STATE["spotify"] == "playing" else ""
        L.append("   Строка            " + (col("b", clip(cur, 56)) if cur else col("d", "—")))
    else:
        L.append("   " + col("d", "ничего не играет"))
    L.append("")
    L.append(" " + col("c", "СТАТИСТИКА"))
    L.append(f"   Время работы       {fmt_uptime(time.time() - START)}")
    L.append(f"   Треков за сессию   {STATE['tracks']}")
    L.append(f"   Текстов скачано    {col('g', str(STATE['downloaded']))}")
    L.append(f"   Уже были           {STATE['had_own']}")
    L.append(f"   Не найдено         {col('r', str(STATE['not_found'])) if STATE['not_found'] else '0'}")
    L.append(f"   Песен в папке      {songs_count()}")
    L.append(f"   Ошибок записи      {col('y', str(STATE['write_errors'])) if STATE['write_errors'] else '0'}")
    L.append("")
    L.append(" " + col("c", "ЖУРНАЛ"))
    if EVENTS:
        for t, m in EVENTS:
            L.append("   " + col("d", t) + " " + clip(m, 74))
    else:
        L.append("   " + col("d", "пока пусто"))
    L.append("")
    L.append(" " + col("d", "Ctrl+C — остановить мост  ·  в игре: K → Source: Bridge → Reload songs"))
    return L


def render():
    if not DASH_ON[0]:
        return
    try:
        frame = "\033[H" + "".join(line + "\033[K\n" for line in build_frame()) + "\033[J"
        sys.stdout.write(frame)
        sys.stdout.flush()
    except Exception:
        pass


def safe_name(text):
    text = re.sub(r'[\\/:*?"<>|]', "", text).strip().rstrip(".")
    return text[:80] or "unknown"


LRC_RE = re.compile(r"\[(\d+):(\d+(?:\.\d+)?)\]\s*(.*)")


def parse_lrc(text):
    """'[01:23.45] слова' -> [(83.45, 'слова'), ...]"""
    rows = []
    for raw in text.splitlines():
        m = LRC_RE.match(raw.strip())
        if not m:
            continue
        t = int(m.group(1)) * 60 + float(m.group(2))
        words = m.group(3).strip()
        rows.append((t, words))
    rows.sort(key=lambda r: r[0])
    return rows


def build_lines(rows):
    """Режет строки LRC на кусочки по WORDS_PER_CHUNK слов и распределяет время."""
    out = []
    n = 0
    for i, (t, text) in enumerate(rows):
        if not text:
            continue
        end = rows[i + 1][0] if i + 1 < len(rows) else t + MAX_LINE_SECONDS
        span = max(0.5, min(end - t, MAX_LINE_SECONDS))
        words = text.split()
        chunks = [" ".join(words[j:j + WORDS_PER_CHUNK]) for j in range(0, len(words), WORDS_PER_CHUNK)]
        total = sum(len(c) for c in chunks) or 1
        cur = t
        for c in chunks:
            dur = span * len(c) / total
            out.append({
                "time": round(cur, 2),
                "text": c,
                "effect": EFFECTS[n % len(EFFECTS)],
                "out": "FADE",
                "duration": round(max(0.8, dur + 0.4), 2),
            })
            cur += dur
            n += 1
    return out


def http_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "StolasLyricsBridge/1.0"})
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read().decode("utf-8"))


def fetch_synced(title, artist, duration):
    """Ищет синхронный текст на LRCLIB. Возвращает строку LRC или None."""
    base = "https://lrclib.net/api/"
    q = {"track_name": title, "artist_name": artist}
    if duration:
        q["duration"] = str(int(round(duration)))
    try:
        data = http_json(base + "get?" + urllib.parse.urlencode(q))
        if data.get("syncedLyrics"):
            return data["syncedLyrics"]
    except Exception:
        pass
    try:
        results = http_json(base + "search?" + urllib.parse.urlencode({"q": f"{artist} {title}".strip()}))
        best = None
        for r in results:
            if not r.get("syncedLyrics"):
                continue
            diff = abs((r.get("duration") or 0) - duration) if duration else 0
            if best is None or diff < best[0]:
                best = (diff, r["syncedLyrics"])
        if best and (not duration or best[0] <= 5):
            return best[1]
    except Exception:
        pass
    return None


def song_folder(title, artist):
    return os.path.join(SONGS_DIR, safe_name(f"{artist} - {title}" if artist else title))


def download_lyrics(title, artist, duration):
    key = (title, artist)
    LYRICS_STATUS[key] = ("search", 0)
    folder = song_folder(title, artist)
    path = os.path.join(folder, "lyrics.json")
    # не перезаписываем, если там уже есть строки (свои или скачанные раньше)
    try:
        with open(path, encoding="utf-8") as f:
            have = json.load(f).get("lyrics")
        if have:
            LYRICS_STATUS[key] = ("have", len(have))
            STATE["had_own"] += 1
            return
    except Exception:
        pass
    lrc = fetch_synced(title, artist, duration)
    if not lrc:
        LYRICS_STATUS[key] = ("none", 0)
        STATE["not_found"] += 1
        log(f"[-] Текст не найден: {artist} - {title}")
        return
    lines = build_lines(parse_lrc(lrc))
    if not lines:
        LYRICS_STATUS[key] = ("none", 0)
        STATE["not_found"] += 1
        log(f"[-] Пустой текст: {artist} - {title}")
        return
    os.makedirs(folder, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"title": title, "artist": artist, "lyrics": lines}, f, ensure_ascii=False, indent=2)
    LYRICS_STATUS[key] = ("ok", len(lines))
    STATE["downloaded"] += 1
    log(f"[+] Текст скачан ({len(lines)} строк): {artist} - {title}. В игре: K -> Reload songs")


def ensure_song_stub(title, artist):
    folder = os.path.join(SONGS_DIR, safe_name(f"{artist} - {title}" if artist else title))
    path = os.path.join(folder, "lyrics.json")
    if os.path.exists(path):
        return
    os.makedirs(folder, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"title": title, "artist": artist, "lyrics": []}, f, ensure_ascii=False, indent=2)
    log(f"[+] Создана заготовка песни: {folder}")


def pick_session(manager):
    sessions = list(manager.get_sessions())
    for s in sessions:
        if "spotify" in (s.source_app_user_model_id or "").lower():
            return s
    return manager.get_current_session()


def write_atomic(data):
    os.makedirs(CONFIG_DIR, exist_ok=True)
    text = json.dumps(data, ensure_ascii=False)
    tmp = BRIDGE_FILE + ".tmp"
    try:
        with open(tmp, "w", encoding="utf-8") as f:
            f.write(text)
        os.replace(tmp, BRIDGE_FILE)
        return
    except OSError:
        pass  # Windows иногда блокирует файл, пишем напрямую
    try:
        with open(BRIDGE_FILE, "w", encoding="utf-8") as f:
            f.write(text)
    except OSError:
        STATE["write_errors"] += 1  # файл занят, повторим на следующем тике


async def main():
    try:
        os.makedirs(CONFIG_DIR, exist_ok=True)
        open(LOG_FILE, "w", encoding="utf-8").close()
    except OSError:
        pass
    if DASHBOARD and sys.stdout is not None and getattr(sys.stdout, "isatty", lambda: False)():
        os.system("")  # включает цвета ANSI в cmd
        try:  # UTF-8 в консоли, иначе рамки и русские буквы ломаются в cp866/cp1251
            import ctypes
            ctypes.windll.kernel32.SetConsoleOutputCP(65001)
            sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass
        DASH_ON[0] = True
        sys.stdout.write("\033]0;Stolas Lyrics bridge\007\033[2J\033[H\033[?25l")
    manager = await SessionManager.request_async()
    last_key = None
    last_render = 0.0
    log(f"Stolas Lyrics bridge {APP_VERSION} запущен. Остановка: Ctrl+C")
    while True:
        try:
            session = pick_session(manager)
            if session is None:
                STATE.update(spotify="none", title="", artist="", pos=0.0, length=0.0)
                write_atomic({"title": "", "artist": "", "position_ms": 0, "playing": False})
            else:
                props = await session.try_get_media_properties_async()
                title = props.title or ""
                artist = props.artist or ""
                playing = session.get_playback_info().playback_status == PlaybackStatus.PLAYING
                tl = session.get_timeline_properties()
                pos = tl.position.total_seconds()
                try:
                    length = (tl.end_time - tl.start_time).total_seconds()
                except Exception:
                    length = 0
                if playing:
                    try:
                        now = datetime.datetime.now(datetime.timezone.utc)
                        pos += max(0.0, (now - tl.last_updated_time).total_seconds())
                    except Exception:
                        pass
                STATE.update(spotify="playing" if playing else "paused", title=title, artist=artist,
                             pos=pos, length=length)
                write_atomic({
                    "title": title,
                    "artist": artist,
                    "position_ms": int(pos * 1000),
                    "playing": bool(playing),
                })
                key = (title, artist)
                if AUTO_CREATE_SONGS and title and key != last_key:
                    STATE["tracks"] += 1
                    ensure_song_stub(title, artist)
                    if AUTO_LYRICS:
                        threading.Thread(target=download_lyrics, args=(title, artist, length), daemon=True).start()
                    else:
                        LYRICS_STATUS[key] = ("off", 0)
                    last_key = key
        except Exception as e:  # не падаем из-за разовых ошибок
            log("Ошибка:", e)
        if time.time() - last_render >= 0.5:
            last_render = time.time()
            render()
        await asyncio.sleep(INTERVAL)


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
    finally:
        if DASH_ON[0]:
            sys.stdout.write("\033[?25h\033[0m\nМост остановлен.\n")
