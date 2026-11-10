using System.Net.WebSockets;
using System.Text;
using PSPReceiver.Input;
using PSPReceiver.Models;
using PSPReceiver.Network;
using PSPReceiver.Security;
using Xunit;

namespace PSPReceiver.Tests;

/// <summary>Real WebSocket client against the real server on loopback.</summary>
public class ServerIntegrationTests
{
    private sealed class NullSink : IInputSink
    {
        public void SetButton(ControllerButton b, bool d) { }
        public void SetAnalog(double x, double y) { }
    }

    private const string Hello = """{"type":"hello","version":1,"pin":"123456","clientName":"TestPhone"}""";

    private static (ReceiverServer server, InputStateTracker tracker) Start(AuthRateLimiter? limiter = null)
    {
        var tracker = new InputStateTracker(new NullSink());
        var server = new ReceiverServer(new PinManager("123456"), tracker, limiter ?? new AuthRateLimiter());
        server.Start(0);
        return (server, tracker);
    }

    private static async Task<ClientWebSocket> Connect(ReceiverServer s, CancellationToken ct)
    {
        var ws = new ClientWebSocket();
        await ws.ConnectAsync(new Uri($"ws://127.0.0.1:{s.Port}/"), ct);
        return ws;
    }

    private static Task Send(ClientWebSocket ws, string json, CancellationToken ct) =>
        ws.SendAsync(Encoding.UTF8.GetBytes(json), WebSocketMessageType.Text, true, ct);

    private static async Task<string> Recv(ClientWebSocket ws, CancellationToken ct)
    {
        var buf = new byte[2048];
        var r = await ws.ReceiveAsync(buf, ct);
        return Encoding.UTF8.GetString(buf, 0, r.Count);
    }

    private static async Task WaitFor(Func<bool> cond, int ms = 6000)
    {
        var until = DateTime.UtcNow.AddMilliseconds(ms);
        while (DateTime.UtcNow < until) { if (cond()) return; await Task.Delay(25); }
        Assert.True(cond(), "Condition not met in time");
    }

    [Fact] public async Task ValidPairing_ButtonsApplied_ThenCloseReleases()
    {
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(15));
        var (server, tracker) = Start();
        using var _ = server;
        using var ws = await Connect(server, cts.Token);

        await Send(ws, Hello, cts.Token);
        Assert.Contains("welcome", await Recv(ws, cts.Token));

        await Send(ws, """{"type":"button","version":1,"button":"CROSS","state":"down"}""", cts.Token);
        await Send(ws, """{"type":"button","version":1,"button":"L","state":"down"}""", cts.Token);
        await WaitFor(() => tracker.ActiveButtons.Count == 2);

        await ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "", cts.Token);
        await WaitFor(() => tracker.ActiveButtons.Count == 0);
    }

    [Fact] public async Task AbruptDisconnect_ReleasesInputs()
    {
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(15));
        var (server, tracker) = Start();
        using var _ = server;
        var ws = await Connect(server, cts.Token);
        await Send(ws, Hello, cts.Token);
        await Recv(ws, cts.Token);
        await Send(ws, """{"type":"button","version":1,"button":"START","state":"down"}""", cts.Token);
        await WaitFor(() => tracker.ActiveButtons.Count == 1);

        ws.Abort();
        ws.Dispose();
        await WaitFor(() => tracker.ActiveButtons.Count == 0);
    }

    [Fact] public async Task HeartbeatTimeout_ReleasesInputs()
    {
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(20));
        var (server, tracker) = Start();
        using var _ = server;
        using var ws = await Connect(server, cts.Token);
        await Send(ws, Hello, cts.Token);
        await Recv(ws, cts.Token);
        await Send(ws, """{"type":"button","version":1,"button":"SQUARE","state":"down"}""", cts.Token);
        await WaitFor(() => tracker.ActiveButtons.Count == 1);

        // Send nothing: server must time out after ~3 s and release.
        await WaitFor(() => tracker.ActiveButtons.Count == 0, 8000);
    }

    [Fact] public async Task WrongPin_Rejected_AndNoInputAccepted()
    {
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(15));
        var (server, tracker) = Start();
        using var _ = server;
        using var ws = await Connect(server, cts.Token);
        await Send(ws, """{"type":"hello","version":1,"pin":"000000","clientName":"Bad"}""", cts.Token);
        Assert.Contains("bad_pin", await Recv(ws, cts.Token));
        Assert.Empty(tracker.ActiveButtons);
    }

    [Fact] public async Task ButtonBeforeHello_Rejected()
    {
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(15));
        var (server, tracker) = Start();
        using var _ = server;
        using var ws = await Connect(server, cts.Token);
        await Send(ws, """{"type":"button","version":1,"button":"CROSS","state":"down"}""", cts.Token);
        Assert.Contains("not_paired", await Recv(ws, cts.Token));
        Assert.Empty(tracker.ActiveButtons);
    }

    [Fact] public async Task MalformedMessage_ClosesSession()
    {
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(15));
        var (server, _) = Start();
        using var _s = server;
        using var ws = await Connect(server, cts.Token);
        await Send(ws, "this is not json", cts.Token);
        Assert.Contains("bad_message", await Recv(ws, cts.Token));
    }

    [Fact] public async Task SecondController_GetsBusy()
    {
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(15));
        var (server, _) = Start();
        using var _s = server;
        using var a = await Connect(server, cts.Token);
        await Send(a, Hello, cts.Token);
        Assert.Contains("welcome", await Recv(a, cts.Token));

        using var b = await Connect(server, cts.Token);
        await Send(b, Hello, cts.Token);
        Assert.Contains("busy", await Recv(b, cts.Token));
    }

    [Fact] public async Task RepeatedBadPins_GetRateLimited()
    {
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(20));
        var (server, _) = Start();
        using var _s = server;
        string last = "";
        for (int i = 0; i < 6; i++)
        {
            using var ws = await Connect(server, cts.Token);
            await Send(ws, """{"type":"hello","version":1,"pin":"000000","clientName":"Bad"}""", cts.Token);
            last = await Recv(ws, cts.Token);
        }
        Assert.Contains("rate_limited", last);
    }

    [Fact] public async Task StopServer_ReleasesInputs()
    {
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(15));
        var (server, tracker) = Start();
        using var ws = await Connect(server, cts.Token);
        await Send(ws, Hello, cts.Token);
        await Recv(ws, cts.Token);
        await Send(ws, """{"type":"button","version":1,"button":"R","state":"down"}""", cts.Token);
        await WaitFor(() => tracker.ActiveButtons.Count == 1);

        server.Stop();
        Assert.Empty(tracker.ActiveButtons);
    }
}
