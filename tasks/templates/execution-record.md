# Execution: `<TASK-ID>`

- **Task specification:** `<relative link>`
- **Execution status:** `ready`
- **Implementer:** `<agent or maintainer identifier>`
- **Plan reviewer:** `<different agent identifier>`
- **Code reviewer:** `<different agent identifier>`
- **Branch/worktree:** `<portable branch name; do not record a personal path>`
- **Started:** `<YYYY-MM-DD>`
- **Last updated:** `<YYYY-MM-DD>`

## Technical implementation plan

### Repository findings and authorities

- `<Relevant observed state and accepted authority>`

### Assumptions and open questions

- `<Assumption, decision owner, and whether it blocks implementation>`

### Proposed changes

- `<Exact files, interfaces, dependencies, migrations, and ordered steps>`

### Test and verification strategy

- `<Tests to add or update and why they prove behavior>`
- `<Exact focused and aggregate commands>`
- `<Required runtime, simulator, device, visual, accessibility, or manual checks>`

### Risk and recovery

- `<Risk, mitigation, rollback, migration, or N/A with reason>`

## Plan review

| Round | Reviewer | Verdict | Critical/Required findings | Resolution |
| --- | --- | --- | --- | --- |
| 1 | `<agent>` | `<approve | request-changes>` | `<findings or none>` | `<correction or pending>` |

### Non-blocking plan findings

| Round | Severity | Finding | Disposition |
| --- | --- | --- | --- |
| `<round>` | `<Recommended | Optional>` | `<finding>` | `<applied, declined, or deferred with reason>` |

- **Approved before implementation:** `<yes | no>`
- **Approval evidence:** `<concise verdict and date>`

## Implementation summary

- `<What changed and why>`

## Deviations from the approved plan

- `<Deviation and reviewer disposition, or none>`

## Code-review rounds

| Round | Reviewer | Verdict | Critical/Required findings | Resolution |
| --- | --- | --- | --- | --- |
| 1 | `<agent>` | `<approve | request-changes>` | `<findings or none>` | `<correction or pending>` |

### Non-blocking code-review findings

| Round | Severity | Finding | Disposition |
| --- | --- | --- | --- |
| `<round>` | `<Recommended | Optional>` | `<finding>` | `<applied, declined, or deferred with reason>` |

After three unsuccessful rounds, record escalation to a fresh reviewer or the
maintainer.

## Final verification

All evidence below must be obtained after the last material correction.

### Acceptance-criteria evidence

| Criterion | Result | Evidence |
| --- | --- | --- |
| `AC-01` | `<pass | fail>` | `<command, observation, or artifact>` |

### Definition of Done evidence

| DoD item | Result | Evidence or justified N/A |
| --- | --- | --- |
| `DoD-01` | `pass` | `<accepted specification revision or recorded override>` |
| `DoD-02` | `pass` | `<acceptance-criteria evidence below>` |
| `DoD-03` | `pass` | `<independent plan-review approval>` |
| `DoD-04` | `pass` | `<independent completed-change approval>` |
| `DoD-05` | `<pass | N/A>` | `<evidence>` |
| `DoD-06` | `pass` | `<applicable quality checks after final correction>` |
| `DoD-07` | `<pass | N/A>` | `<evidence>` |
| `DoD-08` | `pass` | `<authority-compliance evidence>` |
| `DoD-09` | `pass` | `<documentation and routing evidence>` |
| `DoD-10` | `pass` | `<diff, secret, portability, and formatting evidence>` |
| `DoD-11` | `pass` | `<recorded risks, N/A entries, findings, and deviations>` |
| `DoD-12` | `pass` | `<fresh final verdict and evidence>` |

`N/A` is allowed only for applicability-dependent test or runtime items. A
maintainer override is recorded explicitly and never represented as `N/A`.

### Verification applicability and evidence

| Check | Result | Evidence |
| --- | --- | --- |
| Aggregate local quality gate | `<pass | N/A>` | `<command and concise result>` |
| Formatting | `<pass | N/A>` | `<command and concise result or reasoned N/A>` |
| ktlint | `<pass | N/A>` | `<command and concise result or reasoned N/A>` |
| Detekt and Compose Rules | `<pass | N/A>` | `<command and concise result or reasoned N/A>` |
| Compiler warnings | `<pass | N/A>` | `<command and concise result or reasoned N/A>` |
| Unit tests | `<pass | N/A>` | `<command and concise result or reasoned N/A>` |
| Contract tests | `<pass | N/A>` | `<command and concise result or reasoned N/A>` |
| Integration tests | `<pass | N/A>` | `<command and concise result or reasoned N/A>` |
| Compose UI tests | `<pass | N/A>` | `<command and concise result or reasoned N/A>` |
| Platform builds or simulator | `<pass | N/A>` | `<command and concise result or reasoned N/A>` |
| Physical-device checks | `<pass | N/A>` | `<observation or reasoned N/A>` |
| Visual checks | `<pass | N/A>` | `<artifact, observation, or reasoned N/A>` |
| Accessibility checks | `<pass | N/A>` | `<artifact, observation, or reasoned N/A>` |
| Manual checks | `<pass | N/A>` | `<observation or reasoned N/A>` |
| Documentation links and routing | `<pass | N/A>` | `<command and concise result or reasoned N/A>` |
| Task-specific category | `<pass | N/A>` | `<evidence or reasoned N/A>` |

## Final verdict

- **Status:** `<done | blocked>`
- **Blocking findings remaining:** `<none or list>`
- **Non-blocking findings or accepted risks:** `<none or list>`
- **Completed:** `<YYYY-MM-DD or pending>`
