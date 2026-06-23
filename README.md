# POSRouter Android SDK

Pure Kotlin native Android library for the Lensing Protocol. Callers only need their own code (GPOS) and the partner registry code (SUPY).

## Usage

```kotlin
val config = POSRouterConfig(
    participantCode = "GPOS",
    participantKey = "your-participant-key",
    terminalId = "TID001",
    acquirerCode = "SUPY",
    merchantId = "abc123",
    callbackUrl = "gomenu://pay_result",
    currency = "NZD"
)

POSRouter.initialize(context, config)
POSRouter.connect(activity, callback)
POSRouter.pay(activity, PaymentRequest(...), callback)
```

## Routing (caller-blind)

1. SDK resolves `acquirerCode` via Gateway matrix (`GET /matrix?code=SUPY`) with baked defaults fallback.
2. **Optimistic local launch** — no `resolveActivity` / `getPackageInfo` pre-probe; direct `startActivity` with `setPackage`.
3. Explicit Intent (`LENS_DATA`) first, Deep Link fallback (`ezypos://pay?amount=10|...`).
4. **In-memory cache** per acquirer code: after first success, reuse cached method; if cached as unreachable, skip local and use NATS.
5. Local failure on pay auto-falls back to NATS.

No `<queries>` manifest entries required for the registry-driven model.

## Build

```bash
./gradlew :posrouter:assembleRelease
```

Demo: `../demo-android`

## Publish to Maven (GitHub Packages)

Artifacts: `com.posrouter:posrouter:<version>` (AAR + POM with transitive deps).

### 1. Credentials

Copy `local.properties.example` → `local.properties` and set:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=ghp_...   # PAT with write:packages (and read:packages)
```

Or export env vars: `GITHUB_ACTOR`, `GITHUB_TOKEN`.

### 2. Set version

In `gradle.properties`:

```properties
POSROUTER_VERSION=1.0.0
```

### 3. Publish

```bash
./gradlew :posrouter:publishReleasePublicationToGitHubPackagesRepository
```

Local smoke test without remote upload:

```bash
./gradlew :posrouter:publishReleasePublicationToMavenLocal
# → ~/.m2/repository/com/posrouter/posrouter/1.0.0/
```

### 4. Partner app dependency

In `settings.gradle.kts` repositories:

```kotlin
maven {
    url = uri("https://maven.pkg.github.com/posrouter/sdk-android")
    credentials {
        username = providers.gradleProperty("gpr.user").get()
        password = providers.gradleProperty("gpr.key").get()
    }
}
```

In `app/build.gradle.kts`:

```kotlin
implementation("com.posrouter:posrouter:1.0.0")
```

Partners need a GitHub PAT with `read:packages` and access to the `posrouter/sdk-android` repo (or org package permissions).

**Alternatives:** Maven Central (public, requires signing + Sonatype onboarding) or a private Nexus/Artifactory if you outgrow GitHub Packages.
