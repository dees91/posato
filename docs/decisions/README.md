# Architecture Decisions

This directory contains accepted product architecture decision records.

The synchronization PoC used a scope-specific ADR, but that experiment decision
does not automatically become a production decision. Relevant evidence and
constraints are synthesized in the wiki. A new ADR belongs here only after the
maintainer explicitly accepts it for the MVP.

Numbering continues the feasibility decision history without copying the
feasibility ADR into this repository. ADR 0001 remains historical source
evidence at the revision recorded in the wiki. The first product ADR is:

- [ADR 0002: Separate Synchronization Trust and Workspace Modes](0002-synchronization-trust-and-workspace-modes.md)
  — accepted Apple and portable transport, encryption, key-delivery, device
  admission, and migration boundaries.
