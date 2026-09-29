# Execution: Intel Ventura helper registration recovery

- **Brief:** [Intel Ventura helper registration recovery](../specifications/intel-ventura-helper-recovery.md)
- **Status:** `done`
- **Review tier:** High-risk

## Plan

1. Preserve the current BTM record and filtered system logs under ignored verification output. Verify the signed installed bundle, absence of Posato proxy ownership, and HTTP/HTTPS proxy state for every network service. Reserve the full quiescence and third-party approval inventory for any later registration reset.
2. Review the exact BTM registration and find a Posato-only public repair path. Do not unregister a daemon when the application's cleanup protocol cannot confirm Idle.
3. Identify every logged-in user and obtain the maintainer's confirmation that their work is saved. State that reboot ends GUI sessions and SSH. With a separate maintainer decision, restart the Mac after the confirmed daemon approval. After reconnecting, read BTM, `launchd`, and proxy state before any in-app setup. Avoid any BTM reset in this step.
4. If no narrower path can restore the orphaned registration, present the complete third-party item inventory, prior enabled/disabled states, and a restoration plan. Request a fresh maintainer decision for `sudo sfltool resetbtm` and a restart. Explain that the command resets every third-party Background Item approval. Require local macOS authentication; never collect the password.
5. After an authorized reset, restore unrelated background-item preferences, register the existing signed candidate through its setup flow, verify the system daemon in `launchd`, and complete the in-app setup. Stop on any failed precondition or unexpected state.

## Result

- The Ventura BTM log recorded the person's daemon approval. The daemon changed from `disallowed` to `enabled, allowed`, but BTM reported no container item and `launchd` had no Posato daemon. The installed app, helper, and daemon pass strict signature verification.
- The privileged read-only `sfltool dumpbtm` listed Posato's daemon as enabled and allowed, a disabled Posato helper-app record, one enabled third-party app, and an enabled updater agent for another local account under a disabled developer entry. The raw inventory remains ignored because it contains personal paths and identifiers.
- `who` showed two local accounts with console sessions and one Terminal session. Neither account's unsaved GUI work can be inferred from the command. The reboot preflight therefore needs explicit confirmation covering both accounts.
- The maintainer confirmed work was saved in both accounts and restarted the Mac. After reboot, `launchctl print system/app.posato.macos.proxy-settings` found the signed daemon submitted by Service Management, with the expected parent bundle and successful exit status. A relaunch of the installed Posato app started its helper and caused another successful daemon run. The Wi-Fi HTTP and HTTPS proxies remained disabled.
- The maintainer then chose **Finish** in the app and entered the administrator password in the macOS dialog. `user-confirmed`: setup completed. A follow-up read showed five successful daemon runs, the exact Apply and standing-Apply authorization rights present, and Wi-Fi HTTP/HTTPS proxies still disabled while idle. The raw `sfltool dumpbtm` file was removed from the MacBook after the ignored local copy was secured. No Background Items reset, unregister, or data removal occurred.

## Verification

- **Revision and target:** `33dd5b7` packaged source tree; notarized x86-64 candidate on the 2019 Intel MacBook Air running macOS 13.7.8.
- **Initial state:** The daemon was BTM `enabled, allowed` but absent from `launchd`; Posato setup reported a registered helper that could not start. No Posato ownership record or standing grant existed and every network service had HTTP/HTTPS proxies disabled.
- **Actions and evidence:** The maintainer restarted the Mac after confirming saved work in both accounts; `open -a /Applications/Posato.app` relaunched the installed candidate; the maintainer chose **Finish** and authenticated locally. Read-only `launchctl print system/app.posato.macos.proxy-settings`, `security authorizationdb read` for each custom right, `networksetup -getwebproxy Wi-Fi`, and `networksetup -getsecurewebproxy Wi-Fi` confirmed the submitted daemon, rules, and idle proxy state. Filtered logs and pre-restart BTM evidence are under `build/verification/intel-ventura-launch-fix/`.
- **Asserted result:** The daemon is registered and callable, and the maintainer reports that unified setup completed. No manual blocking session or website/application enforcement was exercised on this MacBook, so this does not prove those flows on Ventura.
- The only documented system reset is global. No reset, unregister, or application-data change occurred.

## Independent plan review

- **Verdict:** Changes required before any reset. The reviewer required a readable inventory of all affected third-party Background Items and their previous approval states, plus a restoration plan. The reviewer also required a complete quiescence preflight covering the session, in-flight setup, processes, lease, ownership, standing grant, and proxy state. Both requirements are now in the plan; their evidence is incomplete. A fresh maintainer decision remains mandatory under the earlier MACOS-007 recovery record.
- **Reboot-first review:** Approved as a narrower diagnostic path after one Required safeguard: identify all logged-in users, confirm saved work and the planned session/SSH interruption, obtain separate maintainer authorization, then inspect BTM, `launchd`, and proxies before retrying setup. The maintainer confirmed saved work and performed the restart. The post-restart checks passed.
