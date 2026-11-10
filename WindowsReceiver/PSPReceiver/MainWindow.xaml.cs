using System.ComponentModel;
using System.Windows;

namespace PSPReceiver;

public partial class MainWindow : Window
{
    private readonly MainViewModel _vm = new();
    private readonly TrayController _tray;

    public MainWindow()
    {
        InitializeComponent();
        DataContext = _vm;
        _tray = new TrayController(RestoreFromTray, () => Close());
        StateChanged += OnStateChanged;
        Loaded += (_, _) =>
        {
            // "--tray" is passed by the start-with-Windows entry: begin hidden in the tray.
            if (Environment.GetCommandLineArgs().Contains("--tray")) WindowState = WindowState.Minimized;
        };
    }

    private void OnStateChanged(object? sender, EventArgs e)
    {
        if (WindowState == WindowState.Minimized && _vm.MinimizeToTray)
        {
            Hide();
            _tray.Visible = true;
        }
    }

    private void RestoreFromTray()
    {
        Show();
        WindowState = WindowState.Normal;
        Activate();
        _tray.Visible = false;
    }

    private void OnStart(object sender, RoutedEventArgs e) => _vm.Start();
    private void OnStop(object sender, RoutedEventArgs e) => _vm.Stop();
    private void OnNewPin(object sender, RoutedEventArgs e) => _vm.RegeneratePin();
    private void OnDisconnect(object sender, RoutedEventArgs e) => _vm.DisconnectClient();

    private void OnKeyMapping(object sender, RoutedEventArgs e)
    {
        var w = new MappingWindow(_vm.CurrentKeys) { Owner = this };
        if (w.ShowDialog() == true) _vm.ApplyKeyBindings(w.Result);
    }

    private void OnTestController(object sender, RoutedEventArgs e) =>
        new TestWindow(_vm) { Owner = this }.Show();

    private void OnClosing(object? sender, CancelEventArgs e) => _vm.Dispose();
    private void OnClosed(object? sender, EventArgs e) => _tray.Dispose();
}
