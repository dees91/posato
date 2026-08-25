# Product Identity

## Status

- **Public identity:** Accepted
- **Accepted:** 2026-08-25
- **Provenance:** `user-confirmed`
- **Gate 2:** Complete

This document is the product authority for the accepted public identity. It
does not claim ownership of external resources or accept Apple identifiers
that have not yet been registered.

## Accepted identity

| Field | Contract |
| --- | --- |
| Product name | **Posato** |
| Display name | **Posato** |
| Short fallback | **Posato**; the name is already short, so no abbreviation or alternate spelling is introduced |
| Canonical public domain | `posato.app`; maintainer control confirmed on 2026-08-25 |
| Reverse-DNS root | `app.posato` |
| Historical working name | **Blocker**; retained only in repository history and research provenance |

The public name is **Posato** in every product-facing context unless a later
explicit decision supersedes this contract. **Blocker** must not be used as a
parallel public brand.

`user-confirmed` (2026-08-25): the maintainer purchased `posato.app` and
controls it through a registrar. This records control only; registrar, account,
payment, and renewal details remain outside the repository.

The stable reverse-DNS root is `app.posato`. It deliberately derives from the
controlled canonical domain. A later public rebrand does not by itself require
identifier migration: the project should retain control of `posato.app` and
continue treating existing technical identifiers as durable unless a separate
migration decision is accepted.

## Technical naming contract

New product-owned Apple identifiers follow these logical patterns:

| Component | Pattern |
| --- | --- |
| Platform application | `app.posato.<platform>` |
| Platform helper or extension | `app.posato.<platform>.<role>` |

`<platform>` and `<role>` are stable lowercase identifier segments selected
from the accepted target graph. Gate 4 defines the required applications,
helpers, and extensions; Gate 7 registers their exact bundle identifiers and
the related App Group, Keychain access group, and CloudKit container. This
contract does not pre-register those resources or inherit any PoC identifier.

## Product meaning

The name supports a calm, reflective identity centered on acting without
haste. It describes the state the product helps a person reach rather than the
blocking mechanism or an adversarial relationship with the device.

The selection also preserves the accepted product principles:

- deliberate friction without punishment or surveillance;
- owner-controlled sessions with an intentional exit;
- privacy-preserving behavior without usage tracking; and
- a composed consumer identity that can extend beyond the Apple-first MVP.

## Dated collision screen

`observed` (2026-08-25): the preliminary screen covered normalized exact names
in the US and Polish Apple application storefronts, exact GitHub repository
names, registry RDAP or WHOIS for `.com`, `.app`, `.org`, `.dev`, `.io`, `.pl`,
and `.to`, and a general web check.

- No normalized exact **Posato** Apple application result was found in either
  checked storefront.
- No exact GitHub repository named `posato` was found.
- `posato.com` is registered and offered for development.
- `posato.io` is registered.
- `posato.app`, `posato.org`, `posato.dev`, `posato.pl`, and `posato.to`
  returned no registration object at the time of checking.

This is collision evidence, not trademark clearance, a professional legal
opinion, a registrar guarantee, or proof that an external resource is under
maintainer control. Trademark and release-readiness review remain required
before public launch.

## Public branding versus durable identifiers

The public name, display treatment, logo, colors, copy, and website presentation
are branding. They can change later, although a rebrand has product and user
cost.

Reverse-DNS roots, application and extension bundle identifiers, App Groups,
Keychain access groups, CloudKit containers, helper identifiers, and persisted
protocol identifiers are durable technical identifiers. Their migration is
more expensive and must not be inferred from the public name alone.

## Deferred identity work

Exact Apple resources remain Gate 7 work after the target graph and pull-request
roadmap are accepted. Final logo work, complete visual identity, trademark
review, and public-release readiness are outside Gate 2. No PoC identifier is
inherited by this contract.
