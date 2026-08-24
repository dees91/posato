# Feasibility Results and Limits

## Status summary

| Area | Result | Evidence boundary | Not established |
| --- | --- | --- | --- |
| Cross-device synchronization | `observed`: PASS | One physical Mac and one physical iPhone, two complete exchanges through normal application entry points, required failure checks, privacy checks, clean-source configuration, and exact cleanup | Production scale, delivery guarantees, long-running reliability, App Store readiness, account migration, or a final cryptographic format |
| Apple enforcement | `observed`: PASS | Named development rows for selected macOS browsers and application termination, plus selected iOS websites and an opaque application selection on attached physical devices | Bypass resistance, persistence across every lifecycle transition, arbitrary-browser support, production entitlement approval, distribution, or four-platform parity |

These results answer whether selected mechanisms can work under controlled
conditions. They do not convert experimental implementations into production
architecture.

## Synchronization evidence

`observed`: an earlier synchronization PASS was withdrawn because the tested
application shells did not exercise the complete runtime. The corrected run
routed the full coordinator, persistence, encryption, transport, membership,
and recovery path through the normal desktop and iOS application entry points.

The corrected physical matrix established that:

- each device could originate and receive immutable operations;
- deterministic reduction converged after bidirectional exchange;
- valid local state survived the named offline, transport, integrity, and key
  failures;
- enrollment, recovery, revocation, and workspace deletion paths were
  exercised within the PoC contract;
- the selected CloudKit private-database path did not require an independent
  application backend;
- project-owned test data and configuration could be removed exactly.

The matrix did not establish a service-level delivery guarantee. Cloud-backed
synchronization remained retryable and lifecycle-dependent.

## Enforcement evidence

`observed`: the macOS development spike demonstrated selected domain blocking
through a local HTTP/HTTPS proxy without decrypting TLS. Selected browser
presentation behavior and application termination were exercised separately.

`observed`: the iOS development spike demonstrated exact synthetic website
shielding and one opaque application selection through Apple-managed controls.
The application target remained an opaque native value and was not converted
into a portable identifier.

`observed`: the final enforcement cleanup removed project-owned proxy settings,
temporary authorization, shields, profiles, processes, files, and test state.
Cleanup success is evidence about the harness, not a product uninstall design.

## Evidence quality rules

- A report status is accepted only with its named topology and test boundary.
- A physical-device result does not imply broad device or operating-system
  coverage.
- A development entitlement or locally installed helper does not imply public
  distribution eligibility.
- Browser-specific presentation is distinct from network enforcement.
- A privacy assertion about inspected logs is not a complete product privacy
  proof.
- PoC dependency versions are reproducibility evidence, not current MVP
  requirements.

## Consequences for MVP work

`user-confirmed`: the MVP is a clean implementation informed by these results,
not a promotion of the PoC repository.

The first production slices should therefore:

1. define a small product contract and acceptance test;
2. select the smallest platform mechanism already supported by evidence;
3. recreate the boundary with production naming, errors, lifecycle ownership,
   and observability;
4. port only algorithms or fixtures whose behavior is worth preserving;
5. review each claim again against current platform documentation and a current
   physical test.

## Open verification work

- Re-run selected mechanisms on the MVP deployment targets and current OS
  versions.
- Verify current Apple entitlement and distribution requirements before those
  requirements enter a release plan.
- Define browser and application support promises explicitly.
- Add longevity, restart, network-transition, upgrade, uninstall, and recovery
  tests as the corresponding MVP slices appear.
- Establish a production threat model and independent review scope.
