using PSPReceiver.Configuration;
using PSPReceiver.Input;
using PSPReceiver.Models;
using Xunit;

namespace PSPReceiver.Tests;

public class KeyBindingsTests
{
    [Fact] public void Defaults_CoverAllButtons() =>
        Assert.Equal(Enum.GetValues<ControllerButton>().Length, KeyBindings.Defaults().Buttons.Count);

    [Fact] public void Defaults_ReportTheSpecKeyClashes()
    {
        var text = string.Join("|", KeyBindings.Defaults().FindConflicts());
        Assert.Contains("W is used by", text);
        Assert.Contains("S is used by", text);
        Assert.Contains("A is used by", text);
    }

    [Fact] public void NoConflicts_AfterRemapping()
    {
        var b = KeyBindings.Defaults();
        b.AnalogUp = 0x49; b.AnalogDown = 0x4B; b.AnalogLeft = 0x4A; b.AnalogRight = 0x4C; // I K J L
        Assert.Empty(b.FindConflicts());
    }

    [Fact] public void Sanitize_RepairsMissingInvalidAndDeadZone()
    {
        var b = new KeyBindings { Buttons = new() { [ControllerButton.Cross] = 0 }, AnalogUp = 0, AnalogDeadZone = double.NaN };
        b.Sanitize();
        Assert.Equal(0x5A, b.Buttons[ControllerButton.Cross]);
        Assert.Equal(Enum.GetValues<ControllerButton>().Length, b.Buttons.Count);
        Assert.Equal(0x57, b.AnalogUp);
        Assert.Equal(0.30, b.AnalogDeadZone);
        b.AnalogDeadZone = 5; b.Sanitize();
        Assert.Equal(0.90, b.AnalogDeadZone);
    }

    [Fact] public void Clone_IsIndependent()
    {
        var a = KeyBindings.Defaults();
        var c = a.Clone();
        c.Buttons[ControllerButton.Cross] = 0x42;
        Assert.Equal(0x5A, a.Buttons[ControllerButton.Cross]);
    }

    [Fact] public void KeyNames_KnownKeys()
    {
        Assert.Equal("Z", KeyNames.Of(0x5A));
        Assert.Equal("Up", KeyNames.Of(0x26));
    }

    [Fact] public void Settings_RoundTripKeys()
    {
        var path = Path.Combine(Path.GetTempPath(), Guid.NewGuid() + ".json");
        try
        {
            var s = new AppSettings();
            s.Keys.Buttons[ControllerButton.Start] = 0x20;   // Space
            s.Keys.AnalogDeadZone = 0.5;
            s.MinimizeToTray = false;
            s.Save(path);
            var l = AppSettings.Load(path);
            Assert.Equal(0x20, l.Keys.Buttons[ControllerButton.Start]);
            Assert.Equal(0.5, l.Keys.AnalogDeadZone);
            Assert.False(l.MinimizeToTray);
        }
        finally { File.Delete(path); }
    }

    [Fact] public void Settings_NullKeys_FallBackToDefaults()
    {
        var path = Path.Combine(Path.GetTempPath(), Guid.NewGuid() + ".json");
        try
        {
            File.WriteAllText(path, """{"Port":8765,"Keys":null}""");
            Assert.Equal(0x5A, AppSettings.Load(path).Keys.Buttons[ControllerButton.Cross]);
        }
        finally { File.Delete(path); }
    }
}
