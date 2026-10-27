# Windows

Posato on Windows 11 (`PLATFORM-002`) shows the same Session, Pause sets, and
Schedules screens as Linux. During a pause a Windows service blocks
connections to the chosen websites' addresses with Windows Filtering Platform
filters and ends the chosen applications; the per-machine MSI installs the
service. Until that package exists, the driver runs the `windowsSpikeJar`
build, the Linux application jar with the Windows Compose runtime, which
drives the shared interface but pauses nothing.

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

- **Clone and install:** `$PC windows create`, then either `$PC build -t
  windows && $PC install -t windows` (spike jar) or `$PC install -t windows
  --package <file>.msi`. A failed MSI install reports msiexec's code and keeps
  the end of its log under the run's `logs/`. Proof for the MSI: `$PC
  windows check service --expect present --state Running`.
- **Onboarding:** `$PC launch -t windows --fresh`, then `$PC flow onboard -t
  windows --website example.org` (with the spike jar add `--skip-access`; it
  has no service). Proof: the flow ends on Session with its screenshot;
  `onboarding-deferred-access-linux.json` asserts the summary and sidebar
  nouns for the spike jar.
- **Session and early end:** `$PC flow session -t windows --minutes 25
  --end-early-after 30`. Proof while it runs: `$PC windows check connect
  --host example.org --expect blocked` and `$PC windows check filters
  --expect present`; after the end the same with `allowed` and `absent`. Use
  a fresh browser profile and a host with stable addresses when a browser
  is the proof; DNS over HTTPS in a browser and connections already open are
  outside the filters.
- **Application:** choose it in **Choose apps**, start a pause, then `$PC
  windows check process --name <image> --expect absent` after starting it
  with `$PC windows exec --script 'Start-Process <path>'`.
- **Foreign caller:** `$PC windows check pipe --line status --expect
  connected` as the installing user, and `$PC windows check pipe --line
  status --as-other-user --expect refused` as `posato-other`, a standard
  user that the check creates; `--line 'not a request'` must answer
  `error\trequest`.
- **Login:** `$PC windows check run-value --expect present`, then `$PC
  windows stop && $PC windows boot` and `$PC windows check process --name
  Posato --expect running`.
- **Removal:** `$PC windows uninstall`, then `service --expect absent`,
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
  drivable yet.
- The fork's guest reports a second display; `screenshot` captures the
  primary one, where Posato opens.
