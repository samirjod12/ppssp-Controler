using PSPReceiver.Input;
using PSPReceiver.Models;

namespace PSPReceiver.Gamepad;

public enum GamepadBackendStatus { Unavailable, Available, Connected, Error }

/// <summary>Virtual XInput-style output. Implementations must never report Connected unless a real device exists.</summary>
public interface IGamepadOutput : IInputSink, IDisposable
{
    string BackendName { get; }
    GamepadBackendStatus Status { get; }
    string StatusDetail { get; }
    bool TryConnect();
    void Disconnect();
}

/// <summary>Used when no driver backend exists (default until Phase 7). Honest about being unavailable.</summary>
public sealed class UnavailableGamepadOutput : IGamepadOutput
{
    public string BackendName => "None";
    public GamepadBackendStatus Status => GamepadBackendStatus.Unavailable;
    public string StatusDetail => "No compatible virtual gamepad driver is installed. Keyboard mode is available.";
    public bool TryConnect() => false;
    public void Disconnect() { }
    public void SetButton(ControllerButton button, bool isDown) { }
    public void SetAnalog(double x, double y) { }
    public void Dispose() { }
}
