# Third-party notices

Posato's source code is licensed under the [Apache License, Version 2.0](LICENSE).
The macOS and iOS applications are built with the third-party components below.
This list covers runtime components that ship inside the applications; build
and verification tools are listed separately. Binary distributions must carry
the full license texts and notices of the components they include.

The list reflects the dependencies resolved on 2026-09-23 and must be updated
whenever a runtime dependency changes.

## Runtime components

| Component | Used by | License |
| --- | --- | --- |
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization, kotlinx.datetime, kotlinx.collections.immutable, atomicfu (JetBrains) | macOS, iOS | Apache-2.0 |
| Compose Multiplatform runtime, foundation, UI, Material 3, resources, and lifecycle (JetBrains) | macOS, iOS | Apache-2.0 |
| AndroidX lifecycle, navigation event, annotation, and collection libraries (Google) | macOS, iOS | Apache-2.0 |
| Skiko (JetBrains) | macOS, iOS | Apache-2.0 |
| Skia graphics library (Google), bundled in Skiko native libraries | macOS, iOS | BSD-3-Clause |
| SQLDelight runtime and drivers (Cash App) | macOS, iOS | Apache-2.0 |
| SQLite JDBC (Xerial) | macOS | Apache-2.0, with portions under a BSD-style license |
| SQLite, bundled in SQLite JDBC native libraries | macOS | Public domain |
| Metro dependency injection runtime | macOS, iOS | Apache-2.0 |
| Markdown parser (JetBrains) | macOS, iOS | Apache-2.0 |
| kuri | macOS, iOS | MIT |
| JSpecify annotations | macOS | Apache-2.0 |
| JetBrains Runtime API | macOS | Apache-2.0 |
| OpenJDK runtime from Eclipse Temurin 21 (Eclipse Adoptium), bundled in the macOS application | macOS | GPL-2.0 with Classpath Exception |
| Sparkle 2.10.0 update framework, bundled in the macOS application | macOS | MIT, with bundled components under BSD-2-Clause, MIT, and zlib licenses |

## Required attributions

- **Skia:** Copyright (c) 2011 Google Inc. All rights reserved. Distributed under the BSD-3-Clause license.
- **SQLite JDBC:** portions Copyright (c) 2006, David Crawshaw. All rights reserved. Distributed under a BSD-style license.
- **kuri:** Copyright Omar Aljarrah. Distributed under the MIT License.
- **Sparkle:** Copyright (c) 2006-2013 Andy Matuschak; (c) 2009-2013 Elgato Systems GmbH; (c) 2011-2014 Kornel Lesiński; (c) 2015-2017 Mayur Pawashe; (c) 2014 C.W. Betts; (c) 2014 Petroules Corporation; (c) 2014 Big Nerd Ranch. Distributed under the MIT License. It includes bsdiff and bspatch (Copyright 2003-2005 Colin Percival, BSD-2-Clause), sais-lite (Copyright (c) 2008-2010 Yuta Mori, MIT), the portable Ed25519 implementation (Copyright (c) 2015 Orson Peters, zlib), and SUSignatureVerifier (Copyright (c) 2011 Mark Hamlin, BSD-2-Clause).
- **OpenJDK (Eclipse Temurin 21):** the bundled runtime is produced from the Eclipse Temurin 21 JDK and includes its own `legal` notices directory, which must stay in the distributed application.

## Build and verification tools

These tools are used to build or check Posato and are not shipped inside the
applications: Gradle and its wrapper (Apache-2.0), the Kotlin and Compose
Gradle plugins (Apache-2.0), Detekt and ktlint with Compose rules (Apache-2.0
and MIT), Clikt and SLF4J for the verification tools (Apache-2.0 and MIT), and
SwiftLint build plugins (MIT).
