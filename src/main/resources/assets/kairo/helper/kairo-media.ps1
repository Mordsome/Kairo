# Kairo media helper (Windows). Started by Kairo only while the Now Playing or Music Visualiser mod is on, and
# stopped when they are switched off or the game closes. It reads two things from Windows and prints them for Kairo:
#   - what the system's media controls say is playing (title, artist, app, position, cover): lines starting with N
#   - how loud each band of the sound your PC is playing is right now (32 numbers, no recording kept): lines starting with A
# Nothing is saved except the current cover picture next to Kairo's settings, and nothing is sent anywhere.
param([int]$Info = 1, [int]$Audio = 1, [int]$ParentPid = 0, [string]$CoverPath = '')

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$out = [Console]::Out

function Say([string]$line) {
    $out.WriteLine($line)
    $out.Flush()
}

function Clean([string]$text) {
    if ($null -eq $text) { return '' }
    return ($text -replace "[\t\r\n]", ' ').Trim()
}

# ---- sound levels: the default speakers' output (WASAPI loopback), turned into 32 bands ----
$audioReady = $false
if ($Audio -ne 0) {
    try {
        Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

public static class KairoAudio {
    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    private class MMDeviceEnumerator { }

    [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("A95664D2-9614-4F35-A746-DE8DB63617E6")]
    private interface IMMDeviceEnumerator {
        [PreserveSig] int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);
        [PreserveSig] int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice device);
    }

    [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("D666063F-1587-4E43-81F1-B948E807363F")]
    private interface IMMDevice {
        [PreserveSig] int Activate(ref Guid iid, int clsCtx, IntPtr activationParams, [MarshalAs(UnmanagedType.IUnknown)] out object instance);
    }

    [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("1CB9AD4C-DBFA-4c32-B178-C2F568A703B2")]
    private interface IAudioClient {
        [PreserveSig] int Initialize(int shareMode, int streamFlags, long bufferDuration, long periodicity, IntPtr format, IntPtr sessionGuid);
        [PreserveSig] int GetBufferSize(out uint frames);
        [PreserveSig] int GetStreamLatency(out long latency);
        [PreserveSig] int GetCurrentPadding(out uint padding);
        [PreserveSig] int IsFormatSupported(int shareMode, IntPtr format, out IntPtr closest);
        [PreserveSig] int GetMixFormat(out IntPtr format);
        [PreserveSig] int GetDevicePeriod(out long defaultPeriod, out long minimumPeriod);
        [PreserveSig] int Start();
        [PreserveSig] int Stop();
        [PreserveSig] int Reset();
        [PreserveSig] int SetEventHandle(IntPtr handle);
        [PreserveSig] int GetService(ref Guid iid, [MarshalAs(UnmanagedType.IUnknown)] out object service);
    }

    [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("C8ADBD64-E71E-48a0-A4DE-185C395CD317")]
    private interface IAudioCaptureClient {
        [PreserveSig] int GetBuffer(out IntPtr data, out uint frames, out uint flags, out ulong devicePosition, out ulong qpcPosition);
        [PreserveSig] int ReleaseBuffer(uint frames);
        [PreserveSig] int GetNextPacketSize(out uint frames);
    }

    private const int Size = 2048;
    private const int Bands = 32;
    private static readonly float[] ring = new float[Size];
    private static int write;
    private static int sampleRate = 48000;
    private static long lastPacket;
    private static readonly object gate = new object();
    private static volatile bool running;

    public static void Start() {
        if (running) { return; }
        running = true;
        Thread thread = new Thread(Loop);
        thread.IsBackground = true;
        thread.SetApartmentState(ApartmentState.MTA);
        thread.Start();
    }

    private static void Loop() {
        while (running) {
            try { Capture(); } catch (Exception) { }
            // The speakers changed or went away: wait a moment and listen to the new default ones.
            Thread.Sleep(1500);
        }
    }

    private static void Check(int result) {
        if (result < 0) { Marshal.ThrowExceptionForHR(result); }
    }

    private static void Capture() {
        IMMDeviceEnumerator devices = (IMMDeviceEnumerator) new MMDeviceEnumerator();
        IMMDevice device;
        Check(devices.GetDefaultAudioEndpoint(0, 0, out device));
        Guid clientId = new Guid("1CB9AD4C-DBFA-4c32-B178-C2F568A703B2");
        object clientObject;
        Check(device.Activate(ref clientId, 23, IntPtr.Zero, out clientObject));
        IAudioClient client = (IAudioClient) clientObject;
        IntPtr format;
        Check(client.GetMixFormat(out format));
        int tag = (ushort) Marshal.ReadInt16(format, 0);
        int channels = Marshal.ReadInt16(format, 2);
        sampleRate = Marshal.ReadInt32(format, 4);
        int bits = Marshal.ReadInt16(format, 14);
        // 3 = float; 0xFFFE = "extensible", whose real kind is the first number of the sub-format that follows.
        bool isFloat = tag == 3 || (tag == 0xFFFE && Marshal.ReadInt32(format, 24) == 3);
        Check(client.Initialize(0, 0x00020000, 2000000, 0, format, IntPtr.Zero));
        Marshal.FreeCoTaskMem(format);
        Guid captureId = new Guid("C8ADBD64-E71E-48a0-A4DE-185C395CD317");
        object captureObject;
        Check(client.GetService(ref captureId, out captureObject));
        IAudioCaptureClient capture = (IAudioCaptureClient) captureObject;
        Check(client.Start());
        int bytes = bits / 8;
        try {
            while (running) {
                Thread.Sleep(8);
                uint next;
                Check(capture.GetNextPacketSize(out next));
                while (next > 0) {
                    IntPtr data;
                    uint frames, flags;
                    ulong devicePosition, clock;
                    Check(capture.GetBuffer(out data, out frames, out flags, out devicePosition, out clock));
                    bool silent = (flags & 2) != 0;
                    lock (gate) {
                        for (int i = 0; i < frames; i++) {
                            float sum = 0;
                            if (!silent) {
                                int offset = i * channels * bytes;
                                for (int c = 0; c < channels; c++) {
                                    int at = offset + c * bytes;
                                    if (isFloat && bits == 32) {
                                        sum += BitConverter.ToSingle(BitConverter.GetBytes(Marshal.ReadInt32(data, at)), 0);
                                    } else if (bits == 16) {
                                        sum += Marshal.ReadInt16(data, at) / 32768f;
                                    } else if (bits == 32) {
                                        sum += Marshal.ReadInt32(data, at) / 2147483648f;
                                    } else if (bits == 24) {
                                        int v = Marshal.ReadByte(data, at) | (Marshal.ReadByte(data, at + 1) << 8) | ((sbyte) Marshal.ReadByte(data, at + 2) << 16);
                                        sum += v / 8388608f;
                                    }
                                }
                            }
                            ring[write] = sum / Math.Max(1, channels);
                            write = (write + 1) % Size;
                        }
                        lastPacket = DateTime.UtcNow.Ticks;
                    }
                    Check(capture.ReleaseBuffer(frames));
                    Check(capture.GetNextPacketSize(out next));
                }
            }
        } finally {
            client.Stop();
            Marshal.ReleaseComObject(capture);
            Marshal.ReleaseComObject(client);
            Marshal.ReleaseComObject(device);
            Marshal.ReleaseComObject(devices);
        }
    }

    /** "A" and 32 numbers from 0 to 255: how loud each band is right now, low notes first. */
    public static string Line() {
        const int n = 1024;
        double[] re = new double[n];
        double[] im = new double[n];
        bool fresh;
        int rate;
        lock (gate) {
            fresh = DateTime.UtcNow.Ticks - lastPacket < 2500000; // a quarter of a second
            rate = sampleRate;
            for (int i = 0; i < n; i++) {
                double window = 0.5 - 0.5 * Math.Cos(2 * Math.PI * i / (n - 1));
                re[i] = fresh ? ring[(write - n + i + Size * 2) % Size] * window : 0;
            }
        }
        // A plain radix-2 FFT.
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) { j ^= bit; }
            j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2 * Math.PI / len;
            double wr = Math.Cos(angle), wi = Math.Sin(angle);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = i + k + len / 2;
                    double xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                    double nr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = nr;
                }
            }
        }
        StringBuilder line = new StringBuilder("A");
        double low = 40, high = Math.Min(16000, rate / 2.0 - 1);
        for (int band = 0; band < Bands; band++) {
            double from = low * Math.Pow(high / low, band / (double) Bands);
            double to = low * Math.Pow(high / low, (band + 1) / (double) Bands);
            int first = Math.Max(1, (int) Math.Floor(from * n / rate));
            int last = Math.Max(first, Math.Min(n / 2 - 1, (int) Math.Ceiling(to * n / rate) - 1));
            double peak = 0;
            for (int k = first; k <= last; k++) {
                double magnitude = Math.Sqrt(re[k] * re[k] + im[k] * im[k]) * 4 / n;
                if (magnitude > peak) { peak = magnitude; }
            }
            // From -62 dB (silence) to -6 dB (very loud), a little extra for the high bands, which carry less energy.
            double db = 20 * Math.Log10(peak + 1e-9) + band * 0.35;
            int value = (int) Math.Round(Math.Max(0, Math.Min(1, (db + 62) / 56)) * 255);
            line.Append(' ').Append(value);
        }
        return line.ToString();
    }
}
'@
        [KairoAudio]::Start()
        $audioReady = $true
    } catch {
        Say ('E audio ' + (Clean $_.Exception.Message))
    }
}

# ---- now playing: Windows' media controls (Spotify, browsers, players...) ----
$manager = $null
$asTask = $null
$asStream = $null
if ($Info -ne 0) {
    try {
        Add-Type -AssemblyName System.Runtime.WindowsRuntime
        $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
            $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
        })[0]
        [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
        [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null
        $inputStream = [Windows.Storage.Streams.IInputStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
        $asStream = [System.IO.WindowsRuntimeStreamExtensions].GetMethod('AsStreamForRead', [Type[]]@($inputStream))
    } catch {
        Say ('E info ' + (Clean $_.Exception.Message))
        $Info = 0
    }
}

function Await($operation, [Type]$type) {
    $task = $asTask.MakeGenericMethod($type).Invoke($null, @($operation))
    if (-not $task.Wait(3000)) { throw 'timed out' }
    return $task.Result
}

$coverKey = ''
$coverVersion = 0
$coverTries = 0

function SaveCover($properties, [string]$key) {
    # A new song: fetch its cover once it's there (some players hand it over a moment after the title).
    if ($CoverPath -eq '') { return }
    if ($key -ne $script:coverKey) {
        $script:coverKey = $key
        $script:coverTries = 0
        $script:coverVersion = -[Math]::Abs($script:coverVersion) - 1
    }
    if ($script:coverVersion -gt 0 -or $script:coverTries -ge 12) { return }
    $script:coverTries++
    $reference = $properties.Thumbnail
    if ($null -eq $reference) { return }
    try {
        $stream = Await ($reference.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
        # Called through reflection: PowerShell can't pick the overload for the stream object Windows hands back.
        $reader = $script:asStream.Invoke($null, @($stream))
        $temp = $CoverPath + '.tmp'
        $file = [System.IO.File]::Create($temp)
        try { $reader.CopyTo($file) } finally { $file.Close(); $reader.Close() }
        if ((Get-Item -LiteralPath $temp).Length -gt 0) {
            Move-Item -LiteralPath $temp -Destination $CoverPath -Force
            $script:coverVersion = [Math]::Abs($script:coverVersion)
        }
    } catch { }
}

function InfoLine {
    if ($null -eq $script:manager) {
        $script:manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
    }
    $session = $script:manager.GetCurrentSession()
    if ($null -eq $session) { return "N`tNone" }
    $properties = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
    $timeline = $session.GetTimelineProperties()
    $playback = $session.GetPlaybackInfo()
    $status = [string]$playback.PlaybackStatus
    $position = $timeline.Position.TotalMilliseconds
    $length = ($timeline.EndTime - $timeline.StartTime).TotalMilliseconds
    if ($status -eq 'Playing' -and $timeline.LastUpdatedTime.Year -gt 2000) {
        # The position is as of the player's last report: add the time since.
        $rate = 1.0
        if ($null -ne $playback.PlaybackRate) { $rate = [double]$playback.PlaybackRate }
        $position += ([DateTimeOffset]::UtcNow - $timeline.LastUpdatedTime).TotalMilliseconds * $rate
    }
    if ($length -gt 0 -and $position -gt $length) { $position = $length }
    if ($position -lt 0) { $position = 0 }
    $title = Clean $properties.Title
    $artist = Clean $properties.Artist
    SaveCover $properties ($title + '|' + $artist)
    $cover = [Math]::Max(0, $script:coverVersion)
    return "N`t$status`t$([long]$position)`t$([long]$length)`t$(Clean $session.SourceAppUserModelId)`t$title`t$artist`t$cover"
}

# ---- the loop: sound levels 30 times a second, what's playing twice a second ----
Say 'R ready'
$tick = 0
while ($true) {
    try {
        if ($audioReady) { Say ([KairoAudio]::Line()) }
        if ($Info -ne 0 -and $tick % 15 -eq 0) {
            try {
                Say (InfoLine)
            } catch {
                $manager = $null
                Say "N`tNone"
            }
        }
        if ($ParentPid -ne 0 -and $tick % 60 -eq 0 -and $null -eq (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) {
            break
        }
    } catch {
        # Kairo closed the pipe: nothing left to do.
        break
    }
    $tick++
    Start-Sleep -Milliseconds 33
}
