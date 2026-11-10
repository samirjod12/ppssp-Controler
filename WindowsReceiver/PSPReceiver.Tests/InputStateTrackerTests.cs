using PSPReceiver.Input;
using PSPReceiver.Models;
using Xunit;

namespace PSPReceiver.Tests;

public class InputStateTrackerTests
{
    private sealed class FakeSink : IInputSink
    {
        public List<(ControllerButton, bool)> Calls = new();
        public bool Throw;
        public double X, Y;
        public void SetButton(ControllerButton b, bool d) { if (Throw) throw new InvalidOperationException(); Calls.Add((b, d)); }
        public void SetAnalog(double x, double y) { X = x; Y = y; }
    }

    [Fact] public void DuplicateDown_IsSuppressed()
    {
        var s = new FakeSink(); var t = new InputStateTracker(s);
        Assert.True(t.SetButton(ControllerButton.Cross, true));
        Assert.False(t.SetButton(ControllerButton.Cross, true));
        Assert.Single(s.Calls);
    }

    [Fact] public void UpWithoutDown_IsIgnored()
    {
        var s = new FakeSink(); var t = new InputStateTracker(s);
        Assert.False(t.SetButton(ControllerButton.Circle, false));
        Assert.Empty(s.Calls);
    }

    [Fact] public void ReleaseAll_ReleasesEverythingAndCentersAnalog()
    {
        var s = new FakeSink(); var t = new InputStateTracker(s);
        t.SetButton(ControllerButton.Cross, true);
        t.SetButton(ControllerButton.L, true);
        t.SetAnalog(0.5, 0.5);
        t.ReleaseAll();
        Assert.Empty(t.ActiveButtons);
        Assert.Equal(0, s.X); Assert.Equal(0, s.Y);
        Assert.Contains((ControllerButton.Cross, false), s.Calls);
        Assert.Contains((ControllerButton.L, false), s.Calls);
    }

    [Fact] public void ReleaseAll_SurvivesSinkFailure()
    {
        var s = new FakeSink(); var t = new InputStateTracker(s);
        t.SetButton(ControllerButton.Cross, true);
        s.Throw = true;
        t.ReleaseAll();
        Assert.Empty(t.ActiveButtons);
    }

    [Fact] public void SwitchSink_ReleasesOnOldSinkFirst()
    {
        var a = new FakeSink(); var b = new FakeSink(); var t = new InputStateTracker(a);
        t.SetButton(ControllerButton.Start, true);
        t.SwitchSink(b);
        Assert.Contains((ControllerButton.Start, false), a.Calls);
        Assert.Empty(b.Calls);
    }
}
