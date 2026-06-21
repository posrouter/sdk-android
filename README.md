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
