# Sideband Android SDK

Add Sideband Pulse prompts and event tracking to your Android app.

## Install

Add the SDK dependency:

```kotlin
dependencies {
    implementation("com.sideband:sideband-android:1.2.0")
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


## Requirements

- Android `minSdk` 24+
- A Sideband API key

The SDK includes the required `INTERNET` permission in its manifest.
