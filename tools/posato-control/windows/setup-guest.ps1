# One-time setup of the Windows golden VM for posato-control's Windows target (PLATFORM-002).
# Run elevated over SSH after `tart create --from-iso`, with C:\TartSetup holding:
#   drivers\viosock and drivers\vioserial (virtio-win w11\ARM64), tart-guest-agent.exe
# Reboot the guest afterwards, so that the agent starts in the signed-in session and UAC is off.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$src = 'C:\TartSetup'
$tools = 'C:\Tools'
New-Item -ItemType Directory -Force $tools | Out-Null

# A downloaded installer can stay locked for a while (antivirus scan); deleting it is housekeeping, not setup
function Remove-Download($path) {
  for ($i = 0; $i -lt 30 -and (Test-Path $path); $i++) {
    Remove-Item $path -Force -ErrorAction SilentlyContinue
    if (Test-Path $path) { Start-Sleep 2 }
  }
}

# Windows PowerShell does not stop on a failing native command, so each one is checked
function Assert-Exit($what) {
  if ($LASTEXITCODE -ne 0) { throw "$what failed with exit code $LASTEXITCODE" }
}

# viosock carries `tart exec` over vsock (its INF also adds the VirtioSocketWSP Winsock provider);
# vioserial carries the clipboard
foreach ($inf in "$src\drivers\viosock\viosock.inf", "$src\drivers\vioserial\vioser.inf") {
  pnputil /add-driver $inf /install | Out-Host
  # 3010: installed, takes effect after the reboot that follows this setup anyway
  if ($LASTEXITCODE -ne 0 -and $LASTEXITCODE -ne 3010) { throw "pnputil $inf failed with exit code $LASTEXITCODE" }
}
Set-Service VirtioSocketWSP -StartupType Automatic
Start-Service VirtioSocketWSP

# The guest agent runs in the signed-in user's session, so commands from `tart exec` reach the desktop
$agentDir = 'C:\Program Files\Tart'
New-Item -ItemType Directory -Force $agentDir | Out-Null
Copy-Item "$src\tart-guest-agent.exe" "$agentDir\tart-guest-agent.exe" -Force
$action = New-ScheduledTaskAction -Execute "$env:SystemRoot\System32\conhost.exe" `
  -Argument "--headless `"$agentDir\tart-guest-agent.exe`" --run-agent"
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) `
  -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1)
$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Highest
Register-ScheduledTask -TaskName 'Tart Guest Agent' -Action $action -Trigger $trigger `
  -Settings $settings -Principal $principal -Force | Out-Null

# No UAC prompts and no sleep, screen saver or display timeout in the test session
$policies = 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Policies\System'
Set-ItemProperty $policies -Name ConsentPromptBehaviorAdmin -Value 0
Set-ItemProperty $policies -Name EnableLUA -Value 0
powercfg /change standby-timeout-ac 0
powercfg /change monitor-timeout-ac 0
Set-ItemProperty 'HKCU:\Control Panel\Desktop' -Name ScreenSaveActive -Value 0

# JDK 21 for Windows on ARM, with the Java Access Bridge switched on for this user
curl.exe -sSL -o "$tools\jdk.zip" https://aka.ms/download-jdk/microsoft-jdk-21-windows-aarch64.zip
Assert-Exit 'JDK download'
Expand-Archive "$tools\jdk.zip" $tools -Force
Remove-Download "$tools\jdk.zip"
$jdk = (Get-ChildItem $tools -Directory -Filter 'jdk-21*' | Sort-Object Name)[-1].FullName
& "$jdk\bin\jabswitch.exe" -enable
Assert-Exit 'jabswitch'

# Python for Windows on ARM and the Java Access Bridge client the probe uses
curl.exe -sSL -o "$tools\python.exe" https://www.python.org/ftp/python/3.13.1/python-3.13.1-arm64.exe
Assert-Exit 'Python download'
Start-Process "$tools\python.exe" -ArgumentList '/quiet', 'InstallAllUsers=1', 'PrependPath=1', "TargetDir=$tools\Python", 'Include_test=0' -Wait
Remove-Download "$tools\python.exe"
if (-not (Test-Path "$tools\Python\python.exe")) { throw 'Python did not install' }
& "$tools\Python\python.exe" -m pip install -q java-access-bridge-wrapper==2.0.0
Assert-Exit 'pip install'

# A clone that runs for a while otherwise starts Windows Update and a full Defender scan, which starve the guest;
# posato-control also turns the update services off in each clone, for golden VMs made before this step
foreach ($service in 'wuauserv', 'UsoSvc') {
  sc.exe config $service start= disabled | Out-Null
  sc.exe stop $service | Out-Null
}
Add-MpPreference -ExclusionPath $tools, "$env:SystemRoot\Temp"
Add-MpPreference -ExclusionProcess 'powershell.exe', 'python.exe', 'java.exe', 'javaw.exe'

Write-Host 'setup-guest done'
