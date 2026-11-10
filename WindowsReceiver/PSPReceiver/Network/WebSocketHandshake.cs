using System.Security.Cryptography;
using System.Text;

namespace PSPReceiver.Network;

/// <summary>
/// Minimal RFC 6455 server handshake. We use a raw TcpListener instead of HttpListener because
/// HttpListener needs a URL reservation (administrator) to listen on a LAN address.
/// </summary>
public static class WebSocketHandshake
{
    private const string WsGuid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    public static string ComputeAccept(string key) =>
        Convert.ToBase64String(SHA1.HashData(Encoding.ASCII.GetBytes(key + WsGuid)));

    /// <summary>Returns the full 101 response, or null if the request is not a valid WebSocket upgrade.</summary>
    public static string? TryBuildResponse(string? request)
    {
        if (request is null) return null;
        var lines = request.Split("\r\n");
        if (!lines[0].StartsWith("GET ", StringComparison.Ordinal)) return null;

        var h = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (var line in lines.Skip(1))
        {
            if (line.Length == 0) break;
            var i = line.IndexOf(':');
            if (i <= 0) continue;
            h[line[..i].Trim()] = line[(i + 1)..].Trim();
        }

        if (!h.TryGetValue("Upgrade", out var up) || !up.Equals("websocket", StringComparison.OrdinalIgnoreCase)) return null;
        if (!h.TryGetValue("Connection", out var conn) || !conn.Contains("Upgrade", StringComparison.OrdinalIgnoreCase)) return null;
        if (!h.TryGetValue("Sec-WebSocket-Version", out var ver) || ver != "13") return null;
        if (!h.TryGetValue("Sec-WebSocket-Key", out var key) || key.Length is 0 or > 64) return null;

        return "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
             + "Sec-WebSocket-Accept: " + ComputeAccept(key) + "\r\n\r\n";
    }
}
