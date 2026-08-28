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
    implementation("com.sideband:sideband-android:1.2.1-rc.3")
}
```

If you install from GitHub Packages, add the repository:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/SidebandAI/sideband-android")
        credentials {
            username = findProperty("gpr.user") as String?
            password = findProperty("gpr.token") as String?
        }
    }
    google()
    mavenCentral()
}
```

Add GitHub Package credentials to `~/.gradle/gradle.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.token=YOUR_GITHUB_TOKEN
```

### Configure

Configure Sideband once when your app starts:

```kotlin
import com.sideband.sdk.Sideband
import com.sideband.sdk.core.ClientConfiguration

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
import com.sideband.sdk.ui.SidebandPulseHost

SidebandPulseHost {
    AppContent()
}
```

#### Android Views

For Activity, Fragment, or XML/View apps, install the overlay into your View hierarchy:

```kotlin
import com.sideband.sdk.ui.SidebandPulseOverlayView

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

### Control Pulse Presentation

Use a delegate when you need to decide whether a pulse should appear now:

```kotlin
import com.sideband.sdk.core.ClientDelegate
import com.sideband.sdk.models.PendingPulse
import com.sideband.sdk.models.PulsePresentationDecision

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
    implementation("com.sideband:sideband-android:1.2.1-rc.3")
    implementation("com.sideband:sideband-android-webview:1.2.1-rc.3")
}
```

The bridge is not part of `com.sideband:sideband-android`. It ships as a Maven artifact and as source under `Sources/SidebandWebView/` on the same public release.

### Install the Bridge and Pulse Overlay

Configure Sideband, enable JavaScript, install the bridge before the WebView's first `load`, and install the native pulse overlay. Installation requires a non-empty allowlist of exact `http` or `https` origins. The bridge does not enable JavaScript.

```kotlin
import com.sideband.sdk.Sideband
import com.sideband.sdk.core.ClientConfiguration
import com.sideband.sdk.ui.SidebandPulseOverlayView
import com.sideband.sdk.webview.installWebViewBridge

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
