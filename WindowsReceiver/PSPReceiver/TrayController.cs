namespace PSPReceiver;

public sealed class TrayController : IDisposable
{
    private readonly System.Windows.Forms.NotifyIcon _icon;

    public TrayController(Action open, Action exit)
    {
        var menu = new System.Windows.Forms.ContextMenuStrip();
        menu.Items.Add("Open PSP Receiver", null, (_, _) => open());
        menu.Items.Add("Exit", null, (_, _) => exit());
        _icon = new System.Windows.Forms.NotifyIcon
        {
            Icon = System.Drawing.SystemIcons.Application,
            Text = "PSP Receiver",
            ContextMenuStrip = menu,
            Visible = false
        };
        _icon.DoubleClick += (_, _) => open();
    }

    public bool Visible { get => _icon.Visible; set => _icon.Visible = value; }

    public void Dispose()
    {
        _icon.Visible = false;   // otherwise a ghost icon lingers until the mouse passes over it
        _icon.Dispose();
    }
}
