# Third-party notices

Posato's source code is licensed under the [Apache License, Version 2.0](LICENSE).
The macOS and iOS applications are built with the third-party components below.
This list covers runtime components that ship inside the applications; build
and verification tools are listed separately. Binary distributions must carry
the full license texts and notices of the components they include.

The list reflects the dependencies resolved on 2026-09-14 and must be updated
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
| kuri | macOS, iOS | MIT |
| JSpecify annotations | macOS | Apache-2.0 |
| JetBrains Runtime API | macOS | Apache-2.0 |
| OpenJDK runtime, bundled in the macOS application | macOS | GPL-2.0 with Classpath Exception |

## Required attributions

- **Skia:** Copyright (c) 2011 Google Inc. All rights reserved. Distributed under the BSD-3-Clause license.
- **SQLite JDBC:** portions Copyright (c) 2006, David Crawshaw. All rights reserved. Distributed under a BSD-style license.
- **kuri:** Copyright Omar Aljarrah. Distributed under the MIT License.
- **OpenJDK:** the bundled runtime includes its own `legal` notices directory, which must stay in the distributed application.

## Build and verification tools

These tools are used to build or check Posato and are not shipped inside the
applications: Gradle and its wrapper (Apache-2.0), the Kotlin and Compose
Gradle plugins (Apache-2.0), Detekt and ktlint with Compose rules (Apache-2.0
and MIT), Clikt and SLF4J for the verification tools (Apache-2.0 and MIT), and
SwiftLint build plugins (MIT).
