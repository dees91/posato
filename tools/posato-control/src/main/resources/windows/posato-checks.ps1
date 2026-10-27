# Guest checks for posato-control's Windows target. Reads one JSON request from -Request or standard input and prints one JSON
# object: {"check": "connect|process|service|filters|run-value|pipe|other-user", ...}. Nothing here uses WMI, whose
# provider can hang in the guest. The guest agent passes only the low byte of an exit code, so results travel as JSON.
param([string]$Request)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$query = $(if ($Request) { $Request } else { [Console]::In.ReadToEnd() }) | ConvertFrom-Json

function Out-Result($value) { $value | ConvertTo-Json -Compress -Depth 5 }

# A TCP connection with Test-NetConnection, an HTTP request with curl.exe, and what the name resolves to.
function Test-Connect($hostName, [int]$port) {
  $addresses = @(Resolve-DnsName -Name $hostName -DnsOnly -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress } | ForEach-Object { $_.IPAddress })
  $tcp = Test-NetConnection -ComputerName $hostName -Port $port -WarningAction SilentlyContinue
  $scheme = if ($port -eq 443) { 'https' } else { 'http' }
  $code = curl.exe -s -o NUL -w '%{http_code}' --max-time 15 "${scheme}://${hostName}:$port/"
  [ordered]@{ host = $hostName; port = $port; addresses = $addresses; tcp = [bool]$tcp.TcpTestSucceeded
    remoteAddress = "$($tcp.RemoteAddress)"; curlExit = $LASTEXITCODE; httpCode = "$code" }
}

function Get-Processes($name) {
  $items = @(Get-Process -Name $name -IncludeUserName -ErrorAction SilentlyContinue | ForEach-Object {
    [ordered]@{ pid = $_.Id; path = $_.Path; session = $_.SessionId; owner = $_.UserName } })
  [ordered]@{ name = $name; running = ($items.Count -gt 0); processes = $items }
}

function Get-ServiceState($name) {
  $service = Get-Service -Name $name -ErrorAction SilentlyContinue
  if (-not $service) { return [ordered]@{ name = $name; present = $false; state = 'Absent' } }
  [ordered]@{ name = $service.Name; present = $true; state = "$($service.Status)"; startMode = "$($service.StartType)"
    config = (sc.exe qc $service.Name 5000) -join "`n" }
}

# Filters whose name or description matches, or whose provider or sublayer name matches, from netsh wfp.
function Get-Filters($match) {
  $dir = 'C:\Windows\Temp\posato-wfp'
  New-Item -ItemType Directory -Force $dir | Out-Null
  netsh wfp show filters file="$dir\filters.xml" | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "netsh wfp show filters failed with $LASTEXITCODE" }
  netsh wfp show state file="$dir\state.xml" | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "netsh wfp show state failed with $LASTEXITCODE" }
  [xml]$filters = Get-Content -Raw "$dir\filters.xml"
  # netsh appends a <firewallState> element after </wfpstate>, which is not part of the document
  $stateText = Get-Content -Raw "$dir\state.xml"
  $end = $stateText.IndexOf('</wfpstate>')
  [xml]$state = $(if ($end -ge 0) { $stateText.Substring(0, $end + '</wfpstate>'.Length) } else { $stateText })
  $pattern = "*$match*"
  $providers = @($state.SelectNodes('//providers/item') | Where-Object { $_.displayData.name -like $pattern } |
    ForEach-Object { $_.providerKey })
  $subLayers = @($state.SelectNodes('//subLayers/item') | Where-Object { $_.displayData.name -like $pattern } |
    ForEach-Object { $_.subLayerKey })
  $items = @($filters.SelectNodes('//filters/item') | Where-Object {
      $_.displayData.name -like $pattern -or $_.displayData.description -like $pattern -or
      $providers -contains $_.providerKey -or $subLayers -contains $_.subLayerKey } |
    ForEach-Object { [ordered]@{ id = $_.filterId; name = $_.displayData.name; layer = $_.layerKey; action = $_.action.type } })
  [ordered]@{ match = $match; count = $items.Count; present = ($items.Count -gt 0); providers = $providers
    subLayers = $subLayers; filters = $items }
}

function Get-RunValue($name) {
  $value = (Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run' -Name $name -ErrorAction SilentlyContinue).$name
  [ordered]@{ name = $name; present = ($null -ne $value); value = $value }
}

# Connects to the named pipe, sends one line when given, and reads one answer line; a refusal is a result, not a failure.
function Invoke-Pipe($pipe, $line) {
  $result = [ordered]@{ pipe = $pipe; user = "$env:USERDOMAIN\$env:USERNAME"; connected = $false }
  try {
    $client = New-Object System.IO.Pipes.NamedPipeClientStream('.', $pipe, [System.IO.Pipes.PipeDirection]::InOut)
    $client.Connect(3000)
    $result.connected = $true
    if ($line) {
      $writer = New-Object System.IO.StreamWriter($client)
      $writer.AutoFlush = $true
      $writer.Write($line + "`n")
      $result.reply = (New-Object System.IO.StreamReader($client)).ReadLine()
    }
    $client.Dispose()
  } catch { $result.error = $_.Exception.GetType().Name + ': ' + $_.Exception.Message }
  $result
}

# Runs a script as a standard local user through a one-time scheduled task. The user exists only in the disposable
# clone and gets the batch logon right a scheduled task needs; the password arrives in the request, never in a script.
function Invoke-AsOtherUser($name, $password, $script, [int]$timeoutSeconds) {
  $secure = ConvertTo-SecureString $password -AsPlainText -Force
  if (-not (Get-LocalUser -Name $name -ErrorAction SilentlyContinue)) {
    New-LocalUser -Name $name -Password $secure -PasswordNeverExpires -AccountNeverExpires -Description 'posato-control foreign caller' | Out-Null
    Add-LocalGroupMember -Group (Get-LocalGroup -SID 'S-1-5-32-545') -Member $name
  } else { Set-LocalUser -Name $name -Password $secure }
  $dir = 'C:\Users\Public\posato-other'
  New-Item -ItemType Directory -Force $dir | Out-Null
  icacls $dir /grant "${name}:(OI)(CI)M" | Out-Null
  $sid = (Get-LocalUser -Name $name).SID.Value
  secedit /export /cfg "$dir\rights.inf" /areas USER_RIGHTS | Out-Null
  $rights = Get-Content "$dir\rights.inf"
  $batch = $rights | Where-Object { $_ -like 'SeBatchLogonRight*' }
  if ($batch -notlike "*$sid*") {
    if ($batch) { $rights = $rights -replace '^(SeBatchLogonRight = .*)$', "`$1,*$sid" }
    else { $rights = $rights -replace '^\[Privilege Rights\]$', "[Privilege Rights]`r`nSeBatchLogonRight = *$sid" }
    Set-Content "$dir\rights.inf" $rights -Encoding Unicode
    secedit /configure /db "$dir\rights.sdb" /cfg "$dir\rights.inf" /areas USER_RIGHTS | Out-Null
  }
  Remove-Item "$dir\out.txt", "$dir\exit.txt", "$dir\rights.*" -ErrorAction SilentlyContinue
  [IO.File]::WriteAllText("$dir\task.ps1", $script, [Text.Encoding]::UTF8)
  $command = "& { & '$dir\task.ps1' *> '$dir\out.txt'; Set-Content -Path '$dir\exit.txt' -Value ([int](-not `$?) -bor [int]`$LASTEXITCODE) }"
  $action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -NonInteractive -ExecutionPolicy Bypass -Command $command"
  $task = 'posato-control other user'
  Register-ScheduledTask -TaskName $task -Action $action -User $name -Password $password -RunLevel Limited -Force | Out-Null
  Start-ScheduledTask -TaskName $task
  $deadline = (Get-Date).AddSeconds($timeoutSeconds)
  while (-not (Test-Path "$dir\exit.txt") -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 500 }
  $last = (Get-ScheduledTaskInfo -TaskName $task).LastTaskResult
  Unregister-ScheduledTask -TaskName $task -Confirm:$false
  if (-not (Test-Path "$dir\exit.txt")) { return [ordered]@{ user = $name; finished = $false; taskResult = $last } }
  # .NET reads plain strings; Get-Content adds note properties that ConvertTo-Json would print
  $output = if (Test-Path "$dir\out.txt") { [IO.File]::ReadAllText("$dir\out.txt") } else { '' }
  [ordered]@{ user = $name; finished = $true; exitCode = [int][IO.File]::ReadAllText("$dir\exit.txt").Trim(); output = $output }
}

switch ($query.check) {
  'connect' { Out-Result (Test-Connect $query.host $query.port) }
  'process' { Out-Result (Get-Processes $query.name) }
  'service' { Out-Result (Get-ServiceState $query.name) }
  'filters' { Out-Result (Get-Filters $query.match) }
  'run-value' { Out-Result (Get-RunValue $query.name) }
  'pipe' { Out-Result (Invoke-Pipe $query.pipe $query.line) }
  'other-user' { Out-Result (Invoke-AsOtherUser $query.user $query.password $query.script $query.timeoutSeconds) }
  default { throw "unknown check $($query.check)" }
}
