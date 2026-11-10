using PSPReceiver.Models;

namespace PSPReceiver.Input;

/// <summary>Output backend (keyboard, virtual gamepad). Called only on real state changes.</summary>
public interface IInputSink
{
    void SetButton(ControllerButton button, bool isDown);
    void SetAnalog(double x, double y);
}

/// <summary>
/// Single source of truth for what is currently held. Suppresses duplicates and
/// guarantees ReleaseAll() leaves nothing pressed.
/// </summary>
public sealed class InputStateTracker
{
    private readonly object _gate = new();
    private readonly HashSet<ControllerButton> _down = new();
    private double _x, _y;
    private IInputSink _sink;

    public InputStateTracker(IInputSink sink) => _sink = sink;

    public IReadOnlyCollection<ControllerButton> ActiveButtons { get { lock (_gate) return _down.ToArray(); } }

    public bool SetButton(ControllerButton button, bool isDown)
    {
        lock (_gate)
        {
            bool changed = isDown ? _down.Add(button) : _down.Remove(button);
            if (!changed) return false;           // duplicate event
            _sink.SetButton(button, isDown);
            return true;
        }
    }

    public bool SetAnalog(double x, double y)
    {
        lock (_gate)
        {
            if (x == _x && y == _y) return false;
            _x = x; _y = y;
            _sink.SetAnalog(x, y);
            return true;
        }
    }

    /// <summary>Release everything. Continues even if the sink throws, so our own state never stays "held".</summary>
    public void ReleaseAll()
    {
        lock (_gate)
        {
            foreach (var b in _down.ToArray())
            {
                try { _sink.SetButton(b, false); } catch { /* keep releasing the rest */ }
            }
            _down.Clear();
            if (_x != 0 || _y != 0)
            {
                try { _sink.SetAnalog(0, 0); } catch { }
                _x = _y = 0;
            }
        }
    }

    /// <summary>Switch output backend (mode change). Releases through the old sink first.</summary>
    public void SwitchSink(IInputSink newSink)
    {
        lock (_gate)
        {
            ReleaseAll();
            _sink = newSink;
        }
    }
}
