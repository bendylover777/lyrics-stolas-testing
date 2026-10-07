package com.evolyrics;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Built-in "bridge": asks the OS what is playing (Spotify / Apple Music on macOS, any media app on Windows).
 * Pure Java (no Minecraft classes), runs on its own background threads, no console window.
 */
public final class NowPlaying {
    private static final long STALE_NANOS = 3_000_000_000L;
    private static final char SEP = '\u001f';

    public interface Poller {
        void start();

        void stop();

        /** Latest info, or null when nothing is playing / the data is stale. */
        PlayerInfo latest();

        /** Last problem (permission, missing PowerShell...), or null. */
        String error();
    }

    private NowPlaying() {
    }

    public static Poller create() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) return new MacPoller();
        if (os.contains("win")) return new WinPoller();
        return null;
    }

    public static String osName() {
        return System.getProperty("os.name", "?");
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static boolean isAd(String title) {
        String t = title.trim().toLowerCase(Locale.ROOT);
        return t.equals("advertisement") || t.equals("spotify");
    }

    // ------------------------------------------------------------------ macOS

    static String macScript(String app) {
        return """
            set sep to (ASCII character 31)
            if application "%s" is running then
                try
                    tell application "%s"
                        set st to (player state as text)
                        if st is "stopped" then return "stopped"
                        set t to (name of current track as text)
                        set a to (artist of current track as text)
                        set p to (player position as text)
                        set d to (duration of current track as text)
                    end tell
                    return st & sep & t & sep & a & sep & p & sep & d
                on error errMsg number errNum
                    return "error" & sep & errNum & sep & errMsg
                end try
            end if
            return "off"
            """.formatted(app, app);
    }

    /** Parses osascript output. Returns null for "nothing playing"; fills err[0] for real problems. */
    static PlayerInfo parseMac(String out, boolean durationMs, String app, String[] err) {
        out = out.replace("\r", "");
        if (out.endsWith("\n")) out = out.substring(0, out.length() - 1);
        if (out.isEmpty() || out.equals("off") || out.equals("stopped")) return null;
        String[] parts = out.split(String.valueOf(SEP), -1);
        if (parts[0].equals("error")) {
            String code = parts.length > 1 ? parts[1] : "?";
            String msg = parts.length > 2 ? parts[2] : "";
            if (code.equals("-1743")) err[0] = MacPoller.NO_ACCESS;
            else if (!code.equals("-1728") && !code.equals("-600")) err[0] = "Player error " + code + ": " + msg;
            return null;
        }
        if (parts.length < 5) return null;
        double pos;
        double dur;
        try {
            pos = Double.parseDouble(parts[3].replace(',', '.'));
            dur = Double.parseDouble(parts[4].replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
        if (durationMs) dur /= 1000.0;
        String title = clean(parts[1]);
        if (isAd(title)) return null;
        String state = parts[0].trim().toLowerCase(Locale.ROOT);
        boolean playing = state.equals("playing") || parts[0].endsWith("kPSP");
        return new PlayerInfo(title, clean(parts[2]), playing, pos, dur, app);
    }

    static final class MacPoller implements Poller {
        static final String NO_ACCESS = "No access: allow Minecraft/Java to control Spotify "
            + "(System Settings > Privacy & Security > Automation)";
        private volatile PlayerInfo last;
        private volatile long lastAt;
        private volatile String error;
        private volatile boolean running;
        private Thread thread;

        @Override
        public synchronized void start() {
            if (thread != null) return;
            running = true;
            thread = new Thread(() -> {
                while (running) {
                    try {
                        PlayerInfo p = poll();
                        last = p;
                        lastAt = System.nanoTime();
                    } catch (Exception e) {
                        error = String.valueOf(e.getMessage());
                    }
                    try {
                        Thread.sleep(350);
                    } catch (InterruptedException ie) {
                        return;
                    }
                }
            }, "StolasLyrics-player");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public synchronized void stop() {
            running = false;
            if (thread != null) thread.interrupt();
            thread = null;
        }

        @Override
        public PlayerInfo latest() {
            PlayerInfo p = last;
            if (p == null || System.nanoTime() - lastAt > STALE_NANOS) return null;
            return p;
        }

        @Override
        public String error() {
            return error;
        }

        PlayerInfo poll() throws Exception {
            PlayerInfo p = ask("Spotify", true);
            if (p == null) p = ask("Music", false);
            return p;
        }

        private PlayerInfo ask(String app, boolean ms) throws Exception {
            Process proc = new ProcessBuilder("osascript", "-e", macScript(app)).start();
            String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String err = new String(proc.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                error = "osascript timed out";
                return null;
            }
            if (proc.exitValue() != 0) {
                error = err.contains("-1743") ? NO_ACCESS : "osascript: " + err.trim();
                return null;
            }
            String[] e = new String[1];
            PlayerInfo p = parseMac(out, ms, app.equals("Music") ? "Apple Music" : app, e);
            if (e[0] != null) error = e[0];
            else if (p != null) error = null;
            return p;
        }
    }

    // ---------------------------------------------------------------- Windows

    static final String WIN_SCRIPT = """
        $ErrorActionPreference = 'Stop'
        [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
        $tab = [string][char]9
        $inv = [Globalization.CultureInfo]::InvariantCulture
        function Clean($s) { if ($null -eq $s) { return '' }; return ([string]$s).Replace([string][char]9, ' ').Replace([string][char]13, ' ').Replace([string][char]10, ' ') }
        Add-Type -AssemblyName System.Runtime.WindowsRuntime
        $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
        function Await($op, $type) {
          $task = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op))
          $task.Wait(-1) | Out-Null
          return $task.Result
        }
        [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
        [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
        $mgrType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
        $propType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
        $mgr = Await ($mgrType::RequestAsync()) $mgrType
        while ($true) {
          try {
            $session = $null
            foreach ($s in $mgr.GetSessions()) { if ($s.SourceAppUserModelId -match 'spotify') { $session = $s; break } }
            if ($null -eq $session) { $session = $mgr.GetCurrentSession() }
            if ($null -eq $session) {
              $line = 'N'
            } else {
              $props = Await ($session.TryGetMediaPropertiesAsync()) $propType
              $info = $session.GetPlaybackInfo()
              $tl = $session.GetTimelineProperties()
              $playing = ($info.PlaybackStatus -eq 'Playing')
              $pos = $tl.Position.TotalSeconds
              if ($playing) {
                $el = ([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalSeconds
                if ($el -gt 0 -and $el -lt 600) { $pos += $el }
              }
              $len = $tl.EndTime.TotalSeconds
              $flag = '0'
              if ($playing) { $flag = '1' }
              $line = 'P' + $tab + $flag + $tab + (Clean $props.Title) + $tab + (Clean $props.Artist) + $tab + $pos.ToString('0.000', $inv) + $tab + $len.ToString('0.000', $inv) + $tab + (Clean $session.SourceAppUserModelId)
            }
          } catch {
            $line = 'E' + $tab + (Clean $_.Exception.Message)
          }
          [Console]::Out.WriteLine($line)
          [Console]::Out.Flush()
          Start-Sleep -Milliseconds 250
        }
        """;

    static String winAppName(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        if (s.contains("spotify")) return "Spotify";
        if (s.contains("music") || s.contains("itunes")) return "Apple Music";
        if (s.contains("chrome")) return "Chrome";
        if (s.contains("firefox")) return "Firefox";
        if (s.contains("msedge")) return "Edge";
        return id.isEmpty() ? "Player" : id;
    }

    /** Parses one line from the PowerShell loop. 'N' = no player. Returns null when there is nothing to show. */
    static PlayerInfo parseWinLine(String line, String[] err) {
        if (line.startsWith("\uFEFF")) line = line.substring(1);
        line = line.trim();
        if (line.isEmpty() || line.equals("N")) return null;
        if (line.startsWith("E\t")) {
            err[0] = "Windows media API: " + line.substring(2);
            return null;
        }
        if (!line.startsWith("P\t")) {
            err[0] = "PowerShell: " + line;
            return null;
        }
        String[] p = line.split("\t", -1);
        if (p.length < 7) return null;
        double pos;
        double len;
        try {
            pos = Double.parseDouble(p[4]);
            len = Double.parseDouble(p[5]);
        } catch (NumberFormatException e) {
            return null;
        }
        String title = clean(p[2]);
        if (title.isEmpty() || isAd(title)) return null;
        return new PlayerInfo(title, clean(p[3]), p[1].equals("1"), pos, len, winAppName(p[6]));
    }

    static final class WinPoller implements Poller {
        private volatile PlayerInfo last;
        private volatile long lastAt;
        private volatile String error;
        private volatile boolean running;
        private Process proc;
        private Thread thread;

        @Override
        public synchronized void start() {
            if (thread != null) return;
            running = true;
            thread = new Thread(this::loop, "StolasLyrics-player");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public synchronized void stop() {
            running = false;
            if (proc != null) proc.destroyForcibly();
            if (thread != null) thread.interrupt();
            thread = null;
            proc = null;
        }

        @Override
        public PlayerInfo latest() {
            PlayerInfo p = last;
            if (p == null || System.nanoTime() - lastAt > STALE_NANOS) return null;
            return p;
        }

        @Override
        public String error() {
            return error;
        }

        private void loop() {
            int restarts = 0;
            while (running && restarts < 4) {
                try {
                    String enc = Base64.getEncoder().encodeToString(WIN_SCRIPT.getBytes(StandardCharsets.UTF_16LE));
                    ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                        "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-EncodedCommand", enc);
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    synchronized (this) {
                        proc = p;
                    }
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while (running && (line = r.readLine()) != null) {
                            String[] e = new String[1];
                            PlayerInfo info = parseWinLine(line, e);
                            if (e[0] != null) error = e[0];
                            else if (info != null) error = null;
                            last = info;
                            lastAt = System.nanoTime();
                        }
                    }
                } catch (Exception ex) {
                    error = "PowerShell failed to start: " + ex.getMessage();
                }
                if (!running) return;
                restarts++;
                last = null;
                if (error == null) error = "PowerShell stopped, restarting";
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }
}
