# Security Policy

## Supported versions

The latest release of each platform is supported. Security fixes are made on
the `main` branch and shipped in a new release; the Mac app has no automatic
updater, so install the latest download from GitHub Releases.

## Reporting a vulnerability

Please report vulnerabilities privately through GitHub's
**Report a vulnerability** form on this repository's
[Security page](../../security/advisories/new). Do not open a public issue for a
security problem.

Include what you found, the affected component (macOS app, macOS helper, iOS app,
or synchronization), the Posato version or commit, and steps to reproduce using
synthetic values such as `example.com`. Do not include your own website lists,
app names, account or device identifiers, keys, screenshots, or logs.

Reports are handled on a best-effort basis by the maintainer. You will receive an
acknowledgement when the report is reviewed, and fixes are coordinated before
public disclosure.

## Scope

In scope are weaknesses in Posato's code and design, for example:

- reading or changing another person's synchronized data;
- bypassing the encryption or authentication of synchronized operations;
- abusing the macOS helper or its privileged daemon beyond their accepted
  operations;
- leaking policy, session, or identity data through logs, files, or processes.

The following are accepted limits of Posato and are not vulnerabilities by
themselves:

- a device administrator, root user, debugger, or compromised operating system
  can bypass enforcement, read local data, or remove Posato;
- ending a session early or removing Posato is intentionally possible;
- browsers or apps other than Safari and Chrome Stable, non-standard ports,
  clients that ignore system proxy settings, visiting a site by IP address, or
  iCloud Private Relay are not blocked on macOS;
- Apple can observe the metadata needed to operate iCloud;
- an Apple Account or iCloud Keychain compromise gives access to the workspace.

See the [threat model](docs/security/apple-mvp-threat-model.md) for the full list
of accepted residual risks.
