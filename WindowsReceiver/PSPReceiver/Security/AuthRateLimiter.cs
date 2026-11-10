using System.Net;

namespace PSPReceiver.Security;

/// <summary>Blocks an IP for a period after too many failed PIN attempts.</summary>
public sealed class AuthRateLimiter
{
    private readonly Func<DateTime> _now;
    private readonly int _maxFailures;
    private readonly TimeSpan _blockFor;
    private readonly object _gate = new();
    private readonly Dictionary<IPAddress, (int Fails, DateTime BlockedUntil)> _state = new();

    public AuthRateLimiter(Func<DateTime>? now = null, int maxFailures = 5, TimeSpan? blockFor = null)
    {
        _now = now ?? (() => DateTime.UtcNow);
        _maxFailures = maxFailures;
        _blockFor = blockFor ?? TimeSpan.FromSeconds(60);
    }

    private static IPAddress Norm(IPAddress ip) => ip.IsIPv4MappedToIPv6 ? ip.MapToIPv4() : ip;

    public bool IsBlocked(IPAddress ip)
    {
        lock (_gate) return _state.TryGetValue(Norm(ip), out var e) && e.BlockedUntil > _now();
    }

    public void RecordFailure(IPAddress ip)
    {
        var k = Norm(ip);
        lock (_gate)
        {
            var n = _now();
            _state.TryGetValue(k, out var e);
            int fails = e.BlockedUntil != default && e.BlockedUntil <= n ? 1 : e.Fails + 1;
            _state[k] = (fails, fails >= _maxFailures ? n + _blockFor : default);
        }
    }

    public void RecordSuccess(IPAddress ip)
    {
        lock (_gate) _state.Remove(Norm(ip));
    }
}
