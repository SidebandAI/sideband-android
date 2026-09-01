// Default WebView bridge for HTML-wrapper apps that track with Google Tag Manager.
//
// This module is not part of ai.sideband:sideband-android. It ships as source in the public GitHub repo
// (Sources/SidebandWebView). Use sideband-android-webview as-is, or copy these files into the host app and
// edit them. Keep this file and SidebandWebViewBridgeMessage.kt together.
//
// Customization points:
// - handlerName: WebMessageListener name and window.<name>
// - javaScriptSource: injected window.Sideband API (tagUser / untagUser / track)
// - WebViewBridgeMessage.parse: accepted payload shape and metadata coercion
// - apply(_:): routing into Sideband.tagUser / untagUser / track
//
// The GTM Custom HTML tags in the README must keep using the same window.Sideband methods.

package ai.sideband.sdk.webview

import android.net.Uri
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import androidx.annotation.UiThread
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import ai.sideband.sdk.Sideband
import java.lang.ref.WeakReference
import java.net.URI
import java.net.URISyntaxException
import java.util.WeakHashMap

private const val TAG = "Sideband"

/**
 * Handle for a WebView JavaScript bridge installation.
 */
public class SidebandWebViewBridgeInstallation internal constructor(
    private val uninstallHandler: () -> Unit,
) {
    /**
     * Removes the web message listener and persistent document-start script from the WebView.
     *
     * An already injected `window.Sideband` object remains inert until the WebView navigates or is rebuilt.
     */
    @UiThread
    public fun uninstall() {
        uninstallHandler()
    }
}

/**
 * Installs a JavaScript bridge that forwards `window.Sideband` calls from a [WebView] into the shared client.
 *
 * Call [Sideband.configure] before installing the bridge, and call this before the WebView's first `load` so the
 * bridge is present when Google Tag Manager starts. If an allowed page is already loaded, the SDK also injects
 * `window.Sideband` immediately.
 *
 * Enable JavaScript on the WebView (`webView.settings.javaScriptEnabled = true`). The bridge does not enable it.
 * The installed WebView implementation must support AndroidX WebKit's web message listener and document-start
 * script features. Install and uninstall the bridge on the main thread. Calls are accepted only from an allowed
 * origin's main frame.
 *
 * @param webView Host [WebView] that loads the HTML wrapper.
 * @param allowedOrigins Non-empty allowlist of exact HTTP or HTTPS origins, such as
 * `setOf("https://app.example.com", "https://staging.example.com:8443")`.
 * @return Bridge installation handle, or `null` if Sideband is not configured, required WebView features are
 * unavailable, or WebKit rejects installation.
 * @throws IllegalArgumentException if an origin is not an exact HTTP or HTTPS origin. Wildcards, credentials,
 * non-root paths, queries, and fragments are not accepted.
 */
@UiThread
public fun Sideband.installWebViewBridge(
    webView: WebView,
    allowedOrigins: Set<String>,
): SidebandWebViewBridgeInstallation? {
    checkMainThread()
    val allowedOriginRules = validateAndNormalizeAllowedOrigins(allowedOrigins)

    if (!isConfigured) {
        Log.e(TAG, "Sideband.installWebViewBridge(...) called before Sideband.configure(...).")
        return null
    }

    val missingFeatures = missingRequiredWebViewFeatures(WebViewFeature::isFeatureSupported)
    if (missingFeatures.isNotEmpty()) {
        Log.e(
            TAG,
            "Sideband WebView bridge requires unsupported WebView features: ${missingFeatures.joinToString()}.",
        )
        return null
    }

    if (!webView.settings.javaScriptEnabled) {
        Log.w(TAG, "Sideband.installWebViewBridge(...) requires webView.settings.javaScriptEnabled = true.")
    }

    WebViewBridgeStore.existing(webView)?.let { existing ->
        if (existing.allowedOriginRules == allowedOriginRules) {
            return existing.handle
        }
        existing.uninstall()
    }

    val installation = try {
        WebViewBridgeInstallation.install(webView, allowedOriginRules)
    } catch (exception: RuntimeException) {
        Log.e(TAG, "Sideband WebView bridge installation failed.", exception)
        return null
    }
    WebViewBridgeStore.put(webView, installation)
    return installation.handle
}

internal fun missingRequiredWebViewFeatures(
    isFeatureSupported: (String) -> Boolean,
): List<String> = listOf(
    WebViewFeature.WEB_MESSAGE_LISTENER,
    WebViewFeature.DOCUMENT_START_SCRIPT,
).filterNot(isFeatureSupported)

internal fun validateAndNormalizeAllowedOrigins(allowedOrigins: Set<String>): Set<String> {
    require(allowedOrigins.isNotEmpty()) { "allowedOrigins must contain at least one exact HTTP or HTTPS origin." }
    return allowedOrigins.mapTo(linkedSetOf(), ::validateAndNormalizeOrigin)
}

private fun validateAndNormalizeOrigin(origin: String): String {
    require('*' !in origin) { "WebView bridge origin must not contain a wildcard: $origin" }

    val uri = try {
        URI(origin)
    } catch (_: URISyntaxException) {
        throw IllegalArgumentException("Invalid WebView bridge origin: $origin")
    }
    val scheme = uri.scheme?.lowercase()
    require(scheme == "http" || scheme == "https") {
        "WebView bridge origin must use http or https: $origin"
    }
    require(!uri.isOpaque && uri.host != null) { "Invalid WebView bridge origin: $origin" }
    require(uri.rawUserInfo == null) { "WebView bridge origin must not contain credentials: $origin" }
    require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") {
        "WebView bridge origin must not contain a non-root path: $origin"
    }
    require(uri.rawQuery == null) { "WebView bridge origin must not contain a query: $origin" }
    require(uri.rawFragment == null) { "WebView bridge origin must not contain a fragment: $origin" }
    require(!uri.rawAuthority.orEmpty().endsWith(':')) { "Invalid WebView bridge origin port: $origin" }
    require(uri.port == -1 || uri.port in 1..65_535) { "Invalid WebView bridge origin port: $origin" }

    val host = uri.host.lowercase().let { value ->
        if (':' in value && !value.startsWith('[')) "[$value]" else value
    }
    val port = uri.port.takeUnless { it == -1 || (scheme == "http" && it == 80) || (scheme == "https" && it == 443) }
    return buildString {
        append(scheme)
        append("://")
        append(host)
        if (port != null) {
            append(':')
            append(port)
        }
    }
}

internal fun shouldAcceptWebMessage(
    sourceOrigin: String,
    isMainFrame: Boolean,
    allowedOriginRules: Set<String>,
): Boolean = isMainFrame && normalizedSourceOrigin(sourceOrigin) in allowedOriginRules

internal object WebViewBridgeStore {
    private val lock = Any()
    private val installations = WeakHashMap<WebView, WebViewBridgeInstallation>()

    fun existing(webView: WebView): WebViewBridgeInstallation? = synchronized(lock) {
        installations[webView]?.takeIf { it.isInstalled }
    }

    fun put(webView: WebView, installation: WebViewBridgeInstallation) = synchronized(lock) {
        installations.put(webView, installation)
    }

    fun remove(webView: WebView) = synchronized(lock) {
        installations.remove(webView)
    }

    fun reset() {
        val current = synchronized(lock) {
            installations.values.toList().also { installations.clear() }
        }
        for (installation in current) {
            installation.uninstall()
        }
    }
}

internal class WebViewBridgeInstallation private constructor(
    webView: WebView,
    val allowedOriginRules: Set<String>,
) {
    val handle = SidebandWebViewBridgeInstallation { uninstall() }

    var isInstalled: Boolean = false
        private set

    private val webView = WeakReference(webView)
    private var documentStartScript: ScriptHandler? = null

    private val messageListener = WebViewCompat.WebMessageListener {
            _: WebView,
            message: WebMessageCompat,
            sourceOrigin: Uri,
            isMainFrame: Boolean,
            _: JavaScriptReplyProxy,
        ->
        if (!shouldAcceptWebMessage(sourceOrigin.toString(), isMainFrame, allowedOriginRules)) {
            Log.w(TAG, "Ignoring Sideband WebView message from a disallowed frame or origin.")
            return@WebMessageListener
        }
        message.data?.let(::handleMessage)
    }

    fun uninstall() {
        checkMainThread()
        if (!isInstalled) {
            return
        }
        val current = webView.get()
        documentStartScript?.remove()
        documentStartScript = null
        if (current != null) {
            WebViewCompat.removeWebMessageListener(current, handlerName)
            WebViewBridgeStore.remove(current)
        }
        isInstalled = false
        webView.clear()
    }

    private fun install(webView: WebView) {
        var listenerAdded = false
        try {
            WebViewCompat.addWebMessageListener(webView, handlerName, allowedOriginRules, messageListener)
            listenerAdded = true
            documentStartScript = WebViewCompat.addDocumentStartJavaScript(
                webView,
                javaScriptSource,
                allowedOriginRules,
            )
            webView.evaluateJavascript(javaScriptSource, null)
            isInstalled = true
        } catch (exception: RuntimeException) {
            runCatching { documentStartScript?.remove() }
                .onFailure { Log.e(TAG, "Failed to roll back Sideband document-start script.", it) }
            documentStartScript = null
            if (listenerAdded) {
                runCatching { WebViewCompat.removeWebMessageListener(webView, handlerName) }
                    .onFailure { Log.e(TAG, "Failed to roll back Sideband web message listener.", it) }
            }
            throw exception
        }
    }

    private fun handleMessage(body: String) {
        val action = WebViewBridgeMessage.parse(body) ?: return
        apply(action)
    }

    private fun apply(action: WebViewBridgeAction) {
        when (action) {
            is WebViewBridgeAction.TagUser -> Sideband.tagUser(action.userID)
            is WebViewBridgeAction.UntagUser -> Sideband.untagUser()
            is WebViewBridgeAction.Track -> Sideband.track(eventName = action.name, metadata = action.metadata)
        }
    }

    companion object {
        const val handlerName: String = "sideband"

        fun install(webView: WebView, allowedOriginRules: Set<String>): WebViewBridgeInstallation {
            return WebViewBridgeInstallation(webView, allowedOriginRules).also { it.install(webView) }
        }

        val javaScriptSource: String
            get() = """
				(() => {
					if (window !== window.top) return;
					const owner = "__sidebandAndroidBridge";
					if (window.Sideband && window.Sideband[owner] !== true) return;
					const handler = window.$handlerName;
					if (!handler) return;
					const post = (payload) => handler.postMessage(JSON.stringify(payload));
					const api = {
						tagUser: (userID) => {
							if (userID == null) return;
							post({ action: "tagUser", userID: String(userID) });
						},
						untagUser: () => post({ action: "untagUser" }),
						track: (name, metadata) => {
							if (name == null || String(name).trim() === "") return;
							post({
								action: "track",
								name: String(name),
								metadata: metadata && typeof metadata === "object" ? metadata : {}
							});
						}
					};
					Object.defineProperty(api, owner, { value: true });
					window.Sideband = api;
                })();
            """.trimIndent()
    }
}

private fun normalizedSourceOrigin(sourceOrigin: String): String? = runCatching {
    validateAndNormalizeOrigin(sourceOrigin)
}.getOrNull()

private fun checkMainThread() {
    check(Looper.myLooper() == Looper.getMainLooper()) {
        "Sideband WebView bridge installation and teardown must run on the main thread."
    }
}
