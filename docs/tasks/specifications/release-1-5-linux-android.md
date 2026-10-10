# `PLATFORM-001`, `SYNC-018`, `LINUX-001`, `ANDROID-001`: Linux and Android with a folder workspace

- **Review tier:** high-risk
- **Tier reason:** cryptographic key delivery (pairing code), a new root
  helper and trust boundary on Linux, a VPN and system-overlay boundary on
  Android, and a synchronization transport.
- **Dependencies:** none outside the rows; milestones run in order M1 to M3.
- **Integration group:** stacked pull requests `PR-PORTABLE-FOLDER` (M1),
  `PR-LINUX` (M2), `PR-ANDROID` (M3).
- **Authority:** [release roadmap](../release-roadmap.md) revision 22 and
  [ADR 0010](../../decisions/0010-linux-android-and-folder-workspace.md)
  (proposed); maintainer direction of 2026-10-10.
- **Record:** [execution record](../executions/release-1-5-linux-android.md)

## Outcome

One person's Linux computer, Android phone, and Mac share one Posato
workspace through a folder that another service keeps synchronized, and a
pause started on any of them blocks the chosen websites and applications on
each of them.

## Boundaries

- M1 `SYNC-018` (+ `PLATFORM-001`): ADR 0010; the folder transport and
  pairing in `:shared` on the existing encrypted operation model; the Mac
  desktop offers **Sync with a folder** beside iCloud; `posato-control` shares
  a host folder with Tart guests.
- M2 `LINUX-001`: the Linux variant of `:desktopApp`, the `.deb`, the root
  helper, desktop-entry application choice, and `posato-control --vm linux`.
- M3 `ANDROID-001`: `:androidApp`, DNS `VpnService`, usage-access app
  blocking, schedules with a foreground service, and `posato-control -t android`.
- Non-goals: iOS folder mode, Windows, migration from iCloud to a folder,
  device revocation, pause pages on Linux and Android, Google Play, publishing
  (`RELEASE-007`), x86-64 Linux E2E.
- ADR 0006 format 1 and the macOS enforcement contracts stay unchanged.

## Acceptance

- `AC-01` — Two Tart Macs sharing one host folder pair with a code; a
  pause set change and a pause started on one appear on the other, and the
  existing iCloud path still links.
- `AC-02` — On a Tart Ubuntu guest, a pause blocks a chosen host (name
  resolves to no address) and ends a chosen application, and both are
  released at the end and after an early end; the helper clears by itself
  at the end when the application is not running.
- `AC-03` — On an Android emulator, a pause makes a chosen host unresolvable
  and covers a chosen application with the block screen, and both are
  released at the end.
- `AC-04` — A Linux guest, an Android emulator, and a Tart Mac in one
  folder workspace: a pause started on one applies on the other two.
- `AC-05` — A wrong or expired pairing code joins nothing and leaves the
  workspace intact.

## Verification

- `./gradlew quality` on the stack tip; Android `assembleDebug`; `.deb`
  built in the Linux guest.
- Isolated contract tests written first for the folder mailbox (partial or
  foreign files, re-delivery) and the pairing envelope (wrong code, expiry,
  tampering), because E2E cannot produce those inputs reliably.
- `posato-control` runs for `AC-01` to `AC-05`; evidence under
  `build/verification/`.

## Decisions or blockers

- ADR 0010 stays `Proposed` until the maintainer accepts it at merge.
