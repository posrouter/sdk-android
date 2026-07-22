# SDK versioning

## Spec vs SDK

**Lensing Protocol spec version** (wire subjects, Gateway contract, message shapes) and **Android SDK artifact version** (Maven / AAR) are *usually* tracked separately in most ecosystems:

| | Spec | SDK |
|---|---|---|
| What it describes | Protocol on the wire | Kotlin API + packaging |
| Typical semver | V1.5, V1.6, … | 1.0.0, 1.1.0, 2.0.0, … |
| Breaking change | New subject layout, field renames | Public API rename, behavior change |

That separation is fine and common (HTTP spec vs `okhttp` version, etc.).

## POSRouter policy (from 1.6.x)

We **align the first two semver segments with the Lensing spec** the SDK implements:

```text
{specMajor}.{specMinor}.{sdkPatch}
     1    .   6    .    3
```

| Segment | Meaning |
|---|---|
| **1.6** | Implements **Lensing Protocol V1.6** (subjects, Gateway init, pay/void/result flow) |
| **.3** | SDK-only releases on the same spec (bugfix, reconnect hardening, docs, indicator colors) |

When the spec moves to **V1.7**, the next SDK line starts at **1.7.0** (even if the change is wire-only).

**SDK-only breaking API changes** (rare) bump the **minor** spec segment only if we intentionally ship a new spec line; otherwise document in `MIGRATION.md` and prefer doing API breaks when aligning to the next spec minor.

## Pre-alignment releases

| SDK | Notes |
|---|---|
| **1.0.0 – 1.0.2** | Early partner builds; **not** spec-aligned numbering |
| **1.6.3** | First release under this scheme; includes Lensing status API rename + `lensingIndicatorColor()` |
| **1.6.4** | Local POSRouter Kiosk connect handshake + package-visibility `<queries>`; cancel/result relay hardening for partners |
| **1.6.5** | Patch line for partner TMS / AAR republish |

Partners on **≤ 1.0.2** should treat **1.6.3+** as the upgrade target (see [MIGRATION.md](MIGRATION.md)); current publish line is **1.6.5**. The interim label `1.0.3` was never published.

## Where the version is set

```properties
# sdk-android/gradle.properties
POSROUTER_VERSION=1.6.5
```

Publish:

```bash
./gradlew :posrouter:publishReleasePublicationToGitHubPackagesRepository
```

Partners:

```kotlin
implementation("com.posrouter:posrouter:1.6.5")
```

## Spec reference in code

Implementation comments and docs refer to **V1.6** where wire behavior is spec-defined (e.g. initiator `.result` subject namespace). The SDK version should match that spec line unless `VERSIONING.md` / release notes say otherwise.
