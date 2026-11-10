using PSPReceiver.Models;

namespace PSPReceiver.Input;

/// <summary>
/// Keyboard output backend. Reference-counts physical keys so two controls bound to the same key
/// (e.g. R and analog-up both on W) never release it early or leave it stuck.
/// </summary>
public sealed class KeyboardOutput : IInputSink
{
    private readonly IKeyInjector _inj;
    private readonly object _gate = new();
    private KeyBindings _b;
    private readonly Dictionary<ushort, int> _refs = new();
    private readonly Dictionary<ControllerButton, ushort> _buttonHeld = new();
    private readonly ushort?[] _analogHeld = new ushort?[4]; // 0 up, 1 down, 2 left, 3 right

    public event Action<string>? Error;

    public KeyboardOutput(IKeyInjector injector, KeyBindings bindings)
    {
        _inj = injector;
        _b = bindings.Clone();
    }

    public IReadOnlyCollection<ushort> HeldKeys { get { lock (_gate) return _refs.Keys.ToArray(); } }

    public void SetButton(ControllerButton button, bool isDown)
    {
        lock (_gate)
        {
            if (isDown)
            {
                if (_buttonHeld.ContainsKey(button)) return;
                if (!_b.Buttons.TryGetValue(button, out var vk)) return;
                _buttonHeld[button] = vk;
                Acquire(vk);
            }
            else
            {
                if (!_buttonHeld.Remove(button, out var vk)) return;
                Release(vk);
            }
        }
    }

    public void SetAnalog(double x, double y)
    {
        lock (_gate)
        {
            double dz = _b.AnalogDeadZone;
            bool[] want = { y > dz, y < -dz, x < -dz, x > dz };
            ushort[] keys = { _b.AnalogUp, _b.AnalogDown, _b.AnalogLeft, _b.AnalogRight };
            for (int i = 0; i < 4; i++)
            {
                if (want[i] && _analogHeld[i] is null) { _analogHeld[i] = keys[i]; Acquire(keys[i]); }
                else if (!want[i] && _analogHeld[i] is { } held) { _analogHeld[i] = null; Release(held); }
            }
        }
    }

    /// <summary>Apply a new layout. Releases everything first so no key stays down under the old mapping.</summary>
    public void UpdateBindings(KeyBindings bindings)
    {
        lock (_gate)
        {
            ReleaseAllKeys();
            _b = bindings.Clone();
        }
    }

    /// <summary>Emergency release of every key this object pressed.</summary>
    public void ReleaseAllKeys()
    {
        lock (_gate)
        {
            foreach (var vk in _refs.Keys.ToArray()) SendUp(vk);
            _refs.Clear();
            _buttonHeld.Clear();
            Array.Clear(_analogHeld);
        }
    }

    private void Acquire(ushort vk)
    {
        _refs.TryGetValue(vk, out var n);
        _refs[vk] = n + 1;
        if (n == 0 && !_inj.Send(vk, true)) Error?.Invoke($"Windows rejected key-down for {KeyNames.Of(vk)}");
    }

    private void Release(ushort vk)
    {
        if (!_refs.TryGetValue(vk, out var n)) return;
        if (n > 1) { _refs[vk] = n - 1; return; }
        _refs.Remove(vk);
        SendUp(vk);
    }

    private void SendUp(ushort vk)
    {
        if (_inj.Send(vk, false)) return;
        if (_inj.Send(vk, false)) return;   // one retry: a stuck key is the worst failure
        Error?.Invoke($"Windows rejected key-up for {KeyNames.Of(vk)}");
    }
}
