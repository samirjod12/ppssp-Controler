using System.Runtime.InteropServices;

namespace PSPReceiver.Input;

public interface IKeyInjector
{
    /// <summary>Send one key event. Returns false if Windows rejected it.</summary>
    bool Send(ushort virtualKey, bool down);
}

/// <summary>
/// SendInput using hardware scan codes (games and emulators generally read scan codes / raw state, not just VK messages).
/// Limitation: Windows (UIPI) ignores injected input aimed at an elevated window when this app is not elevated.
/// </summary>
public sealed class WindowsKeyInjector : IKeyInjector
{
    private const uint INPUT_KEYBOARD = 1;
    private const uint KEYEVENTF_EXTENDEDKEY = 0x0001;
    private const uint KEYEVENTF_KEYUP = 0x0002;
    private const uint KEYEVENTF_SCANCODE = 0x0008;
    private const uint MAPVK_VK_TO_VSC = 0;

    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT { public int dx, dy; public uint mouseData, dwFlags, time; public IntPtr dwExtraInfo; }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT { public ushort wVk, wScan; public uint dwFlags, time; public IntPtr dwExtraInfo; }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)] public MOUSEINPUT mi;   // present so the union has the correct (largest) size
        [FieldOffset(0)] public KEYBDINPUT ki;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT { public uint type; public InputUnion u; }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    [DllImport("user32.dll")]
    private static extern uint MapVirtualKey(uint uCode, uint uMapType);

    private static bool IsExtended(ushort vk) =>
        vk is (>= 0x21 and <= 0x28) or 0x2D or 0x2E or 0xA3 or 0xA5 or 0x90 or 0x6F;

    public bool Send(ushort vk, bool down)
    {
        ushort scan = vk == 0xA1 ? (ushort)0x36 : (ushort)MapVirtualKey(vk, MAPVK_VK_TO_VSC); // right shift quirk
        var ki = new KEYBDINPUT();
        uint flags = down ? 0u : KEYEVENTF_KEYUP;
        if (scan != 0)
        {
            ki.wScan = scan;
            flags |= KEYEVENTF_SCANCODE;
            if (IsExtended(vk)) flags |= KEYEVENTF_EXTENDEDKEY;
        }
        else ki.wVk = vk;
        ki.dwFlags = flags;

        var input = new INPUT { type = INPUT_KEYBOARD, u = new InputUnion { ki = ki } };
        return SendInput(1, new[] { input }, Marshal.SizeOf<INPUT>()) == 1;
    }
}
