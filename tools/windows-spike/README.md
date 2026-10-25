# Windows verification spike

These scripts answer one question for `PLATFORM-002`: can an agent verify
Posato on Windows with no person at the machine, the way `posato-control`
verifies the Linux application in a Tart Ubuntu guest? They are spike tooling,
not part of `posato-control` and not a product build. The findings are in the
wiki under [Unattended Verification](../../docs/wiki/topics/unattended-verification.md#windows-guests-platform-002-spike).

## Prerequisites

- An Apple silicon Mac on macOS 26 or later.
- A Tart build with Windows guests. Upstream Tart supports only macOS and
  Linux guests; the spike used the unofficial fork
  [`mustafaakin/tart`](https://github.com/mustafaakin/tart), branch
  `windows-guest-extras` at `e4c623c`, reviewed and built from source:

  ```shell
  brew install lld        # rebuilds the fork's embedded UEFI drivers
  make -C Resources/Windows
  swift build -c release --product tart
  mkdir -p build/tart.app/Contents/MacOS
  cp .build/release/tart build/tart.app/Contents/MacOS/tart
  cp Resources/Info.plist build/tart.app/Contents/Info.plist
  codesign --sign - --entitlements Resources/tart-dev.entitlements --force build/tart.app
  ```

  The fork depends on private Virtualization framework settings (PMU and
  trap emulation), which a macOS update may remove.
- The Windows guest agent from
  [`mustafaakin/tart-guest-agent`](https://github.com/mustafaakin/tart-guest-agent)
  at `f15198a`:
  `GOOS=windows GOARCH=arm64 go build -o build/tart-guest-agent.exe ./cmd`.
- A Windows 11 ARM64 ISO from Microsoft. You are responsible for its
  license; the guest is not activated.
- About 40 GB of free disk for the golden VM and the temporary installation
  media.

Set the environment for both scripts:

```shell
export POSATO_TART_WINDOWS=/path/to/tart-fork/build/tart.app/Contents/MacOS/tart
export POSATO_TART_WINDOWS_AGENT=/path/to/tart-guest-agent/build/tart-guest-agent.exe
export POSATO_TART_WINDOWS_HOME=~/.tart-windows   # keeps the fork's VMs apart from other Tart VMs
```

## Create the golden VM

```shell
tools/windows-spike/create-golden.sh /path/to/Win11_Arm64.iso
```

`tart create --from-iso` installs Windows unattended in two boots. The script
then uses the image's `admin`/`admin` account over SSH, which only Tart's NAT
network reaches, to run [`guest/setup-guest.ps1`](guest/setup-guest.ps1):

- the `viosock` and `vioserial` drivers from the cached virtio-win image;
- the guest agent as a scheduled task at sign-in, so `tart exec` runs in the
  interactive session and reaches the desktop;
- UAC, sleep, and the screen saver off;
- JDK 21 for Windows on ARM with the Java Access Bridge switched on;
- Python for Windows on ARM with `java-access-bridge-wrapper`, the
  [probe](guest/jab_probe.py), and the [screenshot](guest/screenshot.ps1)
  script.

## Run the E2E check

```shell
./gradlew :desktopApp:windowsSpikeJar
tools/windows-spike/windows-e2e.sh desktopApp/build/windows-spike/posato-windows-spike.jar \
  build/verification/windows-spike-$(date +%Y%m%d-%H%M%S)
```

The script clones the golden VM, shares the jar into the guest with
`tart run --dir` (drive `Z:`), starts Posato in the signed-in session, and
drives onboarding through the Java Access Bridge: it reads the welcome step,
presses each button by its accessible name, types a website, and asserts every
following step. It saves each step's accessibility tree as JSON and a
screenshot of the Session screen, then deletes the clone. A failed assertion
exits non-zero.

`windowsSpikeJar` is the Linux application jar with the Compose runtime for
Windows on ARM. The Linux entry point runs on Windows without D-Bus and
without the pausing service, which is enough to drive the shared interface; it
does not pause anything on Windows.
