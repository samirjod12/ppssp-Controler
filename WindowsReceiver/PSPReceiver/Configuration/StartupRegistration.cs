using Microsoft.Win32;

namespace PSPReceiver.Configuration;

/// <summary>Per-user "start with Windows" via HKCU Run. No administrator rights needed.</summary>
public static class StartupRegistration
{
    private const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string ValueName = "PSPReceiver";

    public static bool IsEnabled()
    {
        try
        {
            using var k = Registry.CurrentUser.OpenSubKey(RunKey);
            return k?.GetValue(ValueName) is string s && s.Length > 0;
        }
        catch { return false; }
    }

    public static bool Set(bool enable)
    {
        try
        {
            using var k = Registry.CurrentUser.CreateSubKey(RunKey);
            if (enable)
            {
                var path = Environment.ProcessPath;
                if (string.IsNullOrEmpty(path)) return false;
                k.SetValue(ValueName, $"\"{path}\" --tray");
            }
            else k.DeleteValue(ValueName, false);
            return true;
        }
        catch { return false; }
    }
}
