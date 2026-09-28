# MACOS-022 execution

- **Status:** complete; ready for review and merge

## Plan

1. Confirm why the packaged helper does not request Automation or present the page.
   Inspect its hardened-runtime signature and AppKit event delivery.
2. Add the minimal Apple Events entitlement to the helper, responsible parent, and exact packaging checks.
   If confirmed necessary, keep the main run loop responsive while serialized pipe
   handling retains exclusive ownership of lifecycle state and exit cleanup.
3. Verify supported browser presentation and denial independently in fresh Tart,
   including unrelated tabs and cleanup; run applicable native and quality checks.
4. Complete independent review and close out the authority, wiki, roadmap, and PR.

## Plan review

Approved by an independent reviewer; no Critical or Required findings.
Keep pipe state serialized, picker work on the main actor, and explicit exit
after cleanup if moving the reader off the main thread.

## Regression evidence

The pre-fix build denied Chrome HTTPS with ERR_TUNNEL_CONNECTION_FAILED;
no Automation request appeared, while opening the loopback route directly worked.
The prior reproduction is recorded in PR #106 and ignored verification artifacts.

## Confirmed diagnosis

The entitlement-only candidate still failed. After adding the main run loop,
TCC showed requests attributed to the Tart guest agent. The driver now uses its
existing LaunchServices path for staged builds as well as installed candidates.
TCC then attributed the request to Posato and explicitly rejected its missing
Apple Events entitlement. An independent plan-extension review approved this
conditional parent grant and the driver correction with no Required findings.


## Result

The helper now services its main run loop while a serial worker owns pipe and
lifecycle state. Native selection and NSAppleScript run on the main thread;
proxy denial and restore remain independent of browser presentation. Both the
responsible app and helper carry the Apple Events grant, with exact packaging
checks; the privileged daemon has none. Staged verification launches through
LaunchServices, and the driver can answer Posato Automation consent.

A live stack sample located the remaining hang in AppleScript on a background
thread. Moving presentation to the main thread restored Chrome and Safari.

## Verification

- `./gradlew quality`: passed, including native checks, shared tests, iOS tests,
  packaging verification, and lint.
- Signed development package: passed `posato-control build -t desktop --verify`.
- Tart: Chrome regular and Incognito, Safari regular and Private reached the
  loopback pause page after consent. Denial retained blocking without repeated
  prompts. An unrelated foreground page stayed unchanged.
- End early restored direct access. The native picker selected and removed
  Calculator. Parent termination removed helper processes and proxy settings.
- Fresh-clone repeat of application revision `b01d6cd` passed: first Automation
  consent led Chrome to the pause page. The final driver addition handles the
  default-browser system question; `:posato-control:check` passed afterward.
- Aggregate evidence: ignored `build/verification/runs/macos022-result/README.md`;
  exact-revision screenshot: `build/verification/runs/macos022-final-browser/`.
  No host Posato installation was used.

## Completed-change review

Independent review approved the implementation with no Critical or Required
findings. It checked serialized lifecycle ownership, main-thread native work,
permission scope, exact signing checks, LaunchServices arguments, and consent
prompt attribution.
