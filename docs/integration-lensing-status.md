# Lensing connection status (integration guide)

Monitor the **Lensing / Gateway network track** — not local Ezypos pairing. Use this for status dots, disabling remote pay, and diagnostics.

Reference implementations: **demo-android** (`MainActivity.kt`) and **posrouter-kiosk** (`KioskActivity.kt`).

## Quick start

```kotlin
class MainActivity : AppCompatActivity() {

    private val terminalListener = object : POSRouterTerminalListener {
        override fun onLensingStateChanged(state: LensingConnectionState) {
            updateLensingDot(state)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        POSRouter.initialize(this, config)

        // Bind views first, then register listener (immediate callback with current state)
        lensingDot = findViewById(R.id.lensingDot)
        POSRouter.setTerminalListener(terminalListener)
    }

    override fun onDestroy() {
        POSRouter.setTerminalListener(null)
        super.onDestroy()
    }

    private fun updateLensingDot(state: LensingConnectionState) {
        lensingDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(POSRouter.lensingIndicatorColor(state))
        }
    }
}
```

## API summary

| API | Purpose |
|---|---|
| `POSRouter.setTerminalListener(listener)` | Push updates on the **main thread**; fires once immediately with current state |
| `POSRouter.currentLensingState()` | Pull current state (button clicks, `onResume`) |
| `POSRouter.lensingIndicatorColor(state?)` | Canonical ARGB dot color (default = current state) |
| `POSRouter.reconnectLensing()` | Force Gateway discovery + new NATS session |
| `POSRouter.refreshLensingConnection(backgroundMs)` | Auto-reconnect after long idle or dead socket |
| `LensingConnectionState.indicatorColorArgb()` | Same color, on the enum |
| `LensingConnectionIndicator.colorArgb(state)` | Java-friendly static accessor |

## States

| `LensingConnectionState` | Meaning | Indicator |
|---|---|---|
| `OFFLINE` | Not initialized / idle | Slate `#94A3B8` |
| `DISCOVERING` | Fetching NATS credentials from Gateway | Amber `#F59E0B` |
| `CONNECTING` | Opening NATS session | Amber |
| `CONNECTED` | Ready for remote pay / void / refund | Green `#22C55E` |
| `RECONNECTING` | Brief disconnect; client auto-reconnecting | Amber |
| `FAILED` | Gateway discovery failed (check participant key / network) | Red `#EF4444` |

UI tip: group `DISCOVERING`, `CONNECTING`, and `RECONNECTING` as one “Connecting…” label with the amber dot.

## Gate remote operations

```kotlin
if (POSRouter.currentLensingState() != LensingConnectionState.CONNECTED) {
    showOfflineMessage()
    return
}
POSRouter.pay(activity, request, callback)
```

## vs `connect()`

| | Lensing state | `POSRouter.connect()` |
|---|---|---|
| Tracks | NATS / Gateway session | Routing ready (local acquirer **or** network) |
| Use for | Status indicator | Setup flow after changing terminal / merchant |

If NATS is still connecting when you call `connect()`, the SDK **queues** the callback until `CONNECTED` (`PendingConnectRegistry`).

## Lifecycle best practices

1. Call `initialize()` when config changes (terminal, merchant, participant key) — same config repeats are skipped internally.
2. Register `setTerminalListener` after view binding; clear in `onDestroy`.
3. On `onResume`, call `POSRouter.refreshLensingConnection(backgroundMs)` after long background (see demo / kiosk `onPause` + `SystemClock.elapsedRealtime()`).
4. Provide a manual **Reconnect Lensing** action via `POSRouter.reconnectLensing()` when the UI is stuck Connecting.
5. Debounce rapid state changes (~300 ms) if the dot flickers during network handover.
6. Skip UI updates when `isFinishing || isDestroyed`.

## Terminal side (kiosk / device B)

Same listener works on the payment terminal. Also implement:

- `onRemotePaymentReceived` — incoming pay from ordering app
- `onPaymentCompleted` — local acquirer finished
- `onRemotePaymentVoided` — initiator voided in-flight pay

## Migration from ≤ 1.0.2

See [MIGRATION.md](MIGRATION.md) for `NatsConnectionState` → `LensingConnectionState` rename. Upgrade target: **1.6.5+**.
