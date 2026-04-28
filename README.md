# Sideband Android SDK

Add Sideband Pulse prompts and event tracking to your Android app.

## Install

Add the SDK dependency:

```kotlin
dependencies {
    implementation("com.sideband:sideband-android:0.1.0")
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

Wrap your Compose app with `SidebandPulseHost`:

```kotlin
import com.sideband.sdk.ui.SidebandPulseHost

SidebandPulseHost {
    AppContent()
}
```

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
- Jetpack Compose UI
- A Sideband API key

The SDK includes the required `INTERNET` permission in its manifest.
