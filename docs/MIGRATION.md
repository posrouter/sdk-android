# Migration guide

## 1.6.3 — Lensing connection API rename (first spec-aligned release)

SDK **1.6.3** implements **Lensing Protocol V1.6** and uses spec-aligned versioning (`1.6.{patch}`). See [VERSIONING.md](VERSIONING.md).

This release renames public Lensing status types from NATS-oriented names to **Lensing** terminology. This is a **breaking change** for partners on **≤ 1.0.2** — replacing the AAR alone is not enough; update your app code.

### Rename map

| Removed (≤ 1.0.2) | Use instead (≥ 1.6.3) |
|---|---|
| `NatsConnectionState` | `LensingConnectionState` |
| `onNatsStateChanged(state)` | `onLensingStateChanged(state)` |
| `POSRouter.currentNatsState()` | `POSRouter.currentLensingState()` |

Unchanged: `POSRouter.initialize()`, `connect()`, `pay()`, `refund()`, `setTerminalListener()`, and other callbacks on `POSRouterTerminalListener`.

### Upgrade steps

1. Replace `posrouter-release.aar` **or** bump Maven dependency to **`1.6.3+`**.
2. Project-wide search-and-replace the three symbols above.
3. Rebuild and smoke-test: initialize → wait for green indicator → pay / void on remote route.

### New — indicator colors

Use SDK-provided colors so status dots match POSRouter reference apps:

```kotlin
// Current state color (default arg = currentLensingState())
val argb = POSRouter.lensingIndicatorColor()

// Or explicitly:
val argb = state.indicatorColorArgb()
// LensingConnectionIndicator.colorArgb(state)  // Java static access
```

| State | Color | Hex |
|---|---|---|
| `CONNECTED` | Green | `#22C55E` |
| `DISCOVERING`, `CONNECTING`, `RECONNECTING` | Amber | `#F59E0B` |
| `FAILED` | Red | `#EF4444` |
| `OFFLINE` | Slate | `#94A3B8` |

Constants: `LensingConnectionIndicator.COLOR_CONNECTED`, `COLOR_CONNECTING`, `COLOR_FAILED`, `COLOR_OFFLINE`.

### Before / after (Kotlin)

```kotlin
// Before
override fun onNatsStateChanged(state: NatsConnectionState) {
    if (state == NatsConnectionState.CONNECTED) enablePay()
}
if (POSRouter.currentNatsState() != NatsConnectionState.CONNECTED) return

// After
override fun onLensingStateChanged(state: LensingConnectionState) {
    dot.background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(POSRouter.lensingIndicatorColor(state))
    }
    if (state == LensingConnectionState.CONNECTED) enablePay()
}
if (POSRouter.currentLensingState() != LensingConnectionState.CONNECTED) return
```

### Java

```java
POSRouterTerminalListener listener = new POSRouterTerminalListener() {
    @Override
    public void onLensingStateChanged(LensingConnectionState state) {
        int color = LensingConnectionIndicator.colorArgb(state);
        // apply to View...
    }
};
LensingConnectionState current = POSRouter.INSTANCE.currentLensingState();
```

### If you never used status APIs

If your app only calls `initialize()` / `pay()` and never referenced `NatsConnectionState`, you may compile after AAR swap with no code changes — but we recommend adding `onLensingStateChanged` for production UX.

See also: [integration-lensing-status.md](integration-lensing-status.md).
