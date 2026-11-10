using System.ComponentModel;
using System.Net.Sockets;
using System.Runtime.CompilerServices;
using System.Windows;
using PSPReceiver.Configuration;
using PSPReceiver.Gamepad;
using PSPReceiver.Input;
using PSPReceiver.Network;
using PSPReceiver.Security;

namespace PSPReceiver;

public sealed class MainViewModel : INotifyPropertyChanged, IDisposable
{
    private readonly AppSettings _settings = AppSettings.Load();
    private readonly WindowsKeyInjector _injector = new();
    private readonly DiagnosticsSink _diag = new();
    private readonly KeyboardOutput _keyboard;
    private readonly IGamepadOutput _gamepad = new UnavailableGamepadOutput();
    private readonly FanOutSink _keyboardSink, _gamepadSink;
    private readonly PinManager _pins = new();
    private readonly InputStateTracker _tracker;
    private readonly ReceiverServer _server;
    private InputMode _mode = InputMode.Keyboard;

    private string _serverStatus = "Stopped", _localIp = "", _portText = "", _pin = "";
    private string _clientName = "None", _connectionStatus = "Stopped", _activeInputs = "none";
    private string _gamepadStatus = "", _lastEvent = "", _keyConflicts = "";
    private long _inputCount;
    private bool _isRunning, _startWithWindows;

    public event PropertyChangedEventHandler? PropertyChanged;

    public MainViewModel()
    {
        _keyboard = new KeyboardOutput(_injector, _settings.Keys);
        _keyboard.Error += m => LastEvent = m;
        _keyboardSink = new FanOutSink(_keyboard, _diag);
        _gamepadSink = new FanOutSink(_gamepad, _diag);

        _tracker = new InputStateTracker(_keyboardSink);
        _server = new ReceiverServer(_pins, _tracker, new AuthRateLimiter());
        _server.Log += m => LastEvent = m;
        _server.ClientChanged += n =>
        {
            ClientName = n ?? "None";
            ConnectionStatus = n is null ? "Waiting for client" : "Connected";
        };
        _server.InputCountChanged += c => InputCount = c;
        _diag.Changed += () => ActiveInputs = _diag.Describe();

        PortText = _settings.Port.ToString();
        Pin = _pins.CurrentPin;
        GamepadStatus = $"{_gamepad.BackendName}: {_gamepad.StatusDetail}";
        _startWithWindows = StartupRegistration.IsEnabled();
        UpdateConflicts();
        RefreshNetwork();
    }

    public string ServerStatus { get => _serverStatus; private set => Set(ref _serverStatus, value); }
    public string LocalIp { get => _localIp; private set => Set(ref _localIp, value); }
    public string PortText { get => _portText; set => Set(ref _portText, value); }
    public string Pin { get => _pin; private set => Set(ref _pin, value); }
    public string ClientName { get => _clientName; private set => Set(ref _clientName, value); }
    public string ConnectionStatus { get => _connectionStatus; private set => Set(ref _connectionStatus, value); }
    public long InputCount { get => _inputCount; private set => Set(ref _inputCount, value); }
    public string ActiveInputs { get => _activeInputs; private set => Set(ref _activeInputs, value); }
    public string GamepadStatus { get => _gamepadStatus; private set => Set(ref _gamepadStatus, value); }
    public string LastEvent { get => _lastEvent; private set => Set(ref _lastEvent, value); }
    public string KeyConflictWarning { get => _keyConflicts; private set => Set(ref _keyConflicts, value); }

    public bool IsRunning
    {
        get => _isRunning;
        private set { if (Set(ref _isRunning, value)) { Raise(nameof(CanEditPort)); Raise(nameof(CanStart)); } }
    }
    public bool CanEditPort => !IsRunning;
    public bool CanStart => !IsRunning;
    public bool GamepadAvailable => _gamepad.Status != GamepadBackendStatus.Unavailable;

    public bool IsKeyboardMode { get => _mode == InputMode.Keyboard; set { if (value) SetMode(InputMode.Keyboard); } }
    public bool IsGamepadMode { get => _mode == InputMode.Gamepad; set { if (value) SetMode(InputMode.Gamepad); } }

    public bool MinimizeToTray
    {
        get => _settings.MinimizeToTray;
        set { if (_settings.MinimizeToTray != value) { _settings.MinimizeToTray = value; _settings.Save(); Raise(); } }
    }

    public bool StartWithWindows
    {
        get => _startWithWindows;
        set
        {
            if (value == _startWithWindows) return;
            if (StartupRegistration.Set(value)) { _startWithWindows = value; LastEvent = value ? "Will start with Windows" : "Will not start with Windows"; }
            else LastEvent = "Could not change the Windows startup setting";
            Raise();
        }
    }

    public KeyBindings CurrentKeys => _settings.Keys.Clone();

    public void ApplyKeyBindings(KeyBindings keys)
    {
        keys.Sanitize();
        _settings.Keys = keys;
        _settings.Save();
        _keyboard.UpdateBindings(keys);
        UpdateConflicts();
        LastEvent = "Key mapping saved";
    }

    private void UpdateConflicts()
    {
        var c = _settings.Keys.FindConflicts();
        KeyConflictWarning = c.Count == 0 ? "" : "Shared keys: " + string.Join("; ", c);
    }

    /// <summary>Press and release the given keys through SendInput (focus must be on the test window).</summary>
    public Task SelfTestAsync(params ushort[] virtualKeys) => Task.Run(() =>
    {
        foreach (var vk in virtualKeys)
        {
            _injector.Send(vk, true); Thread.Sleep(40);
            _injector.Send(vk, false); Thread.Sleep(40);
        }
    });

    public void Start()
    {
        if (!int.TryParse(PortText, out var port) || port < 1024 || port > 65535)
        {
            LastEvent = "Port must be between 1024 and 65535";
            return;
        }
        try { _server.Start(port); }
        catch (SocketException ex) { LastEvent = $"Cannot start server: {ex.Message}"; return; }

        _settings.Port = port;
        _settings.Save();
        IsRunning = true;
        ServerStatus = $"Running on port {_server.Port}";
        ConnectionStatus = "Waiting for client";
        RefreshNetwork();
    }

    public void Stop()
    {
        _server.Stop();
        _keyboard.ReleaseAllKeys();
        IsRunning = false;
        ServerStatus = "Stopped";
        ConnectionStatus = "Stopped";
        ClientName = "None";
    }

    public void RegeneratePin() { _pins.Regenerate(); Pin = _pins.CurrentPin; }
    public void DisconnectClient() => _server.DisconnectClient();

    public void RefreshNetwork()
    {
        var ips = LocalNetwork.GetLocalIPv4Addresses();
        LocalIp = ips.Count == 0 ? "No local network found" : string.Join(", ", ips);
    }

    private void SetMode(InputMode m)
    {
        if (m == _mode) return;
        if (m == InputMode.Gamepad)
        {
            if (!_gamepad.TryConnect())
            {
                LastEvent = _gamepad.StatusDetail;
                Raise(nameof(IsGamepadMode)); Raise(nameof(IsKeyboardMode));
                return;
            }
            _tracker.SwitchSink(_gamepadSink);   // releases through the keyboard sink first
        }
        else
        {
            _gamepad.Disconnect();
            _tracker.SwitchSink(_keyboardSink);
        }
        _mode = m;
        _settings.Mode = m;
        _settings.Save();
        Raise(nameof(IsGamepadMode)); Raise(nameof(IsKeyboardMode));
        LastEvent = $"Output mode: {m}";
    }

    public void Dispose()
    {
        _server.Stop();
        _tracker.ReleaseAll();
        _keyboard.ReleaseAllKeys();
        _gamepad.Dispose();
    }

    private bool Set<T>(ref T field, T value, [CallerMemberName] string? name = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value)) return false;
        field = value;
        Raise(name);
        return true;
    }

    private void Raise([CallerMemberName] string? name = null)
    {
        var d = Application.Current?.Dispatcher;
        if (d is null || d.CheckAccess()) PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
        else d.BeginInvoke(() => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name)));
    }
}
