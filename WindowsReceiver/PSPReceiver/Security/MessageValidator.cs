using System.Text;
using System.Text.Json;
using PSPReceiver.Models;

namespace PSPReceiver.Security;

public sealed record ValidationResult(ProtocolMessage? Message, string? ErrorCode)
{
    public bool IsValid => Message is not null;
    public static ValidationResult Ok(ProtocolMessage m) => new(m, null);
    public static ValidationResult Fail(string code) => new(null, code);
}

/// <summary>Strict validation of every incoming frame. Never throws on bad input.</summary>
public static class MessageValidator
{
    public const int MaxFrameBytes = 1024;
    public const int ProtocolVersion = 1;

    private static readonly Dictionary<string, ControllerButton> ButtonNames =
        Enum.GetValues<ControllerButton>().ToDictionary(b => b.ToString().ToUpperInvariant());

    public static ValidationResult Parse(ReadOnlySpan<byte> utf8)
    {
        if (utf8.Length == 0 || utf8.Length > MaxFrameBytes) return ValidationResult.Fail("bad_message");
        try
        {
            using var doc = JsonDocument.Parse(utf8.ToArray());
            var root = doc.RootElement;
            if (root.ValueKind != JsonValueKind.Object) return ValidationResult.Fail("bad_message");

            if (!root.TryGetProperty("version", out var v) || v.ValueKind != JsonValueKind.Number
                || !v.TryGetInt32(out var ver)) return ValidationResult.Fail("bad_message");
            if (ver != ProtocolVersion) return ValidationResult.Fail("unsupported_version");

            if (!root.TryGetProperty("type", out var t) || t.ValueKind != JsonValueKind.String)
                return ValidationResult.Fail("bad_message");

            switch (t.GetString())
            {
                case "heartbeat":
                    return ValidationResult.Ok(new HeartbeatMessage());

                case "hello":
                {
                    var pin = Str(root, "pin");
                    var name = Str(root, "clientName");
                    if (pin is null || pin.Length != 6 || !pin.All(c => c is >= '0' and <= '9'))
                        return ValidationResult.Fail("bad_message");
                    if (name is null || name.Length is < 1 or > 40 || name.Any(char.IsControl))
                        return ValidationResult.Fail("bad_message");
                    return ValidationResult.Ok(new HelloMessage(pin, name));
                }

                case "button":
                {
                    var b = Str(root, "button");
                    var s = Str(root, "state");
                    if (b is null || !ButtonNames.TryGetValue(b, out var button)) return ValidationResult.Fail("bad_message");
                    if (s is not ("down" or "up")) return ValidationResult.Fail("bad_message");
                    return ValidationResult.Ok(new ButtonMessage(button, s == "down"));
                }

                case "analog":
                {
                    if (!Num(root, "x", out var x) || !Num(root, "y", out var y)) return ValidationResult.Fail("bad_message");
                    return ValidationResult.Ok(new AnalogMessage(Math.Clamp(x, -1.0, 1.0), Math.Clamp(y, -1.0, 1.0)));
                }

                default:
                    return ValidationResult.Fail("bad_message");
            }
        }
        catch (JsonException) { return ValidationResult.Fail("bad_message"); }
    }

    public static ValidationResult Parse(string json) => Parse(Encoding.UTF8.GetBytes(json));

    private static string? Str(JsonElement e, string name) =>
        e.TryGetProperty(name, out var p) && p.ValueKind == JsonValueKind.String ? p.GetString() : null;

    private static bool Num(JsonElement e, string name, out double value)
    {
        value = 0;
        return e.TryGetProperty(name, out var p) && p.ValueKind == JsonValueKind.Number
               && p.TryGetDouble(out value) && double.IsFinite(value);
    }
}
