using PSPReceiver.Models;

namespace PSPReceiver.Input;

/// <summary>Sends every event to several sinks (real output + diagnostics). One failing sink never starves the others.</summary>
public sealed class FanOutSink : IInputSink
{
    private readonly IInputSink[] _sinks;
    public FanOutSink(params IInputSink[] sinks) => _sinks = sinks;

    public void SetButton(ControllerButton button, bool isDown) => Each(s => s.SetButton(button, isDown));
    public void SetAnalog(double x, double y) => Each(s => s.SetAnalog(x, y));

    private void Each(Action<IInputSink> act)
    {
        Exception? first = null;
        foreach (var s in _sinks)
        {
            try { act(s); } catch (Exception ex) { first ??= ex; }
        }
        if (first is not null) throw first;
    }
}
