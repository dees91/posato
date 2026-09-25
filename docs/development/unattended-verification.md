# Unattended Verification Environment

Every task verifies its change in this environment; attended verification is
an exception that needs a named extraordinary reason (`AGENTS.md`,
Application verification). The macOS application never runs on the host Mac:
the host builds the package and `posato-control` refuses to drive it there. This guide sets up the environment in which an
agent verifies a Posato change with no person at the Mac or the phone: the macOS app inside Tart virtual
machines and the iOS app on a dedicated test iPhone, both signed in to a
dedicated test Apple Account. After the one-time setup below, the
`posato-control` driver answers every system dialog itself. Background,
measurements, and platform limits are in the
[unattended verification topic](../wiki/topics/unattended-verification.md).

Nothing in this guide is tracked: VM names, Keychain item names, and device
choices live in the ignored `local.properties`; passwords live only in the
login Keychain.

## What you need

- An Apple silicon Mac on macOS 15 or later with about 80 GB free disk.
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
8. Clean the golden VM before shutting it down: quit System Settings and
   Terminal so they do not reopen at login.

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
   automatic update checks. After that, continue with `tart exec` as in
   steps 5-8.
4. For the two-factor code, answer the sign-in alert on the test iPhone with a
   `-t device` scenario scoped to `springboard`, read the code from its
   screenshot, and type it with `vm type`.
5. `$PC vm shutdown --line legacy`, then
   `tart rename posato-run-legacy <legacy-golden>`.

A new device signing in can make the other golden VMs report "Some iCloud
Data Isn't Syncing". iCloud Keychain items, including the Posato workspace key,
then stop reaching them. Choose System Settings > Resume Data Sync and answer
`vm prompt account-password` and `vm prompt mac-password`. If Apple asks
for the trusted phone number, type it with
`vm type --secret phone` from the optional phone item above.

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

Desktop commands take `--vm primary|peer|legacy` and run inside the guest; their
evidence is copied to `build/verification/runs/<run>/guest/`. The
`verify-posato` skill lists the recipes, the prompts each one raises, and the
recovery steps for iCloud dialogs.

## Refreshing a golden VM

After a macOS update in a golden VM, or a new `tart-guest-agent` binary,
re-grant the two privacy approvals (step 7), sign in again if macOS asks, and
clean up before shutting down (step 8). A new golden VM needs a new device
registration.
