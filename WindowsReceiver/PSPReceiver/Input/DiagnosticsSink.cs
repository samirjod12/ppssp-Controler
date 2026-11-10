using PSPReceiver.Models;

namespace PSPReceiver.Input;

/// <summary>Records what is held without sending anything to Windows. Used for the dashboard until SendInput lands in Phase 3.</summary>
public sealed class DiagnosticsSink : IInputSink
{
    private readonly object _gate = new();
    private readonly SortedSet<ControllerButton> _down = new();
    private double _x, _y;

    public event Action? Changed;

    public string Describe()
    {
        lock (_gate)
        {
            var parts = _down.Select(b => b.ToString()).ToList();
            if (_x != 0 || _y != 0) parts.Add($"Analog({_x:0.00}, {_y:0.00})");
            return parts.Count == 0 ? "none" : string.Join(", ", parts);
        }
    }

    public void SetButton(ControllerButton button, bool isDown)
    {
        lock (_gate) { if (isDown) _down.Add(button); else _down.Remove(button); }
        Changed?.Invoke();
    }

    public void SetAnalog(double x, double y)
    {
        lock (_gate) { _x = x; _y = y; }
        Changed?.Invoke();
    }
}
