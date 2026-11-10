using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using PSPReceiver.Input;
using PSPReceiver.Models;

namespace PSPReceiver;

public partial class MappingWindow : Window
{
    private sealed record Row(string Label, Func<ushort> Get, Action<ushort> Set, Button Btn);

    private readonly KeyBindings _k;
    private readonly List<Row> _rows = new();
    private Row? _capturing;

    public KeyBindings Result => _k;

    public MappingWindow(KeyBindings current)
    {
        _k = current.Clone();
        InitializeComponent();
        foreach (var b in Enum.GetValues<ControllerButton>())
        {
            var btn = b;
            AddRow(btn.ToString(), () => _k.Buttons[btn], v => _k.Buttons[btn] = v);
        }
        AddRow("Analog Up", () => _k.AnalogUp, v => _k.AnalogUp = v);
        AddRow("Analog Down", () => _k.AnalogDown, v => _k.AnalogDown = v);
        AddRow("Analog Left", () => _k.AnalogLeft, v => _k.AnalogLeft = v);
        AddRow("Analog Right", () => _k.AnalogRight, v => _k.AnalogRight = v);
        DeadZone.Value = _k.AnalogDeadZone;
        Refresh();
    }

    private void AddRow(string label, Func<ushort> get, Action<ushort> set)
    {
        var grid = new Grid { Margin = new Thickness(0, 2, 0, 2) };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(170) });
        var text = new TextBlock { Text = label, VerticalAlignment = VerticalAlignment.Center, Foreground = System.Windows.Media.Brushes.WhiteSmoke };
        var button = new Button { Padding = new Thickness(8, 4, 8, 4) };
        Grid.SetColumn(button, 1);
        grid.Children.Add(text);
        grid.Children.Add(button);
        Rows.Children.Add(grid);

        var row = new Row(label, get, set, button);
        button.Click += (_, _) => { Cancel(); _capturing = row; button.Content = "Press a key..."; };
        _rows.Add(row);
    }

    private void Cancel()
    {
        _capturing = null;
        Refresh();
    }

    private void Refresh()
    {
        foreach (var r in _rows) r.Btn.Content = KeyNames.Of(r.Get());
        var c = _k.FindConflicts();
        Warning.Text = c.Count == 0 ? "" : "Shared keys (allowed, but both controls press the same key):\n" + string.Join("\n", c);
    }

    private void OnPreviewKeyDown(object sender, KeyEventArgs e)
    {
        if (_capturing is null) return;
        e.Handled = true;
        var key = e.Key switch { Key.System => e.SystemKey, Key.ImeProcessed => e.ImeProcessedKey, _ => e.Key };
        if (key == Key.Escape) { Cancel(); return; }
        int vk = KeyInterop.VirtualKeyFromKey(key);
        if (vk is < 0x08 or > 0xFE) return;
        _capturing.Set((ushort)vk);
        _capturing = null;
        Refresh();
    }

    private void OnDeadZoneChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (DeadZoneText is null || _k is null) return;
        _k.AnalogDeadZone = Math.Round(e.NewValue, 2);
        DeadZoneText.Text = $"{_k.AnalogDeadZone:0.00}";
    }

    private void OnReset(object sender, RoutedEventArgs e)
    {
        var d = KeyBindings.Defaults();
        _k.Buttons = d.Buttons; _k.AnalogUp = d.AnalogUp; _k.AnalogDown = d.AnalogDown;
        _k.AnalogLeft = d.AnalogLeft; _k.AnalogRight = d.AnalogRight; _k.AnalogDeadZone = d.AnalogDeadZone;
        DeadZone.Value = _k.AnalogDeadZone;
        _capturing = null;
        Refresh();
    }

    private void OnSave(object sender, RoutedEventArgs e) => DialogResult = true;
    private void OnCancel(object sender, RoutedEventArgs e) => DialogResult = false;
}
