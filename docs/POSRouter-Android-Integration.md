# POSRouter Android SDK — Integration Guide

This guide is for developers integrating the **POSRouter Android SDK** into an Android app
to take card payments through a POSRouter terminal.

Your app is the **initiator (A-side)**: it starts a payment and receives the result. The
**terminal** (a separate POSRouter device) performs the actual card transaction. Your app
never touches card data or payment hardware.

---

## 1. What you receive from us

Before you start, we (POSRouter) issue you the following. Keep the key secret — it
authenticates your app to the payment network.

| Value | Example | Notes |
|---|---|---|
| `participantCode` | `ACME` | Your caller identity (we issue you your own). |
| `participantKey` | *(secret)* | HMAC secret for the Gateway handshake. **Never commit it or ship it in plaintext where it can be extracted.** |
| `acquirerCode` | `SUPY` | The acquirer "rail" your payments run on. |
| `terminalId` / `merchantId` | `TID001` / `M-1001` | Identify the lane (which terminal + merchant) you transact against. |
| `currency` | `NZD` | ISO-4217 code. |
| `gatewayBaseUrl` | `https://gateway.posrouter.com` | Usually the default; we tell you if you need a different one. |
| SDK version | `1.6.x` | Pin to the exact version we give you. |

> **On native Android the key is embedded in your app** (passed to `initialize`), so it can
> be extracted from the APK. Treat it accordingly — see §14.

---

## 2. Requirements

- **minSdk 24** (Android 7.0) or higher.
- Kotlin or Java. Examples below are Kotlin.

## 3. Install the SDK (Gradle via JitPack)

The SDK is distributed through **JitPack** from the public `posrouter/sdk-android` repo —
**no account, token, or manual file needed.**

Add the JitPack repository:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency (pin the exact version we give you):

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.posrouter:sdk-android:1.6.5")
}
```

The published POM pulls in `jnats` and `kotlinx-coroutines` transitively — no need to
declare them. The first build of a new version compiles on JitPack's servers and may take a
minute; after that it's cached.

### Offline alternative — AAR

If your build can't reach JitPack, ask us for `posrouter-release.aar`, drop it in `libs/`,
and declare the runtime deps yourself:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(files("libs/posrouter-release.aar"))
    implementation("io.nats:jnats:<version>")                                    // versions we give you
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:<version>")
}
```

```kotlin
import com.posrouter.POSRouter
```

---

## 4. Initialize (once, at app start)

```kotlin
POSRouter.initialize(
    context,
    POSRouterConfig(
        participantCode = "ACME",              // from us
        participantKey  = "<your-secret-key>", // from us — keep secret
        terminalId      = "TID001",
        acquirerCode    = "SUPY",              // from us
        merchantId      = "M-1001",
        callbackUrl     = "yourapp://pay_result", // your app's scheme (local track only)
        currency        = "NZD"
        // gatewayBaseUrl = "https://gateway.posrouter.com"  // only if we tell you to override
    )
)
```

`initialize` starts the connection in the background. Call it once (e.g. in `Application`).
Calling it again with a new config re-initializes.

## 5. Connect a lane

`connect` confirms the terminal + merchant lane is reachable before you take a payment. It
needs an `Activity` (the SDK may launch a same-device acquirer).

```kotlin
POSRouter.connect(activity, object : POSRouterCallback {
    override fun onResult(result: PaymentResult) { /* lane ready */ }
    override fun onError(error: POSRouterError) {
        Log.w(TAG, "connect failed: ${error.code} ${error.message}")
    }
})
```

## 6. Take a payment

Amounts are **integer minor units** (cents): `$12.50` → `1250`.

```kotlin
POSRouter.pay(
    activity,
    PaymentRequest(
        terminalId = "TID001",
        amount     = 1250,          // $12.50
        orderId    = "ORDER-9",     // your unique order id — must not be blank
        remark     = "Table 4"      // optional
    ),
    object : POSRouterCallback {
        override fun onResult(result: PaymentResult) {
            when (result.status) {
                PaymentStatus.APPROVED  -> { /* result.transactionId, result.amount */ }
                PaymentStatus.DECLINED  -> {}
                PaymentStatus.CANCELLED -> {}   // see result.metadata["cancelReason"]
                PaymentStatus.ERROR     -> {}
            }
        }
        override fun onError(error: POSRouterError) {
            Log.e(TAG, "${error.code} ${error.message}")   // could not send — see §13
        }
    }
)
```

### Amount helper

Convert a decimal string safely — it **throws** on bad input instead of silently becoming
`0` or a rounded value:

```kotlin
val cents = PaymentRequest.amountFromDecimal("12.50")  // 1250
// throws on "abc", "12,50", overflow, and sub-cent precision like "1.005"
```

## 7. Void an in-flight payment

Soft-void a payment you just started (before it settles). Resolves as a `CANCELLED` result
on your pay callback (with `cancelReason = initiator_void`) when the terminal acks.

```kotlin
POSRouter.voidPayment(orderId = "ORDER-9")   // returns false if no such in-flight pay
```

## 8. Refund a settled payment

```kotlin
POSRouter.refund(
    activity,
    RefundRequest(terminalId = "TID001", orderId = "ORDER-9", amount = 500), // $5.00
    object : POSRouterCallback {
        override fun onResult(result: PaymentResult) { /* ... */ }
        override fun onError(error: POSRouterError) { /* ... */ }
    }
)
```

---

## 9. Connection status (for a status indicator)

```kotlin
POSRouter.setTerminalListener(object : POSRouterTerminalListener {
    override fun onLensingStateChanged(state: LensingConnectionState) {
        val color = POSRouter.lensingIndicatorColor(state)   // packed ARGB for a status dot
    }
})

val state = POSRouter.currentLensingState()
```

| State | Meaning | Indicator |
|---|---|---|
| `CONNECTED` | Ready to transact | Green `#22C55E` |
| `DISCOVERING` / `CONNECTING` / `RECONNECTING` | Establishing / recovering | Amber `#F59E0B` |
| `FAILED` | Connection failed | Red `#EF4444` |
| `OFFLINE` | Not initialized / idle | Slate `#94A3B8` |

Only pay when `CONNECTED`.

## 10. Routing (local vs remote terminal)

By default (`AUTO`) the SDK pays the reachable terminal — a co-located acquirer app on the
same device if present, otherwise a remote terminal over the network. Set a preference
globally or per call (values are `RoutePreference` string constants):

```kotlin
POSRouter.setRoutePreference(RoutePreference.REMOTE_ONLY)                 // global, or per-call:
POSRouter.pay(activity, request, callback, routePreference = RoutePreference.REMOTE_ONLY)
```

| Preference | Behaviour |
|---|---|
| `AUTO` *(default)* | local when reachable → remote fallback |
| `REMOTE_ONLY` | always the remote terminal (network) |
| `LOCAL_ONLY` | same-device acquirer only |
| `LOCAL_FIRST` / `REMOTE_FIRST` | try one, fall back to the other |

## 11. Local track only — forward the acquirer callback

**Skip this section if you only use the remote terminal (`REMOTE_ONLY`).**

If the SDK launches a same-device acquirer app (`AUTO` / `LOCAL_*`), it returns to your app
via your `callbackUrl` scheme. Forward that URI to the SDK so your pay callback fires:

```kotlin
// In the Activity that receives the deep link (launchMode singleTop recommended):
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    intent.data?.let { POSRouter.deliverAcquirerCallback(it) }
}
```

Register your `callbackUrl` scheme with an intent-filter on that Activity in your
`AndroidManifest.xml` so the acquirer can return to you.

---

## 12. `PaymentResult` fields

| Field | Type | |
|---|---|---|
| `status` | `PaymentStatus` | `APPROVED` / `DECLINED` / `CANCELLED` / `ERROR` |
| `amount` | `Long` | minor units (cents) |
| `currency` | `String` | ISO-4217 |
| `orderId` / `attemptId` | `String?` | your order id + the SDK's per-try id |
| `transactionId` | `String?` | acquirer transaction reference (on approval) |
| `message` | `String?` | human-readable detail |
| `metadata` | `Map<String,String>` | extra fields, e.g. `cancelReason` (`user_cancel` vs `initiator_void`) |

## 13. Error codes (`POSRouterError.code`)

| Code | Meaning / action |
|---|---|
| `NOT_INITIALIZED` | Call `initialize(context, config)` first, or the engine isn't connected yet. |
| `ALREADY_CLAIMED` | A payment UI is already open for that order. |
| `CONNECTING` | Refund queued until the connection is back; it will retry. |
| `PUBLISH_FAILED` | Could not send to the terminal (transient) — retry when `CONNECTED`. |
| `GATEWAY_ERROR` | Gateway handshake failed — usually a wrong/whitespace key or wrong `gatewayBaseUrl`. |
| `LOCAL_ACQUIRER_UNAVAILABLE` / `LOCAL_KIOSK_UNAVAILABLE` | Local-track only: the acquirer app isn't installed. |
| `CONNECT_FAILED` | The lane could not be established. |

Blank `orderId` or a non-positive `amount` are rejected before send — validate your input.

---

## 14. Good to know

- **Amounts are always integer cents.** Use `amountFromDecimal` to convert strings safely.
- **`orderId` must be unique per payment and non-blank.** It's how results are matched back
  to your request.
- **Confirm outcomes server-side too.** The live result is delivered over a network stream;
  for anything that must never be missed (e.g. reconciliation), also confirm the final
  status through your own backend / an order-status check — a result emitted during a brief
  reconnect is not replayed.
- **The key is embedded in your APK.** Anyone who decompiles a shipped build can read it.
  Ask us for a dedicated `participantCode` (revocable on its own) rather than a shared one,
  and rotate it if a build leaks.

## 15. Support

Contact your POSRouter integration contact for credentials, the exact SDK version, the
`acquirerCode`, and (for the local track) SDK access / the acquirer app details.
