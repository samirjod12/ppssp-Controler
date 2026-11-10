using PSPReceiver.Models;

namespace PSPReceiver.Input;

/// <summary>User-editable keyboard layout. Values are Windows virtual-key codes.</summary>
public sealed class KeyBindings
{
    public Dictionary<ControllerButton, ushort> Buttons { get; set; } = KeyboardMapping.Defaults();
    public ushort AnalogUp { get; set; } = KeyboardMapping.AnalogUp;
    public ushort AnalogDown { get; set; } = KeyboardMapping.AnalogDown;
    public ushort AnalogLeft { get; set; } = KeyboardMapping.AnalogLeft;
    public ushort AnalogRight { get; set; } = KeyboardMapping.AnalogRight;
    public double AnalogDeadZone { get; set; } = 0.30;

    public static KeyBindings Defaults() => new();

    public KeyBindings Clone() => new()
    {
        Buttons = new Dictionary<ControllerButton, ushort>(Buttons),
        AnalogUp = AnalogUp, AnalogDown = AnalogDown, AnalogLeft = AnalogLeft, AnalogRight = AnalogRight,
        AnalogDeadZone = AnalogDeadZone
    };

    private static bool Valid(ushort vk) => vk is >= 0x08 and <= 0xFE;

    /// <summary>Repairs missing/invalid entries (e.g. hand-edited or corrupt settings file).</summary>
    public void Sanitize()
    {
        Buttons ??= new Dictionary<ControllerButton, ushort>();
        foreach (var kv in KeyboardMapping.Defaults())
            if (!Buttons.TryGetValue(kv.Key, out var v) || !Valid(v)) Buttons[kv.Key] = kv.Value;
        if (!Valid(AnalogUp)) AnalogUp = KeyboardMapping.AnalogUp;
        if (!Valid(AnalogDown)) AnalogDown = KeyboardMapping.AnalogDown;
        if (!Valid(AnalogLeft)) AnalogLeft = KeyboardMapping.AnalogLeft;
        if (!Valid(AnalogRight)) AnalogRight = KeyboardMapping.AnalogRight;
        if (double.IsNaN(AnalogDeadZone)) AnalogDeadZone = 0.30;
        AnalogDeadZone = Math.Clamp(AnalogDeadZone, 0.05, 0.90);
    }

    /// <summary>One human-readable line per physical key that is bound to more than one control.</summary>
    public IReadOnlyList<string> FindConflicts()
    {
        var all = new List<(string Label, ushort Vk)>();
        foreach (var b in Enum.GetValues<ControllerButton>())
            if (Buttons.TryGetValue(b, out var vk)) all.Add((b.ToString(), vk));
        all.Add(("Analog Up", AnalogUp)); all.Add(("Analog Down", AnalogDown));
        all.Add(("Analog Left", AnalogLeft)); all.Add(("Analog Right", AnalogRight));

        return all.GroupBy(x => x.Vk).Where(g => g.Count() > 1).OrderBy(g => g.Key)
                  .Select(g => $"{KeyNames.Of(g.Key)} is used by {string.Join(", ", g.Select(x => x.Label))}")
                  .ToList();
    }
}
