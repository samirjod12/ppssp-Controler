using System.Windows.Input;

namespace PSPReceiver.Input;

public static class KeyNames
{
    public static string Of(ushort vk)
    {
        var key = KeyInterop.KeyFromVirtualKey(vk);
        return key == Key.None ? $"VK 0x{vk:X2}" : key.ToString();
    }
}
