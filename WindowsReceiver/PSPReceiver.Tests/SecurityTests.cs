using System.IO;
using System.Net;
using PSPReceiver.Configuration;
using PSPReceiver.Network;
using PSPReceiver.Security;
using Xunit;

namespace PSPReceiver.Tests;

public class SecurityTests
{
    [Fact] public void Pin_FixedVerifies_WrongFails()
    {
        var p = new PinManager("123456");
        Assert.True(p.Verify("123456"));
        Assert.False(p.Verify("654321"));
    }

    [Fact] public void Pin_Generated_IsSixDigits()
    {
        for (int i = 0; i < 50; i++)
        {
            var p = new PinManager();
            Assert.Equal(6, p.CurrentPin.Length);
            Assert.All(p.CurrentPin, c => Assert.InRange(c, '0', '9'));
            Assert.True(p.Verify(p.CurrentPin));
        }
    }

    [Fact] public void Pin_Regenerate_InvalidatesOld()
    {
        var p = new PinManager("111111");
        p.Regenerate();
        if (p.CurrentPin != "111111") Assert.False(p.Verify("111111"));
    }

    [Fact] public void RateLimiter_BlocksAfterFiveFailures_ThenExpires()
    {
        var now = new DateTime(2026, 1, 1, 0, 0, 0, DateTimeKind.Utc);
        var l = new AuthRateLimiter(() => now);
        var ip = IPAddress.Parse("192.168.1.20");
        for (int i = 0; i < 4; i++) { l.RecordFailure(ip); Assert.False(l.IsBlocked(ip)); }
        l.RecordFailure(ip);
        Assert.True(l.IsBlocked(ip));
        now = now.AddSeconds(61);
        Assert.False(l.IsBlocked(ip));
    }

    [Fact] public void RateLimiter_SuccessResets()
    {
        var l = new AuthRateLimiter();
        var ip = IPAddress.Parse("10.0.0.5");
        for (int i = 0; i < 4; i++) l.RecordFailure(ip);
        l.RecordSuccess(ip);
        l.RecordFailure(ip);
        Assert.False(l.IsBlocked(ip));
    }

    [Theory]
    [InlineData("127.0.0.1", true)]
    [InlineData("192.168.0.10", true)]
    [InlineData("10.1.2.3", true)]
    [InlineData("172.16.0.1", true)]
    [InlineData("172.31.255.255", true)]
    [InlineData("169.254.3.4", true)]
    [InlineData("172.32.0.1", false)]
    [InlineData("8.8.8.8", false)]
    [InlineData("203.0.113.9", false)]
    [InlineData("::1", true)]
    [InlineData("fe80::1", true)]
    [InlineData("fd00::1", true)]
    [InlineData("2001:4860:4860::8888", false)]
    [InlineData("::ffff:192.168.1.5", true)]
    [InlineData("::ffff:8.8.8.8", false)]
    public void LocalNetwork_Filtering(string ip, bool allowed) =>
        Assert.Equal(allowed, LocalNetwork.IsAllowedClient(IPAddress.Parse(ip)));

    [Fact] public void Handshake_Rfc6455SampleKey() =>
        Assert.Equal("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", WebSocketHandshake.ComputeAccept("dGhlIHNhbXBsZSBub25jZQ=="));

    [Fact] public void Handshake_ValidRequest_Gives101()
    {
        var req = "GET / HTTP/1.1\r\nHost: x\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                  "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n";
        var res = WebSocketHandshake.TryBuildResponse(req);
        Assert.NotNull(res);
        Assert.StartsWith("HTTP/1.1 101", res);
        Assert.Contains("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", res);
    }

    [Theory]
    [InlineData("GET / HTTP/1.1\r\nHost: x\r\n\r\n")]
    [InlineData("POST / HTTP/1.1\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: a\r\nSec-WebSocket-Version: 13\r\n\r\n")]
    [InlineData("GET / HTTP/1.1\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: a\r\nSec-WebSocket-Version: 8\r\n\r\n")]
    [InlineData("GET / HTTP/1.1\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\n\r\n")]
    public void Handshake_Invalid_ReturnsNull(string req) => Assert.Null(WebSocketHandshake.TryBuildResponse(req));

    [Fact] public void Settings_RoundTrip_AndBadValuesSanitized()
    {
        var path = Path.Combine(Path.GetTempPath(), Guid.NewGuid() + ".json");
        try
        {
            new AppSettings { Port = 9000, Mode = InputMode.Gamepad }.Save(path);
            var s = AppSettings.Load(path);
            Assert.Equal(9000, s.Port);
            Assert.Equal(InputMode.Gamepad, s.Mode);

            File.WriteAllText(path, """{"Port":22,"Mode":99}""");
            s = AppSettings.Load(path);
            Assert.Equal(8765, s.Port);
            Assert.Equal(InputMode.Keyboard, s.Mode);

            File.WriteAllText(path, "garbage");
            Assert.Equal(8765, AppSettings.Load(path).Port);
        }
        finally { File.Delete(path); }
    }
}
