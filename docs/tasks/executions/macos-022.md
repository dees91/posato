# MACOS-022 execution

- **Status:** active

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
