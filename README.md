# POSRouter Android SDK

Pure Kotlin native Android library (`.aar` output ready) for the Lensing Protocol.

## Usage

```kotlin
POSRouter.initialize(context, code = "GPOS", key = "your-participant-key")

POSRouter.pay(
    activity,
    PaymentRequest(
        terminalId = "TID001",
        amount = 1250,
        currency = "USD",
        targetPackageName = "com.ezypos.app"
    ),
    object : POSRouterCallback {
        override fun onResult(result: PaymentResult) { /* ... */ }
        override fun onError(error: POSRouterError) { /* ... */ }
    }
)
```

## Build

```bash
./gradlew :posrouter:assembleRelease
```
