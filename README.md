# Sideband Android SDK

Add Sideband Pulse prompts and event tracking to your Android app.

## Install

Add the SDK dependency:

```kotlin
dependencies {
    implementation("com.sideband:sideband-android:1.2.0")
}
```

HTML-wrapper apps that track with Google Tag Manager also add:

```kotlin
implementation("com.sideband:sideband-android-webview:1.2.0")
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

## Configure

Configure Sideband once when your app starts:

```kotlin
import com.sideband.sdk.Sideband
import com.sideband.sdk.core.ClientConfiguration

Sideband.configure(
    ClientConfiguration(
        apiKey = "YOUR_API_KEY",
        appVersion = BuildConfig.VERSION_NAME,
    )
)
```

## Show Pulses

### Compose Apps

Wrap your Compose app with `SidebandPulseHost`:

```kotlin
import com.sideband.sdk.ui.SidebandPulseHost

SidebandPulseHost {
    AppContent()
}
```

### Android Views Apps

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

The Views API uses Sideband's Compose overlay internally, but host apps do not need to write Compose UI code.
When installing into a specific root view, pass a `FrameLayout` so the overlay can position the FAB and sheet without blocking host content.

## Track Events

Identify users and track events directly from your app code:

```kotlin
Sideband.tagUser("user_123")

Sideband.track(
    eventName = "checkout_started",
    metadata = mapOf("plan" to "pro"),
)
```

Sideband calls are fire-and-forget. Events are batched and synced in the background.

## HTML wrapper apps (Google Tag Manager)

If product logic lives in a `WebView`, keep the API key and pulse UI native. Web and GTM only call `window.Sideband`. The same GTM container works on the iOS wrapper.

The bridge is **not** inside `com.sideband:sideband-android`. It ships on the same public release as a Maven artifact and as source under `Sources/SidebandWebView/`. Add both artifacts at the same version:

```kotlin
dependencies {
    implementation("com.sideband:sideband-android:1.2.0")
    implementation("com.sideband:sideband-android-webview:1.2.0")
}
```

**Native (once).** Configure Sideband, enable JavaScript, attach the bridge before the WebView's first `load`, and install the pulse overlay. The bridge does not enable JavaScript.

```kotlin
import com.sideband.sdk.Sideband
import com.sideband.sdk.core.ClientConfiguration
import com.sideband.sdk.ui.SidebandPulseOverlayView
import com.sideband.sdk.webview.installWebViewBridge

Sideband.configure(
    ClientConfiguration(
        apiKey = "YOUR_API_KEY",
        appVersion = BuildConfig.VERSION_NAME,
    )
)
webView.settings.javaScriptEnabled = true
Sideband.installWebViewBridge(webView)
SidebandPulseOverlayView.install(this)
```

Pulses still present in the native overlay, over the WebView.

**GTM (no app release for new events).** Add Custom HTML tags. `{{Event}}` and `{{User ID}}` are GTM variables you already have or create (Built-In Event, Data Layer variable).

Track — trigger All Custom Events (or the events Sideband should target). Skip built-in `gtm.*` events:

```html
<script>
  (function () {
    var name = {{Event}};
    if (!name || String(name).indexOf('gtm.') === 0) return;
    if (window.Sideband) window.Sideband.track(name);
  })();
</script>
```

To pass properties, add GTM Data Layer variables and a second argument:

```html
<script>
  if (window.Sideband) {
    window.Sideband.track('{{Event}}', {
      value: '{{Value}}'
    });
  }
</script>
```

Identify — Custom HTML tag on login / container load when a user ID is available:

```html
<script>
  if (window.Sideband && '{{User ID}}') {
    window.Sideband.tagUser('{{User ID}}');
  }
</script>
```

On logout, call `Sideband.untagUser()` from native, or `window.Sideband.untagUser()` from a GTM tag.

To customize handler name, injected JavaScript, or payload parsing, copy `Sources/SidebandWebView/` into the host app and stop depending on `sideband-android-webview`.

## Requirements

- Android `minSdk` 24+
- A Sideband API key

The SDK includes the required `INTERNET` permission in its manifest.
