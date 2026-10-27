# Unattended Verification Environment

Every task verifies its change in this environment; attended verification is
an exception that needs a named extraordinary reason (`AGENTS.md`,
Application verification). The macOS application never runs on the host Mac:
the host builds the package and `posato-control` refuses to drive it there. This guide sets up the environment in which an
agent verifies a Posato change with no person at the Mac or the phone: the macOS app inside Tart virtual
machines and the iOS app on a dedicated test iPhone, both signed in to a
dedicated test Apple Account, the Linux app in a Tart Ubuntu VM, the Windows
app in a Tart Windows VM, and the
Android app on an emulator. After the one-time setup below, the
`posato-control` driver answers every system dialog itself. Background,
measurements, and platform limits are in the
[unattended verification topic](../wiki/topics/unattended-verification.md).

Nothing in this guide is tracked: VM names, Keychain item names, and device
choices live in the ignored `local.properties`; passwords live only in the
login Keychain.

## What you need

- An Apple silicon Mac on macOS 15 or later with about 80 GB free disk, and
  at least 20 GB still free once the golden VMs exist: `doctor` reports it as
  `host.diskSpace`, and `vm create` and every command inside a running guest
  refuse below it. A clone created with `--allow-low-disk` keeps working down
  to a hard floor of 5 GB. Purgeable space, such as Time Machine local
  snapshots, does not count as free.
  On a host with `machine-ledger`, `vm create` and `linux create` also claim
  a 5 GB lease for the clone, with the guest's memory, and stop when the
  ledger refuses it below its own disk reserve or beyond its memory budget.
- An Apple Developer Program membership, and the App Store Connect team key
  described in [Apple development provisioning](apple-provisioning.md).
- A dedicated test Apple Account with two-factor authentication. Use an
  address you control; the test iPhone becomes its trusted device.
- A dedicated test iPhone on a cable, in English, with a passcode, Developer
  Mode on, and preferably no Face ID enrolled (the driver also handles two
  failed Face ID attempts).

Store the three secrets once in the login Keychain. Each command prompts for
the value without echoing it:

```shell
security add-generic-password -s <vm-admin-item> -a <guest-user> -w
security add-generic-password -s <apple-account-item> -a <test-account-email> -w
security add-generic-password -s <iphone-passcode-item> -a <any-label> -w
```

Optionally store the test account's trusted phone number, in international
format, for iCloud re-verification:
`security add-generic-password -s <apple-account-phone-item> -a <test-account-email> -w`.

## Tart and the golden VMs

1. Install Tart: `brew install openai/tools/tart`. If Homebrew refuses the
   `softnet` dependency from an untrusted tap, trust only that formula with
   `brew trust --formula openai/tools/softnet`.
2. Create each golden VM from an IPSW on this Mac; iCloud does not work in a
   downloaded image or in an upgraded older VM:

   ```shell
   tart create <primary-golden> --from-ipsw latest --disk-size 60
   tart set <primary-golden> --cpu 4 --memory 8192 --display 1440x900
   tart run <primary-golden>
   ```

3. In Setup Assistant choose English (US), create an administrator account
   whose password is the `<vm-admin-item>` secret, skip the Apple Account for
   now, and decline analytics, Siri, Location Services, Screen Time, and
   FileVault. Choose the Light appearance and download-only updates.
4. In the guest's Terminal, install `tart-guest-agent` from its GitHub
   release, verify the checksum, copy it to `/usr/local/bin`, and load the
   global LaunchAgent from the Cirrus Labs image templates with `--run-agent`.
   From then on the host runs guest commands with `tart exec`.
5. Turn on automatic login:
   `sudo sysadminctl -autologin set -userName <guest-user> -password -`.
   Turn off sleep and automatic updates, then shut the guest down from inside
   (`sudo shutdown -h now`); `tart stop` can lose its last writes.
6. Sign the golden VM in to the test Apple Account in System Settings and
   approve the first two-factor code on the test iPhone. Join iCloud Keychain
   with the guest password when asked.
7. Grant Accessibility and Screen Recording to `tart-guest-agent`: run
   `posato-control doctor -t desktop --request-permissions` in the guest once,
   then switch both rows on in System Settings.
8. Install Google Chrome in the primary golden VM, for the pause page in a
   second browser: download
   `https://dl.google.com/chrome/mac/universal/stable/GGRO/googlechrome.dmg`
   in the guest with `curl`, copy the app to `/Applications`, open it once,
   choose a search engine, and decline the default-browser and sign-in
   offers, so nothing covers the first page of a run. To add it to an existing
   golden VM, boot it under the clone's name (`tart rename`, `vm boot`) as
   for an iCloud repair.
9. Clean the golden VM before shutting it down: quit System Settings, Chrome,
   and Terminal so they do not reopen at login.

Create the peer golden VM the same way, from its own IPSW or as a clone of
the primary golden VM taken before step 6, and give it its own sign-in and
privacy approvals.

The `legacy` line holds a golden VM on the previous macOS major version for
platform checks. Apple publishes no restore image (IPSW) for some point
releases, so use the newest `UniversalMac_<version>_Restore.ipsw` for that
version from Apple's update server. The driver can prepare it without a person:

1. `tart create posato-run-legacy --from-ipsw <ipsw-url> --disk-size 60`, then
   `tart set` as above. The clone's name lets `posato-control vm` address it.
2. `$PC vm boot --line legacy` boots it headless. Walk Setup Assistant with
   `vm click`, `vm press`, and `vm type` (`--secret admin` for the guest
   administrator password). Check checkbox states on a `vm screenshot`.
3. Serve the checksum-verified agent and a small setup script from the host
   on the Tart bridge address, and run the script in the guest's Terminal
   with `sudo`. The script performs step 4 (install the agent in
   `/usr/local/bin` and load its LaunchAgent) and turns off sleep and
   automatic update checks. It also installs Rosetta
   (`softwareupdate --install-rosetta --agree-to-license`), which the x86-64
   sync check needs; an existing golden VM gets it by booting under the
   clone's name and running that command with `sudo`. After that, continue with `tart exec` as in
   steps 5-8.
4. For the two-factor code, answer the sign-in alert on the test iPhone with a
   `-t device` scenario scoped to `springboard`, read the code from its
   screenshot, and type it with `vm type`.
5. `$PC vm shutdown --line legacy`, then
   `tart rename posato-run-legacy <legacy-golden>`.

A new device signing in can make the other golden VMs report "Some iCloud
Data Isn't Syncing". iCloud Keychain items, including the Posato workspace key,
then stop reaching them. `vm create` reports the state as `iCloudKeychain`
(`syncing`, `paused`, `signed-out`, `needs-attention`, or `unknown`), and
`vm icloud --line <line>` checks it and exits 3 unless it syncs or cannot be
read. `vm icloud --line <line> --resume` runs Resume Data Sync and answers the
account, Mac password, and passcode dialogs. Repair
the golden VM itself, not only a clone: boot it under the clone's name
(`tart rename`, `vm boot`), run `vm icloud --resume`, `vm shutdown`, and
rename it back. If Apple asks for the trusted phone number, type it with
`vm type --secret phone` from the optional phone item above.

When the test account's session in a golden VM expires, every clone raises
"This Mac can't connect to iCloud because of a problem with" the account.
`vm onboard` answers it with Later, reports `goldenAccountNeedsAttention`,
and marks the clone, so `vm icloud` and `flow icloud link` in that clone
refuse with `ICLOUD_KEYCHAIN_PAUSED` and the state `needs-attention`; they
also refuse while the alert shows over System Settings. A new clone starts
unmarked. Renew the account in that golden VM (`observed` 2026-10-08): boot
it under the clone's name as above, open Apple Account settings from the
alert with `vm press --line <line> return`, answer
`vm prompt account-password --line <line>`, and if Apple asks for a
two-factor code, type it with `vm type --line <line> --text <code>`. The
code goes to the account's trusted phone number, so the maintainer reads it
out; this is the only step that needs them. Quit System Settings and any
other open application, then `vm shutdown` and rename it back.

The `ventura` line holds a macOS 13 golden VM that runs the x86-64 build under
Rosetta. A guest older than macOS 15 cannot sign in to an Apple Account, so
this line has none, and it runs only notarized candidates.

1. Create it from the newest Ventura restore image for virtual Macs (13.6,
   `UniversalMac_13.6_22G120_Restore.ipsw`), as `posato-run-ventura`, and
   set `--display 1440x900px`: macOS 13 renders a Tart display at 1x, and
   the pixel size keeps text readable and the System Settings layout the
   same as on the other lines.
2. Walk Setup Assistant as for the legacy line, and skip the Apple Account.
   Where a text click does not register, use `vm press` (Tab, Space,
   Return).
3. Run the setup script of the legacy line, with
   `softwareupdate --install-rosetta --agree-to-license` added. Set the time
   zone and automatic login through `tart exec` and `sudo -S`.
4. Grant Screen Recording (run `screencapture` through `tart exec`, then
   `vm prompt toggle --row tart-guest-agent`) and Accessibility (call
   `AXIsProcessTrustedWithOptions` with the prompt option through
   `tart exec osascript -l JavaScript`, then toggle the same row).
5. Quit System Settings and Terminal, `vm shutdown --line ventura`, and
   `tart rename posato-run-ventura <ventura-golden>`; set
   `posato.vm.venturaGolden` to that name.

## The Linux golden VM and the Android emulator

Linux and Android need no Apple Account and no signing. The Linux guest is a
Tart Ubuntu VM with an X11 session that logs in by itself; the Android target
is an emulator of your own.

1. Clone Cirrus Labs' Ubuntu image, size it, and boot it:

   ```shell
   tart clone ghcr.io/cirruslabs/ubuntu:latest <linux-golden>
   tart set <linux-golden> --memory 4096 --cpu 4 --disk-size 30 --display 1280x900
   tart run <linux-golden> --no-graphics &
   ```

2. Install the session, the packaging tools, and the input and capture tools
   through `tart exec <linux-golden> /bin/sh -c '...'`:

   ```shell
   sudo -n apt-get update
   sudo -n DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
     xorg lightdm lightdm-gtk-greeter openbox openjdk-21-jdk fakeroot dpkg-dev binutils \
     xdotool imagemagick x11-utils dbus-x11 policykit-1 pkexec sqlite3 curl dnsutils \
     fonts-dejavu-core gnome-calculator at-spi2-core libatk-wrapper-java \
     libatk-wrapper-java-jni python3-pyatspi
   ```

   The last four publish Posato's accessibility tree on AT-SPI, which
   `posato-control -t linux` reads; `launch -t linux` turns it on for the
   process it starts.

3. Log the guest user in automatically to an Openbox session, and let that
   user approve `pkexec` so the pausing service installs without a password
   (as root; `<guest-user>` is the image's user):

   ```shell
   mkdir -p /etc/lightdm/lightdm.conf.d /etc/polkit-1/rules.d
   printf '[Seat:*]\nautologin-user=<guest-user>\nautologin-user-timeout=0\nuser-session=openbox\nautologin-session=openbox\n' \
     > /etc/lightdm/lightdm.conf.d/50-posato-autologin.conf
   groupadd -f autologin && usermod -aG autologin <guest-user>
   printf 'polkit.addRule(function(action, subject) { if (subject.user == "<guest-user>" && action.id == "org.freedesktop.policykit.exec") { return polkit.Result.YES; } });\n' \
     > /etc/polkit-1/rules.d/49-posato-verification.rules
   systemctl set-default graphical.target && systemctl enable lightdm
   ```

   The rule applies only to this verification guest; a person's Linux
   computer asks for their password.
4. Shut the guest down with `tart stop <linux-golden>` and set
   `posato.vm.linuxGolden`. `posato-control linux create` clones it as
   `posato-run-linux`.

The Linux status icon (`LINUX-003`) needs the desktop a person runs: GNOME
with Ubuntu's AppIndicator extension, which the Openbox golden lacks. Make a
second golden line from the first:

1. Clone and boot it: `tart clone <linux-golden> <linux-gnome-golden>` and
   `tart run <linux-gnome-golden> --no-graphics &`.
2. Install Ubuntu's minimal desktop and a capture tool (as root, after
   `apt-get update`):

   ```shell
   DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
     ubuntu-desktop-minimal yaru-theme-gtk yaru-theme-icon yaru-theme-gnome-shell \
     gnome-terminal gnome-control-center nautilus gnome-screenshot xdg-utils
   ```

   The desktop settings install a netplan file that hands the network to
   NetworkManager, which this install leaves out, so a clone boots without a
   network. Keep the guest on systemd-networkd:

   ```shell
   printf 'network:\n  version: 2\n  renderer: networkd\n' > /etc/netplan/00-network-manager-all.yaml
   chmod 600 /etc/netplan/00-network-manager-all.yaml
   ```

   The desktop also brings the software updater, whose windows cover
   Posato in screenshots; remove it (this removes only the
   `ubuntu-desktop-minimal` metapackage besides it):

   ```shell
   DEBIAN_FRONTEND=noninteractive apt-get purge -y update-notifier update-manager
   ```

3. Replace LightDM with GDM, log the guest user in automatically, and store
   Xorg as the user's session, which `xdotool` and whole-screen capture
   need; Wayland stays available:

   ```shell
   systemctl disable lightdm
   echo /usr/sbin/gdm3 > /etc/X11/default-display-manager
   ln -sf /usr/lib/systemd/system/gdm3.service /etc/systemd/system/display-manager.service
   printf '[daemon]\nAutomaticLoginEnable=true\nAutomaticLogin=<guest-user>\nWaylandEnable=true\nDefaultSession=ubuntu-xorg.desktop\n' \
     > /etc/gdm3/custom.conf
   mkdir -p /var/lib/AccountsService/users
   printf '[User]\nSession=ubuntu-xorg\nXSession=ubuntu-xorg\nSystemAccount=false\n' \
     > /var/lib/AccountsService/users/<guest-user>
   ```

4. Turn off the lock screen, blanking, and the welcome dialog with a dconf
   system database (`/etc/dconf/profile/user` with `user-db:user` and
   `system-db:local`, keys `org/gnome/desktop/screensaver/lock-enabled=false`,
   `idle-activation-enabled=false`, `org/gnome/desktop/session/idle-delay=uint32 0`,
   and `org/gnome/shell/welcome-dialog-last-shown-version='999'` in
   `/etc/dconf/db/local.d/`, then `dconf update`), remove
   `gnome-initial-setup` if present, and as the guest user link
   `~/.Xauthority` to `/run/user/<uid>/gdm/Xauthority`.
5. Shut it down from inside and set `posato.vm.linuxGnomeGolden` (default
   `posato-golden-linux-gnome`).

`posato-control linux create --golden gnome` clones it as
`posato-run-linux`; `--session wayland` then logs the user in to the
Wayland session (GDM stops, the session in `custom.conf` and
AccountsService changes, `accounts-daemon` restarts, GDM starts). Under
Tart's virtio GPU, GNOME runs on Wayland. There, `gnome-screenshot -f`
captures the whole screen, while GNOME Shell's own `Screenshot` D-Bus
method refuses other callers, and `xdotool` input does not reach Posato's
window; `wmctrl -c Posato` closes it politely.

What the status icon relies on (`observed` 2026-10-21, `LINUX-003`): the
AppIndicator extension lists items in its watcher as `<bus name>@<path>`,
shows XEmbed tray icons too but in an unthemed AWT menu, finds an item's
themed icons in `IconThemePath` only when that folder has an
`index.theme`, and never sends dbusmenu's `closed` event. It also
rejects a `Properties.Get` reply that is not a variant (dbus-java answers
with the getter's own type unless the getter returns a `Variant`), reads
the menu when the item registers, ignores `LayoutUpdated` while its menu
is closed, and never opens a menu it holds empty, so an item that
registers before its menu has lines shows an empty menu. An autostart
entry runs at the next login on the session's own bus, which `linux
relogin` reproduces.

For Android, install the Android SDK's command-line tools, emulator, and a
Google APIs system image for API 33 or later, then create and boot an
emulator only Posato uses:

```shell
avdmanager create avd -n <posato-avd> -k "system-images;android-<api>;google_apis_playstore;arm64-v8a" -d pixel_7
emulator -avd <posato-avd> -port 5560 -no-window -no-audio -no-boot-anim -no-snapshot-save &
```

Set `posato.android.serial` to its serial (`emulator-5560` for that port).
Never drive an emulator that someone else uses.

A phone on USB works the same way: turn on Developer options and USB
debugging, answer "Allow USB debugging?" once with Always allow, and pass its
serial with `--udid` (`posato-control devices list` shows it). `doctor -t
android` names what a phone adds: another app's VPN, which a website pause
turns off, strict private DNS, which bypasses website pausing, and a screen
lock, after which a reboot needs one unlock by a person. The emulator has
none of these.

## The Windows golden VM

Windows (`PLATFORM-002`) runs in a Tart Windows 11 ARM64 guest. Upstream
Tart runs only macOS and Linux guests, so Windows needs a separately built
fork with its own `TART_HOME`, which keeps its VMs apart from the others.
The fork relies on private Virtualization framework settings (PMU and trap
emulation), which a macOS update may remove.

1. Build the unofficial fork
   [`mustafaakin/tart`](https://github.com/mustafaakin/tart), branch
   `windows-guest-extras` at `e4c623c`, after reviewing it:

   ```shell
   git clone https://github.com/mustafaakin/tart.git tart-windows && cd tart-windows
   git checkout e4c623c
   brew install lld        # rebuilds the fork's embedded UEFI drivers
   make -C Resources/Windows
   swift build -c release --product tart
   mkdir -p build/tart.app/Contents/MacOS
   cp .build/release/tart build/tart.app/Contents/MacOS/tart
   cp Resources/Info.plist build/tart.app/Contents/Info.plist
   codesign --sign - --entitlements Resources/tart-dev.entitlements --force build/tart.app
   ```

2. Build the Windows guest agent from
   [`mustafaakin/tart-guest-agent`](https://github.com/mustafaakin/tart-guest-agent)
   at `f15198a`:

   ```shell
   git clone https://github.com/mustafaakin/tart-guest-agent.git && cd tart-guest-agent
   git checkout f15198a
   GOOS=windows GOARCH=arm64 go build -o build/tart-guest-agent.exe ./cmd
   ```

3. Download a Windows 11 ARM64 ISO from Microsoft. You are responsible for
   its license; the guest is not activated. Keep about 40 GB free for the
   golden VM and the installation media.
4. Create the golden VM with no interaction (about 25 minutes):

   ```shell
   export POSATO_TART_WINDOWS=<tart-fork>/build/tart.app/Contents/MacOS/tart
   export POSATO_TART_WINDOWS_AGENT=<tart-guest-agent>/build/tart-guest-agent.exe
   export POSATO_TART_WINDOWS_HOME=~/.tart-windows
   tools/posato-control/windows/create-golden.sh <Win11_Arm64.iso>
   ```

   `tart create --from-iso` installs Windows unattended in two boots. The
   script then uses the image's `admin`/`admin` account over SSH, which only
   Tart's NAT network reaches, to run
   [`setup-guest.ps1`](../../tools/posato-control/windows/setup-guest.ps1):
   the `viosock` and `vioserial` drivers, the guest agent as a scheduled task
   at sign-in (so `tart exec` reaches the desktop), UAC, sleep, and the screen
   saver off, Windows Update off and Defender exclusions for the driver's
   folders, JDK 21 for Windows on ARM with the Java Access Bridge on, and
   Python with `java-access-bridge-wrapper`. The guest keeps `admin`/`admin`,
   has UAC off, and answers SSH on Tart's NAT network, which is acceptable only
   for this disposable test guest without personal data. The JDK link follows
   the latest JDK 21 build, so golden VMs made at different times can differ in
   the JDK patch release.
5. Set `posato.vm.windowsTart` (the fork's binary), and, when they differ
   from the defaults, `posato.vm.windowsTartHome` (`~/.tart-windows`) and
   `posato.vm.windowsGolden` (`posato-golden-windows`). The environment
   variables above override them.

`posato-control windows create` clones it as `posato-run-windows` (about 30
seconds until the agent answers); the driver copies its own guest scripts in
on each boot, so changing them needs no new golden VM.

## Register the VMs for development signing

The sync companion's development profile only runs on registered devices.
Each golden VM line needs one registration; its per-run clones inherit the
identifier. Register them with `posato-provisioning`, never in the portal:

```shell
tart run <primary-golden> --no-graphics &
tart run <peer-golden> --no-graphics &
tools/posato-provisioning/build/install/posato-provisioning/bin/posato-provisioning \
  devices register --tart-vm <primary-golden> --tart-vm <peer-golden>
```

Then refresh the profile with
`posato-provisioning profiles ensure app.posato.macos.sync --replace`, shut
both guests down from inside, and rebuild with `posato-control build -t
desktop`. Never run a golden VM at the same time as one of its clones: macOS
then gives one of them a new identifier, which the profile no longer covers.

## Configure the driver

Add to the ignored `local.properties`:

```properties
posato.vm.primaryGolden=<primary-golden>
posato.vm.peerGolden=<peer-golden>
posato.vm.legacyGolden=<legacy-golden>
posato.vm.venturaGolden=<ventura-golden>
posato.vm.linuxGolden=<linux-golden>
posato.vm.windowsTart=<tart-fork>/build/tart.app/Contents/MacOS/tart
posato.android.serial=<emulator-serial>
posato.vm.adminKeychainService=<vm-admin-item>
posato.vm.adminKeychainAccount=<guest-user>
posato.vm.accountKeychainService=<apple-account-item>
posato.vm.accountKeychainAccount=<test-account-email>
posato.vm.accountPhoneKeychainService=<apple-account-phone-item>
posato.control.devicePasscodeKeychainService=<iphone-passcode-item>
posato.control.devicePasscodeKeychainAccount=<any-label>
```

On the test iPhone, enable Settings → Developer → Enable UI Automation. The
first driver run after that, and the first after each restart, shows a
passcode request for XCTest on the phone; its owner enters it once.
Also set Settings → Display & Brightness → Auto-Lock to Never: a locked
screen makes every driver run fail with `DEVICE_AUTOMATION_LOCKED`, and a
phone that locks during a pause between runs stops an unattended
verification until someone unlocks it.

## Running a verification

```shell
PC=tools/posato-control/build/install/posato-control/bin/posato-control
$PC build -t desktop
$PC vm create --line primary        # and --line peer for Mac-to-Mac sync
$PC launch -t desktop --vm primary
$PC run -t desktop --vm primary --scenario tools/posato-control/fixtures/scenarios/onboarding-sync-desktop.json
$PC vm prompt background --line primary
$PC run -t desktop --vm primary --scenario tools/posato-control/fixtures/scenarios/onboarding-helper-finish-desktop.json
$PC vm destroy --line primary
```

Desktop commands take `--vm primary|peer|legacy|ventura` and run inside the guest; their
evidence is copied to `build/verification/runs/<run>/guest/`. The
`verify-posato` skill lists the recipes, the prompts each one raises, and the
recovery steps for iCloud dialogs.

## Refreshing a golden VM

After a macOS update in a golden VM, or a new `tart-guest-agent` binary,
re-grant the two privacy approvals (step 7), sign in again if macOS asks, and
clean up before shutting down (step 9). A new golden VM needs a new device
registration.
