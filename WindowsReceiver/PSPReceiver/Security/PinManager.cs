using System.Security.Cryptography;
using System.Text;

namespace PSPReceiver.Security;

/// <summary>
/// Generates the 6-digit pairing PIN. Verification uses a salted SHA-256 hash with a constant-time
/// compare; the plaintext PIN is kept in memory only so the dashboard can display it. Never persisted.
/// </summary>
public sealed class PinManager
{
    private readonly object _gate = new();
    private byte[] _salt = Array.Empty<byte>();
    private byte[] _hash = Array.Empty<byte>();

    public string CurrentPin { get; private set; } = "";

    public PinManager() => Regenerate();

    /// <summary>For tests only.</summary>
    public PinManager(string fixedPin) => Set(fixedPin);

    public void Regenerate() => Set(RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6"));

    private void Set(string pin)
    {
        lock (_gate)
        {
            _salt = RandomNumberGenerator.GetBytes(16);
            _hash = Hash(pin, _salt);
            CurrentPin = pin;
        }
    }

    public bool Verify(string candidate)
    {
        byte[] salt, expected;
        lock (_gate) { salt = _salt; expected = _hash; }
        return CryptographicOperations.FixedTimeEquals(Hash(candidate, salt), expected);
    }

    private static byte[] Hash(string pin, byte[] salt)
    {
        var pinBytes = Encoding.UTF8.GetBytes(pin);
        var data = new byte[salt.Length + pinBytes.Length];
        salt.CopyTo(data, 0);
        pinBytes.CopyTo(data, salt.Length);
        return SHA256.HashData(data);
    }
}
