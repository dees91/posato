# Runs a command line as the signed-in user with the filtered token that UAC gives a standard session, through a
# one-time interactive scheduled task, for a clone with UAC on. The guest agent itself runs elevated, and an elevated
# process can neither show what a person's Posato does nor read its window through the Java Access Bridge (UIPI drops
# the bridge's messages from the lower integrity level).
#
# Usage: posato-limited.ps1 -CommandLine <cmd.exe line> [-HasInput] [-NoWait] [-TimeoutSeconds 120]
#
# -HasInput passes this script's standard input to the command; -NoWait starts the command and returns, as a launch
# does, and leaves its output to the command's own redirections. Otherwise the command's output and exit code are this
# script's. No WMI query runs here; the ScheduledTasks cmdlets are the ones the other-user check already uses.
param(
    [Parameter(Mandatory = $true)][string]$CommandLine,
    [switch]$HasInput,
    [switch]$NoWait,
    [int]$TimeoutSeconds = 120
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$id = [Guid]::NewGuid().ToString('N')
# The user's own temporary folder, which both the elevated agent and the filtered task can write.
$base = Join-Path $env:TEMP "posato-limited-$id"
$redirect = ''
if ($HasInput) {
    $file = [IO.File]::Create("$base.in")
    try { [Console]::OpenStandardInput().CopyTo($file) } finally { $file.Close() }
    $redirect = " < `"$base.in`""
}
$line = if ($NoWait) {
    "/s /c `"$CommandLine$redirect`""
} else {
    "/v:on /s /c `"$CommandLine$redirect > `"$base.out`" 2> `"$base.err`" & echo !ERRORLEVEL! > `"$base.code`"`""
}

# Tasks of earlier calls that have finished are removed; a launch's task stays while its Posato runs.
Get-ScheduledTask -TaskName 'posato-control limited *' -ErrorAction SilentlyContinue |
    Where-Object { $_.State -ne 'Running' } | Unregister-ScheduledTask -Confirm:$false -ErrorAction SilentlyContinue

$task = "posato-control limited $id"
# A writable working folder: the bridge wrapper writes its log there, and a task otherwise starts in System32.
$action = New-ScheduledTaskAction -Execute "$env:WINDIR\System32\cmd.exe" -Argument $line -WorkingDirectory $env:TEMP
$principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName $task -Action $action -Principal $principal -Settings $settings -Force | Out-Null
Start-ScheduledTask -TaskName $task
if ($NoWait) { exit 0 }

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while (-not (Test-Path "$base.code") -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 200 }
# The exit code is written last; a moment lets cmd close it.
Start-Sleep -Milliseconds 100
$finished = Test-Path "$base.code"
if (-not $finished) { Stop-ScheduledTask -TaskName $task -ErrorAction SilentlyContinue }
Unregister-ScheduledTask -TaskName $task -Confirm:$false -ErrorAction SilentlyContinue
# A process the command started can still hold the output files open, so they are read with sharing.
function Read-Shared([string]$path) {
    if (-not (Test-Path $path)) { return '' }
    $stream = [IO.File]::Open($path, 'Open', 'Read', 'ReadWrite, Delete')
    try { return (New-Object IO.StreamReader($stream)).ReadToEnd() } finally { $stream.Close() }
}
$out = Read-Shared "$base.out"
$err = Read-Shared "$base.err"
$code = if ($finished) { [int]([IO.File]::ReadAllText("$base.code").Trim()) } else { 124 }
Remove-Item "$base.*" -Force -ErrorAction SilentlyContinue
[Console]::Out.Write($out)
if (-not $finished) { $err += "posato-limited: the command did not finish within $TimeoutSeconds s." }
[Console]::Error.Write($err)
exit $code
