# Third-party notices

Posato's source code is licensed under the [Apache License, Version 2.0](LICENSE).
The macOS and iOS applications are built with the third-party components below.
This list covers runtime components that ship inside the applications; build
and verification tools are listed separately. Binary distributions must carry
the full license texts and notices of the components they include.

The list reflects the dependencies resolved on 2026-09-29 and must be updated
whenever a runtime dependency changes.

## Runtime components

| Component | Used by | License |
| --- | --- | --- |
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization, kotlinx.datetime, kotlinx.collections.immutable, atomicfu (JetBrains) | macOS, iOS | Apache-2.0 |
| Compose Multiplatform runtime, foundation, UI, Material 3, resources, lifecycle, and Navigation 3 (JetBrains) | macOS, iOS | Apache-2.0 |
| AndroidX lifecycle, navigation event, Navigation 3 runtime, saved state, annotation, and collection libraries (Google) | macOS, iOS | Apache-2.0 |
| Skiko (JetBrains) | macOS, iOS | Apache-2.0 |
| Skia graphics library (Google), bundled in Skiko native libraries | macOS, iOS | BSD-3-Clause |
| SQLDelight runtime and drivers (Cash App) | macOS, iOS | Apache-2.0 |
| SQLite JDBC (Xerial) | macOS | Apache-2.0, with portions under a BSD-style license |
| SQLite, bundled in SQLite JDBC native libraries | macOS | Public domain |
| Metro dependency injection runtime | macOS, iOS | Apache-2.0 |
| Markdown parser (JetBrains) | macOS, iOS | Apache-2.0 |
| kuri | macOS, iOS | MIT |
| QRose QR code painter (Alexander Zhirkevich) | macOS, iOS, Linux, Android | MIT |
| dbus-java core and native Unix socket transport (David M. and hypfvieh), 5.2.2 | Linux | MIT |
| SLF4J API (QOS.ch), required by dbus-java | Linux | MIT |
| Java Native Access (JNA) and JNA Platform, 5.19.1, with the bundled `jnidispatch` native library | Windows | Apache-2.0 |
| OpenJDK runtime, bundled in the Windows application by jpackage | Windows | GPL-2.0 with Classpath Exception |
| JSpecify annotations | macOS | Apache-2.0 |
| JetBrains Runtime API | macOS | Apache-2.0 |
| OpenJDK runtime from Eclipse Temurin 21 (Eclipse Adoptium), bundled in the macOS application | macOS | GPL-2.0 with Classpath Exception |
| Sparkle 2.10.0 update framework, bundled in the macOS application | macOS | MIT, with bundled components under BSD-2-Clause, MIT, and zlib licenses |

## Required attributions

- **Skia:** Copyright (c) 2011 Google Inc. All rights reserved. Distributed under the BSD-3-Clause license.
- **SQLite JDBC:** portions Copyright (c) 2006, David Crawshaw. All rights reserved. Distributed under a BSD-style license.
- **kuri:** Copyright Omar Aljarrah. Distributed under the MIT License.
- **QRose:** Copyright (c) 2023 Alexander Zhirkevich. Distributed under the MIT License.
- **dbus-java:** Copyright (c) the dbus-java authors (hypfvieh and contributors). Distributed under the MIT License.
- **JNA:** Copyright (c) Timothy Wall and the JNA contributors. Licensed under Apache-2.0 OR LGPL-2.1-or-later; Posato uses it under the Apache License, Version 2.0.
- **SLF4J:** Copyright (c) 2004-2023 QOS.ch Sarl (Switzerland). Distributed under the MIT License.
- **Sparkle:** Copyright (c) 2006-2013 Andy Matuschak; (c) 2009-2013 Elgato Systems GmbH; (c) 2011-2014 Kornel Lesiński; (c) 2015-2017 Mayur Pawashe; (c) 2014 C.W. Betts; (c) 2014 Petroules Corporation; (c) 2014 Big Nerd Ranch. Distributed under the MIT License. It includes bsdiff and bspatch (Copyright 2003-2005 Colin Percival, BSD-2-Clause), sais-lite (Copyright (c) 2008-2010 Yuta Mori, MIT), the portable Ed25519 implementation (Copyright (c) 2015 Orson Peters, zlib), and SUSignatureVerifier (Copyright (c) 2011 Mark Hamlin, BSD-2-Clause).
- **OpenJDK (Eclipse Temurin 21):** the bundled runtime is produced from the Eclipse Temurin 21 JDK and includes its own `legal` notices directory, which must stay in the distributed application.

## Website and README artwork

The download badges on posato.app and in the README are not shipped inside
the applications. Their text is outlined from the Inter typeface (Rasmus
Andersson, SIL Open Font License 1.1). The Linux and Android marks come from
Simple Icons (CC0-1.0 path data): Tux was drawn by Larry Ewing with The GIMP,
and the Android robot is reproduced or modified from work created and shared
by Google and used according to terms described in the Creative Commons 3.0
Attribution License. Linux is a registered trademark of Linus Torvalds;
Android is a trademark of Google LLC; Apple, Mac, and App Store are
trademarks of Apple Inc.

## Build and verification tools

These tools are used to build or check Posato and are not shipped inside the
applications: Gradle and its wrapper (Apache-2.0), the Kotlin and Compose
Gradle plugins (Apache-2.0), Detekt and ktlint with Compose rules (Apache-2.0
and MIT), Clikt and SLF4J for the verification tools (Apache-2.0 and MIT), and
SwiftLint build plugins (MIT).
