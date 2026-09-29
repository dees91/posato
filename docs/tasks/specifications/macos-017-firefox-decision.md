# `MACOS-017`: Decide Firefox support on macOS

- **Review tier:** Standard
- **Tier reason:** A discovery row that ends with a proposed ADR 0005
  revision and a delivery plan; it merges no product code. The delivery row
  `MACOS-023` takes its own tier, likely High-risk.
- **Dependencies:** `MACOS-022` (merged in #107); release 1.3, wave 2.
- **Integration group:** PR-FIREFOX-DECISION
- **Authority:** [Release roadmap](../release-roadmap.md) revision 13, row
  `MACOS-017`; maintainer named the row on 2026-09-29.

## Outcome

The maintainer can accept or reject a Firefox support promise for macOS on
the basis of a proposed ADR 0005 revision and, if accepted, a delivery plan
for `MACOS-023`.

## Boundaries

- Evidence comes from the spike in #110, rebased onto `main` and run once in
  Tart. The spike stays unmerged; this PR carries only documents.
- The proposal answers:
  - whether an ordinary Firefox profile honors the session proxy without
    Posato changing its settings, which ADR 0005 forbids today;
  - presentation through a Posato extension, and its signing and
    distribution through addons.mozilla.org for Firefox Release;
  - the rendezvous: the spike's fixed loopback port 48151 against the
    per-session port, its fail-closed conflict, whether any web page can
    probe it to learn that a session is active, and alternatives such as
    native messaging;
  - the extension's permissions and the privacy boundary: no browsing
    history or allowed navigation recorded anywhere;
  - This Mac setup guidance and how an unattended run verifies it.
- Non-goals: other browsers, Firefox channels other than Release and the
  one used for verification, and TLS interception.

## Acceptance

- `AC-01` — The rebased spike runs in Tart on current `main`. Blocked HTTPS
  in Firefox shows the pause page and unrelated sites load, or the failure is
  recorded.
- `AC-02` — A proposed ADR 0005 revision states the support scope, the
  mechanism, its privacy and local-trust limits, and the rejected
  alternatives.
- `AC-03` — A `MACOS-023` delivery plan names its tier, acceptance criteria,
  and maintainer gates (for example the addons.mozilla.org account), or the
  proposal recommends no support and says why.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- One Tart run (`--vm primary`) of the rebased spike, with Firefox Nightly
  and the unsigned extension: a denied HTTPS site, an unrelated site, and a
  port conflict. Evidence goes under ignored `build/verification/`.

## Decisions or blockers

- Accepting the ADR 0005 revision is the maintainer's decision; this row
  only proposes it.
