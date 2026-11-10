using PSPReceiver.Models;

namespace PSPReceiver.Input;

/// <summary>Default keyboard layout as Windows virtual-key codes (VK_*). Editable at runtime in later phases.</summary>
public static class KeyboardMapping
{
    public static Dictionary<ControllerButton, ushort> Defaults() => new()
    {
        [ControllerButton.Up] = 0x26,       // VK_UP
        [ControllerButton.Down] = 0x28,     // VK_DOWN
        [ControllerButton.Left] = 0x25,     // VK_LEFT
        [ControllerButton.Right] = 0x27,    // VK_RIGHT
        [ControllerButton.Cross] = 0x5A,    // Z
        [ControllerButton.Circle] = 0x58,   // X
        [ControllerButton.Square] = 0x41,   // A
        [ControllerButton.Triangle] = 0x53, // S
        [ControllerButton.L] = 0x51,        // Q
        [ControllerButton.R] = 0x57,        // W
        [ControllerButton.Start] = 0x0D,    // VK_RETURN
        [ControllerButton.Select] = 0xA1,   // VK_RSHIFT
    };

    // Analog-as-keys defaults from the spec: Up=W, Down=S, Left=A, Right=D.
    // NOTE: these collide with R=W, Triangle=S and Square=A. The Phase 3 mapping editor must warn on conflicts.
    public const ushort AnalogUp = 0x57, AnalogDown = 0x53, AnalogLeft = 0x41, AnalogRight = 0x44;
}
