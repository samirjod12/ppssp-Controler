using PSPReceiver.Models;
using PSPReceiver.Security;
using Xunit;

namespace PSPReceiver.Tests;

public class MessageValidatorTests
{
    [Fact] public void ButtonDown_Parses()
    {
        var r = MessageValidator.Parse("""{"type":"button","version":1,"button":"CROSS","state":"down"}""");
        Assert.Equal(new ButtonMessage(ControllerButton.Cross, true), r.Message);
    }

    [Fact] public void Analog_IsClamped()
    {
        var r = MessageValidator.Parse("""{"type":"analog","version":1,"x":5,"y":-9}""");
        Assert.Equal(new AnalogMessage(1.0, -1.0), r.Message);
    }

    [Theory]
    [InlineData("not json")]
    [InlineData("[]")]
    [InlineData("""{"type":"button","version":1,"button":"HOME","state":"down"}""")]
    [InlineData("""{"type":"button","version":1,"button":"CROSS","state":"held"}""")]
    [InlineData("""{"type":"launch","version":1}""")]
    [InlineData("""{"type":"analog","version":1,"x":"1","y":0}""")]
    [InlineData("""{"type":"hello","version":1,"pin":"12ab56","clientName":"x"}""")]
    [InlineData("""{"type":"heartbeat"}""")]
    public void Malformed_IsRejected(string json) => Assert.False(MessageValidator.Parse(json).IsValid);

    [Fact] public void WrongVersion_ReportsUnsupported() =>
        Assert.Equal("unsupported_version", MessageValidator.Parse("""{"type":"heartbeat","version":2}""").ErrorCode);

    [Fact] public void Oversized_IsRejected() =>
        Assert.False(MessageValidator.Parse(new string(' ', 2000) + """{"type":"heartbeat","version":1}""").IsValid);

    [Fact] public void Hello_Valid() =>
        Assert.True(MessageValidator.Parse("""{"type":"hello","version":1,"pin":"483920","clientName":"Pixel"}""").IsValid);
}
