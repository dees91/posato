# Captures the primary screen of the interactive desktop to the given PNG path; -AllScreens captures every display
# (the Tart fork's guest reports a second display beside the primary one).
param([string]$Path = 'C:\Windows\Temp\posato-screen.png', [switch]$AllScreens)
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
Add-Type @'
using System.Runtime.InteropServices;
public static class Dpi { [DllImport("user32.dll")] public static extern bool SetProcessDPIAware(); }
'@
[Dpi]::SetProcessDPIAware() | Out-Null
$b = if ($AllScreens) { [System.Windows.Forms.SystemInformation]::VirtualScreen } else { [System.Windows.Forms.Screen]::PrimaryScreen.Bounds }
$bmp = New-Object System.Drawing.Bitmap $b.Width, $b.Height
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($b.Left, $b.Top, 0, 0, $bmp.Size)
$bmp.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "$Path $($b.Width)x$($b.Height)"
