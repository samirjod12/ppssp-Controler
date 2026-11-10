using System.Windows;
using System.Windows.Input;

namespace PSPReceiver;

public partial class TestWindow : Window
{
    private readonly MainViewModel _vm;

    public TestWindow(MainViewModel vm)
    {
        _vm = vm;
        InitializeComponent();
        DataContext = vm;
        Loaded += (_, _) => Capture.Focus();
    }

    private static string Name(KeyEventArgs e) =>
        (e.Key switch { Key.System => e.SystemKey, Key.ImeProcessed => e.ImeProcessedKey, _ => e.Key }).ToString();

    private void OnKeyDown(object sender, KeyEventArgs e)
    {
        e.Handled = true;
        if (!e.IsRepeat) Log("Down  " + Name(e));
    }

    private void OnKeyUp(object sender, KeyEventArgs e)
    {
        e.Handled = true;
        Log("Up    " + Name(e));
    }

    private async void OnSelfTest(object sender, RoutedEventArgs e)
    {
        Capture.Focus();
        await Task.Delay(150);               // let focus settle before injecting
        await _vm.SelfTestAsync(0x5A, 0x58); // Z, X
        Log("Self-test finished: expect Down/Up Z and X above. If missing, SendInput is not reaching this window.");
    }

    private void Log(string message)
    {
        LogList.Items.Insert(0, $"{DateTime.Now:HH:mm:ss.fff}  {message}");
        if (LogList.Items.Count > 200) LogList.Items.RemoveAt(200);
    }
}
