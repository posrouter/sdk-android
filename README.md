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
    // Optional: override Gateway for staging / Vercel Preview
    // gatewayBaseUrl = "https://preview.posrouter.com"
)

POSRouter.initialize(context, config)

// Optional: routing preference (default "auto") — applies to connect, pay, refund
POSRouter.setRoutePreference(RoutePreference.REMOTE_FIRST)
// or pass on connect:
POSRouter.connect(activity, callback, routePreference = RoutePreference.REMOTE_FIRST)

POSRouter.pay(activity, PaymentRequest(...), callback)
```

## Routing (caller-blind)

1. SDK resolves `acquirerCode` via Gateway matrix (`GET /matrix?code=SUPY`) with baked defaults fallback.
2. **Optimistic local launch** — no `resolveActivity` / `getPackageInfo` pre-probe; direct `startActivity` with `setPackage`.
3. Explicit Intent (`LENS_DATA`) first, Deep Link fallback (`ezypos://pay?amount=10|...`).
4. **In-memory cache** per acquirer code: after first success, reuse cached method; if cached as unreachable, skip local and use NATS (unless preference overrides — see below).
5. By default, local failure on pay auto-falls back to NATS.

No `<queries>` manifest entries required for the registry-driven model.

### Route preference

Apps can override the default local-then-remote behaviour at runtime. Values are **strings** (same style as deeplink / JSON wire fields). Use constants from `RoutePreference` or pass literals.

| Value | Constant | `connect` / `pay` / `refund` |
|-------|----------|------------------------------|
| `auto` | `RoutePreference.AUTO` | **Default.** Try local when acquirer not cached unreachable → on failure publish to NATS. |
| `local_first` | `RoutePreference.LOCAL_FIRST` | Always try local first (ignores unreachable cache) → on failure NATS. |
| `remote_first` | `RoutePreference.REMOTE_FIRST` | Skip local; NATS only. |
| `local_only` | `RoutePreference.LOCAL_ONLY` | Local only; `LOCAL_ACQUIRER_UNAVAILABLE` on failure. |
| `remote_only` | `RoutePreference.REMOTE_ONLY` | NATS only; never launch local acquirer. |

**API**

```kotlin
POSRouter.setRoutePreference(RoutePreference.REMOTE_FIRST)   // global for session
POSRouter.getRoutePreference()                               // current value
POSRouter.connect(activity, callback, routePreference = "remote_first")  // optional third arg
```

- Omitted, blank, or unknown strings → `auto` (case-insensitive; `local-first` and `local_first` both work).
- `initialize()` resets preference to `auto`.
- Preference is **SDK runtime state** — not sent on NATS subjects or Level 1 deeplinks.
- `voidPayment()` is always NATS and is not affected.

**Typical scenarios**

| Scenario | Suggested preference |
|----------|---------------------|
| Handheld POS with Ezypos on same device | `auto` or `local_first` |
| Tablet ordering, fixed terminal pays | `remote_first` |
| Gateway / kiosk with no acquirer app | `remote_only` |
| Air-gapped or local-only testing | `local_only` |

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
