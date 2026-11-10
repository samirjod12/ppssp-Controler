using PSPReceiver.Input;
using PSPReceiver.Models;
using Xunit;

namespace PSPReceiver.Tests;

public class KeyboardOutputTests
{
    private sealed class FakeInjector : IKeyInjector
    {
        public readonly List<(ushort Vk, bool Down)> Events = new();
        public int FailUps;
        public bool Send(ushort vk, bool down)
        {
            if (!down && FailUps > 0) { FailUps--; return false; }
            Events.Add((vk, down));
            return true;
        }
        public int Count(ushort vk, bool down) => Events.Count(e => e.Vk == vk && e.Down == down);
    }

    private const ushort Z = 0x5A, X = 0x58, W = 0x57, D = 0x44, A = 0x41;

    private static (KeyboardOutput k, FakeInjector f) Make(KeyBindings? b = null)
    {
        var f = new FakeInjector();
        return (new KeyboardOutput(f, b ?? KeyBindings.Defaults()), f);
    }

    [Fact] public void Button_DownThenUp_SendsMappedKey()
    {
        var (k, f) = Make();
        k.SetButton(ControllerButton.Cross, true);
        k.SetButton(ControllerButton.Cross, false);
        Assert.Equal(new[] { (Z, true), (Z, false) }, f.Events);
    }

    [Fact] public void DuplicateDown_AndUpWithoutDown_AreIgnored()
    {
        var (k, f) = Make();
        k.SetButton(ControllerButton.Cross, false);
        k.SetButton(ControllerButton.Cross, true);
        k.SetButton(ControllerButton.Cross, true);
        Assert.Single(f.Events);
    }

    [Fact] public void SimultaneousButtons_AllHeld()
    {
        var (k, _) = Make();
        k.SetButton(ControllerButton.Cross, true);
        k.SetButton(ControllerButton.Circle, true);
        Assert.Contains(Z, k.HeldKeys);
        Assert.Contains(X, k.HeldKeys);
    }

    [Fact] public void SharedKey_IsReferenceCounted()
    {
        // Defaults: R = W and Analog Up = W.
        var (k, f) = Make();
        k.SetButton(ControllerButton.R, true);
        k.SetAnalog(0, 1);
        Assert.Equal(1, f.Count(W, true));          // only one real key-down

        k.SetButton(ControllerButton.R, false);
        Assert.Equal(0, f.Count(W, false));         // analog still holds W

        k.SetAnalog(0, 0);
        Assert.Equal(1, f.Count(W, false));         // now released exactly once
        Assert.Empty(k.HeldKeys);
    }

    [Fact] public void Analog_RespectsDeadZone()
    {
        var (k, f) = Make();
        k.SetAnalog(0.2, 0);                        // inside 0.30 dead zone
        Assert.Empty(f.Events);
        k.SetAnalog(0.5, 0);
        Assert.Equal(1, f.Count(D, true));
        k.SetAnalog(0, 0);
        Assert.Equal(1, f.Count(D, false));
    }

    [Fact] public void Analog_Diagonal_PressesTwoKeys()
    {
        var (k, _) = Make();
        k.SetAnalog(-0.8, 0.8);                     // left + up
        Assert.Contains(A, k.HeldKeys);
        Assert.Contains(W, k.HeldKeys);
    }

    [Fact] public void ReleaseAllKeys_ReleasesEverything()
    {
        var (k, f) = Make();
        k.SetButton(ControllerButton.Cross, true);
        k.SetAnalog(1, 0);
        k.ReleaseAllKeys();
        Assert.Empty(k.HeldKeys);
        Assert.Equal(1, f.Count(Z, false));
        Assert.Equal(1, f.Count(D, false));
    }

    [Fact] public void UpdateBindings_ReleasesHeldKeysFirst()
    {
        var (k, f) = Make();
        k.SetButton(ControllerButton.Cross, true);
        var nb = KeyBindings.Defaults();
        nb.Buttons[ControllerButton.Cross] = 0x42;  // B
        k.UpdateBindings(nb);
        Assert.Equal(1, f.Count(Z, false));
        k.SetButton(ControllerButton.Cross, false); // stale up from tracker must be harmless
        k.SetButton(ControllerButton.Cross, true);
        Assert.Equal(1, f.Count(0x42, true));
    }

    [Fact] public void KeyUp_IsRetriedOnce()
    {
        var (k, f) = Make();
        k.SetButton(ControllerButton.Cross, true);
        f.FailUps = 1;
        k.SetButton(ControllerButton.Cross, false);
        Assert.Equal(1, f.Count(Z, false));
    }

    [Fact] public void KeyUp_DoubleFailure_RaisesError()
    {
        var (k, f) = Make();
        string? err = null;
        k.Error += m => err = m;
        k.SetButton(ControllerButton.Cross, true);
        f.FailUps = 2;
        k.SetButton(ControllerButton.Cross, false);
        Assert.NotNull(err);
    }

    [Fact] public void WorksThroughInputStateTracker_ReleaseAll()
    {
        var (k, f) = Make();
        var t = new InputStateTracker(k);
        t.SetButton(ControllerButton.Triangle, true);
        t.SetAnalog(0, -1);
        t.ReleaseAll();
        Assert.Empty(k.HeldKeys);
        // Triangle (S) and analog-down (S) share one physical key by default, so exactly one key-up is sent.
        Assert.Equal(1, f.Events.Count(e => !e.Down));
    }
}
