using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using PSPReceiver.Input;
using PSPReceiver.Models;
using PSPReceiver.Security;

namespace PSPReceiver.Network;

/// <summary>WebSocket server. One paired controller at a time. Every exit path releases all inputs.</summary>
public sealed class ReceiverServer : IDisposable
{
    public const int HelloTimeoutMs = 5000;
    public const int HeartbeatTimeoutMs = 3000;

    private readonly PinManager _pins;
    private readonly InputStateTracker _tracker;
    private readonly AuthRateLimiter _limiter;
    private readonly object _gate = new();
    private TcpListener? _listener;
    private CancellationTokenSource? _cts;
    private CancellationTokenSource? _activeSession;
    private long _inputCount;

    public event Action<string>? Log;
    public event Action<string?>? ClientChanged;
    public event Action<long>? InputCountChanged;

    public bool IsRunning { get; private set; }
    public int Port { get; private set; }

    public ReceiverServer(PinManager pins, InputStateTracker tracker, AuthRateLimiter limiter)
    {
        _pins = pins; _tracker = tracker; _limiter = limiter;
    }

    /// <summary>Start listening. Pass 0 to let the OS choose a port (tests). Throws SocketException if the port is taken.</summary>
    public void Start(int port)
    {
        if (IsRunning) return;
        var listener = new TcpListener(IPAddress.Any, port);
        listener.Start();
        _listener = listener;
        Port = ((IPEndPoint)listener.LocalEndpoint).Port;
        _cts = new CancellationTokenSource();
        Interlocked.Exchange(ref _inputCount, 0);
        IsRunning = true;
        var ct = _cts.Token;
        _ = Task.Run(() => AcceptLoopAsync(listener, ct));
        Log?.Invoke($"Server started on port {Port}");
    }

    public void Stop()
    {
        if (!IsRunning) return;
        IsRunning = false;
        _cts?.Cancel();
        try { _listener?.Stop(); } catch { }
        _listener = null;
        _tracker.ReleaseAll();
        Log?.Invoke("Server stopped");
    }

    public void DisconnectClient()
    {
        lock (_gate) _activeSession?.Cancel();
    }

    public void Dispose() => Stop();

    private async Task AcceptLoopAsync(TcpListener listener, CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            TcpClient tcp;
            try { tcp = await listener.AcceptTcpClientAsync(ct); }
            catch (OperationCanceledException) { break; }
            catch (ObjectDisposedException) { break; }
            catch (SocketException)
            {
                if (ct.IsCancellationRequested) break;
                try { await Task.Delay(100, ct); } catch (OperationCanceledException) { break; }
                continue;
            }
            _ = Task.Run(() => HandleAsync(tcp, ct));
        }
    }

    private async Task HandleAsync(TcpClient tcp, CancellationToken serverCt)
    {
        using var _ = tcp;
        var remote = (tcp.Client.RemoteEndPoint as IPEndPoint)?.Address;
        if (remote is null || !LocalNetwork.IsAllowedClient(remote))
        {
            Log?.Invoke($"Rejected connection from non-local address {remote}");
            return;
        }

        WebSocket? ws = null;
        CancellationTokenSource? session = null;
        bool claimed = false;
        string endReason = "closed";
        try
        {
            var stream = tcp.GetStream();

            using (var hs = CancellationTokenSource.CreateLinkedTokenSource(serverCt))
            {
                hs.CancelAfter(HelloTimeoutMs);
                var request = await ReadHeadersAsync(stream, hs.Token);
                var response = WebSocketHandshake.TryBuildResponse(request);
                if (response is null)
                {
                    await WriteAsciiAsync(stream, "HTTP/1.1 400 Bad Request\r\nConnection: close\r\nContent-Length: 0\r\n\r\n", hs.Token);
                    return;
                }
                await WriteAsciiAsync(stream, response, hs.Token);
            }

            ws = WebSocket.CreateFromStream(stream, new WebSocketCreationOptions
            {
                IsServer = true,
                KeepAliveInterval = Timeout.InfiniteTimeSpan
            });
            session = CancellationTokenSource.CreateLinkedTokenSource(serverCt);
            var buffer = new byte[MessageValidator.MaxFrameBytes + 1];
            bool authed = false;
            string clientName = "";

            while (ws.State == WebSocketState.Open)
            {
                using var rcts = CancellationTokenSource.CreateLinkedTokenSource(session.Token);
                rcts.CancelAfter(authed ? HeartbeatTimeoutMs : HelloTimeoutMs);
                var r = await ws.ReceiveAsync(buffer, rcts.Token);

                if (r.MessageType == WebSocketMessageType.Close) { endReason = "client closed"; break; }
                if (r.MessageType != WebSocketMessageType.Text || !r.EndOfMessage)
                {
                    await SendAsync(ws, ErrorJson("bad_message", "Invalid frame"), session.Token);
                    endReason = "bad frame"; break;
                }

                var v = MessageValidator.Parse(buffer.AsSpan(0, r.Count));
                if (!v.IsValid)
                {
                    await SendAsync(ws, ErrorJson(v.ErrorCode!, "Rejected message"), session.Token);
                    endReason = "invalid message"; break;
                }

                if (!authed)
                {
                    if (v.Message is not HelloMessage hello)
                    {
                        await SendAsync(ws, ErrorJson("not_paired", "Send hello first"), session.Token);
                        endReason = "not paired"; break;
                    }
                    if (_limiter.IsBlocked(remote))
                    {
                        await SendAsync(ws, ErrorJson("rate_limited", "Too many attempts"), session.Token);
                        endReason = "rate limited"; break;
                    }
                    if (!_pins.Verify(hello.Pin))
                    {
                        _limiter.RecordFailure(remote);
                        await SendAsync(ws, ErrorJson("bad_pin", "Pairing failed"), session.Token);
                        Log?.Invoke($"Wrong PIN from {remote}");
                        endReason = "bad pin"; break;
                    }
                    _limiter.RecordSuccess(remote);

                    lock (_gate)
                    {
                        if (_activeSession is null) { _activeSession = session; claimed = true; }
                    }
                    if (!claimed)
                    {
                        await SendAsync(ws, ErrorJson("busy", "Another controller is connected"), session.Token);
                        endReason = "busy"; break;
                    }

                    authed = true;
                    clientName = hello.ClientName;
                    await SendAsync(ws, JsonSerializer.Serialize(new { type = "welcome", version = 1, serverName = Environment.MachineName }), session.Token);
                    ClientChanged?.Invoke(clientName);
                    Log?.Invoke($"Paired with {clientName} ({remote})");
                    continue;
                }

                switch (v.Message)
                {
                    case HeartbeatMessage:
                        await SendAsync(ws, "{\"type\":\"heartbeat\",\"version\":1}", session.Token);
                        break;
                    case ButtonMessage b:
                        if (_tracker.SetButton(b.Button, b.IsDown)) CountInput();
                        break;
                    case AnalogMessage a:
                        if (_tracker.SetAnalog(a.X, a.Y)) CountInput();
                        break;
                    default:
                        await SendAsync(ws, ErrorJson("bad_message", "Unexpected message"), session.Token);
                        endReason = "unexpected message";
                        ws.Abort();
                        break;
                }
            }
        }
        catch (OperationCanceledException)
        {
            endReason = serverCt.IsCancellationRequested ? "server stopped" : (session?.IsCancellationRequested == true ? "disconnected by user" : "timeout");
        }
        catch (WebSocketException) { endReason = "connection lost"; }
        catch (IOException) { endReason = "connection lost"; }
        catch (Exception ex) { endReason = "error: " + ex.Message; }
        finally
        {
            if (claimed)
            {
                _tracker.ReleaseAll();
                lock (_gate) { if (ReferenceEquals(_activeSession, session)) _activeSession = null; }
                ClientChanged?.Invoke(null);
                Log?.Invoke($"Controller disconnected ({endReason}); all inputs released");
            }
            try
            {
                if (ws is { State: WebSocketState.Open })
                {
                    using var close = new CancellationTokenSource(1000);
                    await ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "bye", close.Token);
                }
            }
            catch { }
            ws?.Dispose();
            session?.Dispose();
        }
    }

    private void CountInput()
    {
        InputCountChanged?.Invoke(Interlocked.Increment(ref _inputCount));
    }

    private static string ErrorJson(string code, string message) =>
        JsonSerializer.Serialize(new { type = "error", version = 1, code, message });

    private static Task SendAsync(WebSocket ws, string json, CancellationToken ct) =>
        ws.SendAsync(Encoding.UTF8.GetBytes(json), WebSocketMessageType.Text, true, ct);

    private static async Task<string?> ReadHeadersAsync(NetworkStream s, CancellationToken ct)
    {
        var buf = new byte[4096];
        int len = 0;
        while (len < buf.Length)
        {
            int n = await s.ReadAsync(buf.AsMemory(len), ct);
            if (n == 0) return null;
            len += n;
            var text = Encoding.ASCII.GetString(buf, 0, len);
            if (text.Contains("\r\n\r\n", StringComparison.Ordinal)) return text;
        }
        return null;
    }

    private static Task WriteAsciiAsync(NetworkStream s, string text, CancellationToken ct) =>
        s.WriteAsync(Encoding.ASCII.GetBytes(text), ct).AsTask();
}
