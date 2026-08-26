# `DIAGNOSTICS-001`: Define MVP diagnostics and support-data policy

- **Review tier:** `high-risk`
- **Tier reason:** The task decides whether sensitive operational and behavioral
  data may be recorded, retained, exposed, or exported across application and
  platform trust boundaries.
- **Dependencies:** `SECURITY-001`
- **Integration group:** `PR-DIAGNOSTICS`
- **Authority:** [MVP roadmap revision 2](../mvp-roadmap.md) and maintainer
  activation on 2026-08-26

## Outcome

The maintainer can accept one concise authority that defines the Apple MVP's
diagnostic purposes, field allowlist, prohibited data, redaction, consent,
local retention and deletion, user exposure, support export, and remote-
collection boundary.

## Boundaries

- Cover only the accepted Apple MVP and remain within the accepted threat
  model's data classification and no-behavior-history rule.
- Separate always-available user status, explicitly enabled local diagnostic
  capture, user-initiated support export, and automatic remote collection.
- Use `.research/blocker` only for bounded redaction and privacy-test evidence;
  do not inherit PoC schemas, logging code, identifiers, or collection scope.
- Define the smallest policy future producers must satisfy without adding a
  logger, SDK, support backend, export format, UI, dependency, or production
  diagnostic implementation in this task.
- Keep analytics, automatic telemetry, automatic crash upload, support-account
  data, browsing or application-use history, and release privacy notices out of
  scope.

## Acceptance

- `AC-01` — Every permitted diagnostic surface has one explicit purpose and a
  clear default, consent, exposure, and transmission boundary.
- `AC-02` — An explicit field allowlist and prohibited-data list prevent
  diagnostics from reconstructing policy, identity, browsing, application use,
  or content while retaining stable actionable failure categories.
- `AC-03` — Local capture and support export have bounded retention, deletion,
  preview, and user-controlled sharing rules; the MVP adds no remote processor.
- `AC-04` — Future producing tasks own their concrete event names, caps,
  redaction tests, and user-facing behavior without broadening this policy.
- `AC-05` — The maintainer explicitly accepts the reviewed proposal before it
  becomes an accepted authority or its conclusions enter the maintained wiki.

## Verification

- Traceability review against the threat model, MVP scope, architecture and
  synchronization decisions, relevant wiki synthesis, and exact PoC/spike
  privacy evidence.
- Documentation links, `git diff --check`, scoped prohibited-data scan, and
  independent high-risk plan and completed-change reviews.

## Decisions or blockers

- Maintainer privacy acceptance of the reviewed proposal is required to
  complete the task.
