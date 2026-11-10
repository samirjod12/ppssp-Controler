namespace PSPReceiver.Models;

public enum ControllerButton
{
    Up, Down, Left, Right,
    Cross, Circle, Square, Triangle,
    L, R, Start, Select
}

public abstract record ProtocolMessage;
public sealed record HelloMessage(string Pin, string ClientName) : ProtocolMessage;
public sealed record ButtonMessage(ControllerButton Button, bool IsDown) : ProtocolMessage;
public sealed record AnalogMessage(double X, double Y) : ProtocolMessage;
public sealed record HeartbeatMessage : ProtocolMessage;
