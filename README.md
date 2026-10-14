# Sideband Android SDK

Sideband adds in-app Pulse prompts and event tracking to Android apps.

## Get Started

### Requirements

- Android `minSdk` 24+
- A Sideband API key

The SDK includes the required `INTERNET` permission in its manifest.

### Install

Add the SDK dependency:

```kotlin
dependencies {
    implementation("ai.sideband:sideband-android:1.4.0")
}
```

Resolve the SDK from Maven Central:

```kotlin
repositories {
    google()
    mavenCentral()
}
```

Version `1.3.0` moves the Maven group from `com.sideband` to `ai.sideband` and the Kotlin API from `com.sideband.sdk` to `ai.sideband.sdk`. No compatibility API for the old namespace is included.

### Configure

Configure Sideband once when your app starts:

```kotlin
import ai.sideband.sdk.Sideband
import ai.sideband.sdk.core.ClientConfiguration

Sideband.configure(
    ClientConfiguration(
        apiKey = "YOUR_API_KEY",
    )
)
```

### Present Pulses

#### Compose

Wrap your Compose app with `SidebandPulseHost`:

```kotlin
import ai.sideband.sdk.ui.SidebandPulseHost

SidebandPulseHost {
    AppContent()
}
```

#### Android Views

For Activity, Fragment, or XML/View apps, install the overlay into your View hierarchy:

```kotlin
import ai.sideband.sdk.ui.SidebandPulseOverlayView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        SidebandPulseOverlayView.install(this)
    }
}
```

Fragments can install into a top-level `FrameLayout` overlay container:

```kotlin
val root = requireView().findViewById<FrameLayout>(R.id.root_container)
SidebandPulseOverlayView.install(root)
```

The Views API uses Sideband's Compose overlay internally, but host apps do not need to write Compose UI code. When installing into a specific root view, pass a `FrameLayout` so the overlay can position the FAB and sheet without blocking host content.

### Identify Users and Track Events

Use a stable host-app user identifier, then track the events Sideband should use for targeting:

```kotlin
Sideband.tagUser("user-123")

Sideband.track(
    eventName = "checkout_started",
    metadata = mapOf("plan" to "pro"),
)
```

Tagged user IDs may contain at most 256 characters. Sideband retains a valid tagged user ID until you replace it or clear it on logout. An overlong ID is rejected without replacing the in-memory or persisted identity and without scheduling a sync; an overlong ID persisted by an earlier SDK version is cleared when restored:

```kotlin
Sideband.untagUser()
```

Sideband calls are fire-and-forget. Events are batched and synced in the background.

### Event Metadata and Pulse Targeting

Pass metadata to `track` as string key-value pairs. Use metadata to describe an
event without creating a separate event name for each source, item type, or mode.
Only these six keys support Pulse trigger filters and event-history conditions:

| Key | Value format | Example |
| --- | --- | --- |
| `source` | String | `"home"` |
| `item_type` | String | `"article"` |
| `mode` | String | `"manual"` |
| `variant` | String | `"control"` |
| `count` | Integer sent as a string | `"3"` |
| `duration` | Seconds sent as a string; fractions allowed | `"12.5"` |

Use the exact lowercase key names. The server trims and lowercases values for
`source`, `item_type`, `mode`, and `variant`. These keys support equals and
not-equals comparisons. It compares `count` and `duration` numerically, with
equals, not-equals, greater-than, greater-than-or-equal, less-than, and
less-than-or-equal comparisons. Send `duration` in seconds, not milliseconds.

```kotlin
Sideband.track(
    eventName = "content_created",
    metadata = mapOf(
        "source" to "editor",
        "item_type" to "article",
        "count" to "3",
        "duration" to "12.5",
        "content_id" to "article-123",
    ),
)
```

Custom keys such as `content_id`, `screen`, or `plan` are stored unchanged, but
cannot be used in Pulse targeting filters. A missing key does not match any
filter, including not-equals.

The server stores at most 20 metadata properties per event. It keeps valid
targeting keys first, then fills the remaining slots with custom keys in sorted
key order. It drops empty keys and invalid targeting values without rejecting
the event. For example, `count: "3.5"` and `count: "3.0"` are invalid; use
`count: "3"`. Normalization and numeric comparison happen on the server, not in
the SDK. These rules also apply to events sent through the WebView/GTM bridge.

### Control Pulse Presentation

Use a delegate when you need to decide whether a pulse should appear now:

```kotlin
import ai.sideband.sdk.core.ClientDelegate
import ai.sideband.sdk.models.PendingPulse
import ai.sideband.sdk.models.PulsePresentationDecision

class PulseGate : ClientDelegate {
    override suspend fun clientShouldPresentPulse(
        pulse: PendingPulse,
    ): PulsePresentationDecision {
        return PulsePresentationDecision.SHOW_NOW
    }
}
```

Configure Sideband with the delegate:

```kotlin
Sideband.configure(
    configuration = ClientConfiguration(apiKey = "YOUR_API_KEY"),
    delegate = PulseGate(),
)
```

Return `SHOW_NOW` to present immediately, `NOT_NOW` to skip this opportunity, or `NEVER` to decline the pulse permanently.

## WebView Integration

Use the Sideband WebView bridge when your app loads product content in a `WebView`. The bridge exposes `window.Sideband` to the page while the API key, event delivery, and pulse UI remain native.

### Add WebView Support

Add the WebView artifact at the same version as the core SDK:

```kotlin
dependencies {
    implementation("ai.sideband:sideband-android:1.4.0")
    implementation("ai.sideband:sideband-android-webview:1.4.0")
}
```

The bridge is not part of `ai.sideband:sideband-android`. It ships as a Maven artifact and as source under `Sources/SidebandWebView/` on the same public release.

### Install the Bridge and Pulse Overlay

Configure Sideband, enable JavaScript, install the bridge before the WebView's first `load`, and install the native pulse overlay. Installation requires a non-empty allowlist of exact `http` or `https` origins. The bridge does not enable JavaScript.

```kotlin
import ai.sideband.sdk.Sideband
import ai.sideband.sdk.core.ClientConfiguration
import ai.sideband.sdk.ui.SidebandPulseOverlayView
import ai.sideband.sdk.webview.installWebViewBridge

Sideband.configure(
    ClientConfiguration(
        apiKey = "YOUR_API_KEY",
    )
)
webView.settings.javaScriptEnabled = true
Sideband.installWebViewBridge(
    webView = webView,
    allowedOrigins = setOf(
        "https://app.example.com",
        "https://staging.example.com:8443",
    ),
)
SidebandPulseOverlayView.install(this)
```

Origins must include the scheme and may include a port. Wildcards, credentials, non-root paths, queries, and fragments are rejected. Install and uninstall the bridge on the main thread. The bridge requires a current Android System WebView with AndroidX WebKit web-message-listener and persistent document-start-script support; installation returns `null` and installs nothing when either feature is unavailable. Messages from iframes and origins outside the allowlist are rejected natively.

Pulses appear in the native overlay above the WebView.

### Use the JavaScript API

After the bridge is installed, code in the page can identify users and track events:

```javascript
window.Sideband.tagUser("user-123")
window.Sideband.track("checkout_started", { plan: "pro" })
```

Clear the identity when the user logs out:

```javascript
window.Sideband.untagUser()
```

Metadata scalar values are converted to strings. Nested values are ignored.

To customize the handler name, injected JavaScript, or payload parsing, copy `Sources/SidebandWebView/` into the host app and stop depending on `sideband-android-webview`.

## Google Tag Manager

Complete the WebView integration first. Google Tag Manager can then call the same `window.Sideband` API without requiring a new app release for each tracked event.

### Forward Events

In Google Tag Manager, create a Custom HTML tag triggered by All Custom Events, or only by the events Sideband should receive. Skip GTM's built-in `gtm.*` events.

```html
<script>
  (function () {
    var name = {{Event}};
    if (!name || String(name).indexOf('gtm.') === 0) return;
    if (window.Sideband) window.Sideband.track(name);
  })();
</script>
```

`{{Event}}` is GTM's built-in Event variable.

### Forward Event Metadata

Create GTM Data Layer variables for the properties you want to send, then pass them as the second argument. Metadata scalar values are sent to Sideband as strings.

```html
<script>
  if (window.Sideband) {
    window.Sideband.track('{{Event}}', {
      value: '{{Value}}'
    });
  }
</script>
```

`{{Value}}` is a Data Layer variable supplied by the host app.

### Identify Users

Create a Custom HTML tag that runs on login, or on container load when a stable user ID is available:

```html
<script>
  if (window.Sideband && '{{User ID}}') {
    window.Sideband.tagUser('{{User ID}}');
  }
</script>
```

`{{User ID}}` is a Data Layer variable supplied by the host app. On logout, call `Sideband.untagUser()` from native code or `window.Sideband.untagUser()` from a GTM tag.
