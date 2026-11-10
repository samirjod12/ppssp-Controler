using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace PSPReceiver.Network;

public static class LocalNetwork
{
    /// <summary>True for loopback, RFC1918, link-local and IPv6 unique-local addresses only.</summary>
    public static bool IsAllowedClient(IPAddress ip)
    {
        if (ip.IsIPv4MappedToIPv6) ip = ip.MapToIPv4();
        if (IPAddress.IsLoopback(ip)) return true;

        if (ip.AddressFamily == AddressFamily.InterNetwork)
        {
            var b = ip.GetAddressBytes();
            return b[0] == 10
                || (b[0] == 172 && b[1] >= 16 && b[1] <= 31)
                || (b[0] == 192 && b[1] == 168)
                || (b[0] == 169 && b[1] == 254);
        }
        if (ip.AddressFamily == AddressFamily.InterNetworkV6)
            return ip.IsIPv6LinkLocal || (ip.GetAddressBytes()[0] & 0xFE) == 0xFC;
        return false;
    }

    /// <summary>Active private IPv4 addresses of this PC (what the phone should type in).</summary>
    public static IReadOnlyList<string> GetLocalIPv4Addresses()
    {
        var result = new List<string>();
        try
        {
            foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (nic.OperationalStatus != OperationalStatus.Up) continue;
                if (nic.NetworkInterfaceType is NetworkInterfaceType.Loopback or NetworkInterfaceType.Tunnel) continue;
                foreach (var ua in nic.GetIPProperties().UnicastAddresses)
                {
                    var a = ua.Address;
                    if (a.AddressFamily == AddressFamily.InterNetwork && IsAllowedClient(a) && !IPAddress.IsLoopback(a))
                        result.Add(a.ToString());
                }
            }
        }
        catch (NetworkInformationException) { }
        return result;
    }
}
