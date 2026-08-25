# MVP Interaction Prototype

## Source identity

- **Repository revision:**
  `a081d4278cf8517c46b4322ed3c098462f153570`
- **Artifact:** `prototypes/mvp-interaction-flow/index.html`
- **Source type:** disposable interactive UX prototype
- **Reviewed:** 2026-08-25
- **Authority:** evidence only; not a product, design, architecture, or
  implementation authority

## Why this source exists

The prototype makes the accepted low-fidelity MVP flows inspectable as one
interactive browser artifact. It is useful for finding missing states,
contradictory actions, large-window layout problems, and unclear local-versus-
shared ownership before production UI work starts.

The accepted [design authority](../../../DESIGN.md),
[MVP scope](../../product/mvp-scope.md), and
[architecture baseline](../../decisions/0003-mvp-application-architecture-baseline.md)
remain authoritative. Future task plans may cite this source as usability
evidence, but they must not import its reducer, geometry, fixtures, or web
implementation as a product contract.

## Maintainer-confirmed evidence

- `user-confirmed`: Free play is an inspection workbench. It may prepare
  deterministic prerequisite state so every listed prototype action can be
  exercised independently.
- `user-confirmed`: the prototype must demonstrate configurable exact website
  entries and synthetic application selection rather than imply a fixed
  one-site, one-application limit.
- `user-confirmed`: macOS needs a large-window presentation that keeps sparse
  task content and its primary action visually related.
- `user-confirmed`: the prototype is an MVP UX artifact, not production code.

## Browser-observed evidence

At the pinned revision, browser checks directly exercised representative
strict product paths for:

- first-installation setup through ready state;
- session setup, review, start, blocked presentation, intentional early end,
  and normal expiry;
- action-required review for missing permission and missing local application
  mapping; and
- existing-workspace discovery with delayed synchronizable-Keychain delivery,
  waiting, retry, and no parallel-workspace action.

Additional recorded checks exercised all Free play actions from reset,
configurable website and synthetic application items, invalid and duplicate
input, preservation of the last valid value during a failed edit, independent
Mac and iPhone application mappings, keyboard submission, responsive overflow,
and automated browser accessibility scanning in the checked states.

These are `observed` prototype results, not production acceptance evidence.

## Evidence limits

- The artifact runs in a browser and does not prove Compose behavior, native
  Apple component behavior, platform authorization, enforcement, CloudKit,
  Keychain, helper IPC, extension lifecycle, accessibility technology support,
  performance, security, or physical-device behavior.
- Synthetic application names, sample domains, validation and normalization
  rules, timing, layout geometry, copy variants, and deterministic workbench
  prerequisites are fixtures or hypotheses unless an accepted authority says
  otherwise.
- Free play shortcuts do not relax the strict product state model.
- Automated accessibility scans do not replace keyboard, VoiceOver, Voice
  Control, Switch Control, Dynamic Type, Full Keyboard Access, contrast, and
  reduced-motion verification in the production applications.
- The source must not be copied wholesale into product code.

## Planning use

The accepted [MVP roadmap](../../../tasks/mvp-roadmap.md) routes this evidence to
the smallest UI tasks that consume it: exact-domain and semantic-application
target management, device-local mapping, shared manual-session behavior, local
session integration, and first- and second-installation onboarding. Each task
must still plan and obtain fresh Compose, simulator, runtime, accessibility,
and physical-device evidence where applicable.

## Related synthesis

- [Brand and product design baseline](../topics/brand-and-design-baseline.md)
- [MVP open questions](../topics/mvp-open-questions.md)
