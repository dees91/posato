# Windows

Posato on Windows 11 (`PLATFORM-002`) shows the same Session, Pause sets, and
Schedules screens as Linux. During a pause a Windows service blocks
connections to the chosen websites' addresses with Windows Filtering Platform
filters and ends the chosen applications; the per-machine MSI installs the
service. Closing the window keeps Posato running with its icon in the
notification area; a second launch shows the window again.

## Sub-features

- `windows-install`: the MSI installs quietly and leaves the service
  `PosatoHelper` running; removing it leaves no service, filter, or process.
- `windows-onboarding`: first run reaches Session; the summary and sidebar
  say "computer", never "Mac".
- `windows-websites`: during a pause a TCP connection to a chosen host fails
  and Posato's filters are present; after the end both are gone.
- `windows-apps`: a chosen application's processes end during a pause.
- `windows-refusal`: the service's pipe refuses another local user and
  answers a malformed line with `error\trequest`.
- `windows-login`: Posato's `Run` value starts it hidden at sign-in.

## How to get to it (user POV)

Install the MSI, open Posato from the Start menu, and follow the first-run
flow. **Manage apps**, **Apps**, and **Choose apps** list the Start menu's
desktop programs.

## Driving it with posato-control

Preconditions: the Windows golden VM and the Tart fork from the unattended
verification guide, `posato.vm.windowsTart` set, and room in
`machine-ledger host` for a 4 GiB guest. Run `$PC doctor -t windows` first.

- **Build the arm64 MSI:** `$PC windows create`, `$PC build -t windows`
  (about 2 minutes: the package inputs on the host, jpackage and WiX in the
  clone; the MSI lands in `desktopApp/build/windows/msi/`), then `$PC windows
  destroy` so that the install runs in a fresh clone.
- **Clone and install:** `$PC windows create`, then `$PC install -t windows`
  (the last built MSI) or `$PC install -t windows --package <file>.msi`. A
  failed MSI install reports msiexec's code and keeps the end of its log under
  the run's `logs/`. Proof: `$PC windows check service --expect present
  --state Running`.
- **Onboarding:** `$PC launch -t windows --fresh`, then `$PC flow onboard -t
  windows --website example.org`. The access step only checks the service the
  MSI installed. Proof: the flow ends on Session with its screenshot.
- **Close and show again:** close the window with `$PC windows exec --script
  'Get-Process Posato | Where-Object { $_.MainWindowHandle -ne 0 } |
  ForEach-Object { $_.CloseMainWindow() }'`; the first close shows the
  "still running" notice (`$PC tap -t windows --text OK`). Starting
  `C:\Program Files\Posato\Posato.exe` again shows the same window with no
  new process. `launch` writes Posato's output to `C:\Tools\posato.log` and
  `posato-error.log`, which `logs -t windows` reads.
- **Session and early end:** `$PC flow session -t windows --minutes 25
  --end-early-after 30`. Proof while it runs: `$PC windows check connect
  --host example.org --expect blocked` and `$PC windows check filters
  --expect present`; after the end the same with `allowed` and `absent`. Use
  a fresh browser profile and a host with stable addresses when a browser
  is the proof; DNS over HTTPS in a browser and connections already open are
  outside the filters.
- **Application:** **Manage apps**, **Apps**, **Choose apps**; the Swing
  dialog is then the tree, so `$PC tap -t windows --text "7-Zip File
  Manager"` ticks a program and `$PC tap -t windows --text Choose --role
  button` closes it. Store applications (Notepad, Paint) have no Start menu
  shortcut and are never listed. Start a pause, then `$PC
  windows check process --name <image> --expect absent` after starting it
  with `$PC windows exec --script 'Start-Process <path>'`.
- **UAC on:** the golden VM has UAC off. To run as a person's computer does,
  `$PC windows exec --script 'Set-ItemProperty
  HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Policies\System -Name
  EnableLUA -Value 1; shutdown.exe /r /t 5 /f'` and wait until `windows exec`
  answers again; then `launch` and every bridge command run Posato with the
  filtered token. Proof: `$PC windows exec --limited --script 'whoami
  /groups'` lists Administrators as "Group used for deny only".
- **Foreign caller:** `$PC windows check pipe --line status --expect
  connected` as the installing user, and `$PC windows check pipe --line
  status --as-other-user --expect refused` as `posato-other`, a standard
  user that the check creates; `--line 'not a request'` must answer
  `error\trequest`.
- **Login:** `$PC windows check run-value --expect present`, then `$PC
  windows stop && $PC windows boot` and `$PC windows check process --name
  Posato --expect running`.
- **Removal:** `$PC windows uninstall -t windows`, then `service --expect absent`,
  `filters --expect absent`, and `process --name Posato --expect absent`.
- Finish with `$PC windows destroy`.

## Gotchas

- The guest agent passes only the low byte of an exit code; read codes from
  the check's JSON, not the command's exit status.
- WMI (`Get-CimInstance`) has hung in the guest; scripts given to `windows
  exec` should use `Get-Process`, `Get-Service`, and `sc.exe` instead.
- A clone left running starts Windows Update and a Defender scan; `windows
  create` turns both down, but a guest that still crawls is deleted and
  cloned again, never repaired.
- The tray icon's menu, the UAC prompt, and the Windows file dialogs are not
  drivable yet. The icon's registration shows under `HKCU:\Control
  Panel\NotifyIconSettings` (an entry whose `ExecutablePath` ends in
  `Posato\Posato.exe`), and a hidden Posato keeps running with no main window.
- jpackage's Windows launchers run the application in a child process of
  themselves, so `Posato` and `posato-helper` each show two processes.
- The fork's guest reports a second display; `screenshot` captures the
  primary one, where Posato opens.
