# Packages build/windows/package (the jar from windowsAppJar, its icon and version, and these files) as a per-machine
# MSI on a Windows machine of the target architecture: a Tart Windows guest for arm64, a GitHub Actions runner for x64.
#
# Usage: package-msi.ps1 -Output <directory> [-JavaHome <JDK 21>] [-Wix <folder with candle.exe and light.exe>]
#
# jpackage needs WiX 3. Without -Wix, candle.exe on PATH, or WIX, the pinned WiX 3.14.1 binaries are downloaded once
# into %LOCALAPPDATA%\posato-build and checked against their SHA-256. When POSATO_WINDOWS_SIGNING names a code-signing
# certificate (its subject name, or its SHA-1 thumbprint) in the current user's or the machine's store, the launchers
# and the MSI are signed with signtool and timestamped at POSATO_WINDOWS_TIMESTAMP (default DigiCert's server);
# otherwise the MSI is unsigned.
param(
    [Parameter(Mandatory = $true)][string]$Output,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Wix = ''
)
$ErrorActionPreference = 'Stop'
$inputs = $PSScriptRoot

# The service's installer steps and the upgrade identity never change between versions.
$upgradeCode = '5BBD64CA-6B87-429A-90A8-0F4FA147E299'
$wixUrl = 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip'
$wixSha256 = '6ac824e1642d6f7277d0ed7ea09411a508f6116ba6fae0aa5f2c7daa2ff43d31'
$modules = 'java.base,java.desktop,java.sql,java.naming,java.logging,java.management,jdk.net,jdk.unsupported,' +
    'jdk.accessibility,java.net.http,jdk.crypto.ec,java.xml,jdk.security.auth'

function Assert-Exit([string]$step) {
    if ($LASTEXITCODE -ne 0) { throw "$step failed with exit code $LASTEXITCODE" }
}

function Find-Jdk {
    if ($JavaHome -and (Test-Path (Join-Path $JavaHome 'bin\jpackage.exe'))) { return $JavaHome }
    $onPath = Get-Command jpackage.exe -ErrorAction SilentlyContinue
    if ($onPath) { return Split-Path (Split-Path $onPath.Source) }
    throw 'No JDK 21 with jpackage: pass -JavaHome or set JAVA_HOME.'
}

function Use-Wix {
    if ($Wix) { $env:PATH = "$Wix;$env:PATH"; return }
    if (Get-Command candle.exe -ErrorAction SilentlyContinue) { return }
    if ($env:WIX -and (Test-Path (Join-Path $env:WIX 'bin\candle.exe'))) { $env:PATH = "$(Join-Path $env:WIX 'bin');$env:PATH"; return }
    $cache = Join-Path $env:LOCALAPPDATA 'posato-build\wix314'
    if (-not (Test-Path (Join-Path $cache 'candle.exe'))) {
        New-Item -ItemType Directory -Force $cache | Out-Null
        $zip = Join-Path $cache 'wix314-binaries.zip'
        curl.exe -sSfL --retry 3 -o $zip $wixUrl
        Assert-Exit 'WiX download'
        if ((Get-FileHash -Algorithm SHA256 $zip).Hash.ToLowerInvariant() -ne $wixSha256) {
            Remove-Item -Force $zip
            throw 'The WiX download does not match its pinned SHA-256.'
        }
        Expand-Archive $zip $cache -Force
        Remove-Item -Force $zip
    }
    $env:PATH = "$cache;$env:PATH"
}

function Find-SignTool {
    $onPath = Get-Command signtool.exe -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    $kits = Join-Path ${env:ProgramFiles(x86)} 'Windows Kits\10\bin'
    $found = Get-ChildItem $kits -Recurse -Filter signtool.exe -ErrorAction SilentlyContinue |
        Where-Object { $_.Directory.Name -eq 'x64' } | Sort-Object FullName | Select-Object -Last 1
    if (-not $found) { throw 'POSATO_WINDOWS_SIGNING is set, but signtool.exe was not found.' }
    return $found.FullName
}

function Invoke-Sign([string[]]$files) {
    $identity = $env:POSATO_WINDOWS_SIGNING
    if (-not $identity) { return }
    $timestamp = if ($env:POSATO_WINDOWS_TIMESTAMP) { $env:POSATO_WINDOWS_TIMESTAMP } else { 'http://timestamp.digicert.com' }
    $select = if ($identity -match '^[0-9A-Fa-f]{40}$') { @('/sha1', $identity) } else { @('/n', $identity) }
    & (Find-SignTool) sign @select /fd sha256 /tr $timestamp /td sha256 @files
    Assert-Exit 'Signing'
}

$jdk = Find-Jdk
$jpackage = Join-Path $jdk 'bin\jpackage.exe'
$java = Join-Path $jdk 'bin\java.exe'
# Windows PowerShell turns a native command's redirected standard error into errors, which Stop would raise.
$ErrorActionPreference = 'Continue'
$properties = & $java -XshowSettings:properties -version 2>&1 | Out-String
$ErrorActionPreference = 'Stop'
$arch = switch -Regex ($properties) {
    'os\.arch = aarch64' { 'arm64'; break }
    'os\.arch = amd64' { 'x64'; break }
    default { throw 'The JDK is neither Windows on ARM64 nor x64.' }
}
$jnaFolder = @{ arm64 = 'win32-aarch64'; x64 = 'win32-x86-64' }[$arch]
$version = (Get-Content (Join-Path $inputs 'version.txt') -Raw).Trim()
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw "Unexpected version '$version'." }
Use-Wix

$work = Join-Path ([IO.Path]::GetTempPath()) ("posato-msi-" + [Guid]::NewGuid())
$appInput = Join-Path $work 'input'
$resources = Join-Path $work 'resources'
$image = Join-Path $work 'image'
New-Item -ItemType Directory -Force $appInput, $resources, $image, $Output | Out-Null
try {
    # The application folder holds the jar, the notices, and JNA's native library for this architecture, which both
    # launchers load from there instead of unpacking it into a temporary folder.
    Copy-Item (Join-Path $inputs 'posato.jar'), (Join-Path $inputs 'LICENSE'), (Join-Path $inputs 'NOTICE'),
        (Join-Path $inputs 'THIRD_PARTY_NOTICES.md') $appInput
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $jar = [IO.Compression.ZipFile]::OpenRead((Join-Path $appInput 'posato.jar'))
    try {
        $entry = $jar.GetEntry("com/sun/jna/$jnaFolder/jnidispatch.dll")
        if (-not $entry) { throw "posato.jar has no JNA library for $jnaFolder." }
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $appInput 'jnidispatch.dll'))
    } finally {
        $jar.Dispose()
    }
    Copy-Item (Join-Path $inputs 'main.wxs') $resources
    # The service's launcher: no console, no shortcut, no performance data file in the system's temporary folder.
    $helper = Join-Path $work 'posato-helper.properties'
    @(
        'main-class=app.posato.windows.helper.MainKt',
        'java-options=-Djna.nounpack=true -Djna.boot.library.path=$APPDIR -XX:-UsePerfData -XX:+DisableAttachMechanism',
        'win-console=false',
        'win-menu=false',
        'win-shortcut=false'
    ) | Set-Content -Encoding ascii $helper

    & $jpackage --type app-image --name Posato --app-version $version --vendor Posato `
        --description 'Pause the websites and apps you choose.' --icon (Join-Path $inputs 'posato.ico') `
        --input $appInput --main-jar posato.jar --main-class app.posato.desktop.windows.WindowsMainKt `
        --java-options '-XX:+DisableAttachMechanism' --java-options '-Djna.nounpack=true' `
        --java-options '-Djna.boot.library.path=$APPDIR' `
        --jlink-options '--strip-debug --no-man-pages --no-header-files' --add-modules $modules `
        --add-launcher "posato-helper=$helper" --dest $image
    Assert-Exit 'jpackage app-image'
    $app = Join-Path $image 'Posato'
    Invoke-Sign @((Join-Path $app 'Posato.exe'), (Join-Path $app 'posato-helper.exe'))

    & $jpackage --type msi --app-image $app --name Posato --app-version $version --vendor Posato `
        --description 'Pause the websites and apps you choose.' --icon (Join-Path $inputs 'posato.ico') `
        --install-dir Posato --win-menu --win-menu-group Posato --win-upgrade-uuid $upgradeCode `
        --resource-dir $resources --dest $work
    Assert-Exit 'jpackage msi'
    $msi = Join-Path $Output "Posato-$version-$arch.msi"
    Move-Item -Force (Join-Path $work "Posato-$version.msi") $msi
    Invoke-Sign @($msi)
    Write-Output $msi
} finally {
    Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
}
