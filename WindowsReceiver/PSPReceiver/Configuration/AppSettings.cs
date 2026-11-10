using System.IO;
using System.Text.Json;
using PSPReceiver.Input;

namespace PSPReceiver.Configuration;

public enum InputMode { Keyboard, Gamepad }

public sealed class AppSettings
{
    public int Port { get; set; } = 8765;
    public InputMode Mode { get; set; } = InputMode.Keyboard;
    public KeyBindings Keys { get; set; } = KeyBindings.Defaults();
    public bool MinimizeToTray { get; set; } = true;

    public static string DefaultPath => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "PSPReceiver", "settings.json");

    public static AppSettings Load(string? path = null)
    {
        try
        {
            var p = path ?? DefaultPath;
            if (File.Exists(p))
            {
                var s = JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(p));
                if (s is not null) { s.Sanitize(); return s; }
            }
        }
        catch (Exception) { /* corrupt or unreadable file: fall back to defaults */ }
        return new AppSettings();
    }

    public void Save(string? path = null)
    {
        try
        {
            var p = path ?? DefaultPath;
            Directory.CreateDirectory(Path.GetDirectoryName(p)!);
            File.WriteAllText(p, JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch (Exception) { /* settings are best-effort */ }
    }

    private void Sanitize()
    {
        if (Port < 1024 || Port > 65535) Port = 8765;
        if (!Enum.IsDefined(Mode)) Mode = InputMode.Keyboard;
        Keys ??= KeyBindings.Defaults();
        Keys.Sanitize();
    }
}
