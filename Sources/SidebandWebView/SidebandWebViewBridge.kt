// Default WebView bridge for HTML-wrapper apps that track with Google Tag Manager.
//
// This module is not part of com.sideband:sideband-android. It ships as source in the public GitHub repo
// (Sources/SidebandWebView). Use sideband-android-webview as-is, or copy these files into the host app and
// edit them. Keep this file and SidebandWebViewBridgeMessage.kt together.
//
// Customization points:
// - handlerName: JavascriptInterface name and window.<name>
// - javaScriptSource: injected window.Sideband API (tagUser / untagUser / track)
// - WebViewBridgeMessage.parse: accepted payload shape and metadata coercion
// - apply(_:): routing into Sideband.tagUser / untagUser / track
//
// The GTM Custom HTML tags in the README must keep using the same window.Sideband methods.

package com.sideband.sdk.webview

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.sideband.sdk.Sideband
import java.util.WeakHashMap

private const val TAG = "Sideband"

/**
 * Handle for a WebView JavaScript bridge installation.
 */
public class SidebandWebViewBridgeInstallation internal constructor(
    private val uninstallHandler: () -> Unit,
) {
    /**
     * Removes the JavascriptInterface from the WebView.
     *
     * Injected `window.Sideband` scripts remain until the WebView navigates or is rebuilt.
     */
    public fun uninstall() {
        uninstallHandler()
    }
}

/**
 * Installs a JavaScript bridge that forwards `window.Sideband` calls from a [WebView] into the shared client.
 *
 * Call [Sideband.configure] before installing the bridge, and call this before the WebView's first `load` so the
 * bridge is present when Google Tag Manager starts. If the current page is already loaded, the SDK also injects
 * `window.Sideband` immediately.
 *
 * Enable JavaScript on the WebView (`webView.settings.javaScriptEnabled = true`). The bridge does not enable it.
 *
 * The returned handle can be used to remove the JavascriptInterface during teardown. The bridge retains the
 * installation, so host apps do not need to retain the handle just to keep the bridge active.
 *
 * @param webView Host [WebView] that loads the HTML wrapper.
 * @return Bridge installation handle, or `null` if Sideband has not been configured.
 */
public fun Sideband.installWebViewBridge(webView: WebView): SidebandWebViewBridgeInstallation? {
    if (!isConfigured) {
        Log.e(TAG, "Sideband.installWebViewBridge(webView) called before Sideband.configure(...).")
        return null
    }

    if (!webView.settings.javaScriptEnabled) {
        Log.w(TAG, "Sideband.installWebViewBridge(webView) requires webView.settings.javaScriptEnabled = true.")
    }

    WebViewBridgeStore.existing(webView)?.let { existing ->
        return existing.handle
    }

    val installation = WebViewBridgeInstallation(webView)
    WebViewBridgeStore.put(webView, installation)
    return installation.handle
}

internal object WebViewBridgeStore {
    private val lock = Any()
    private val installations = WeakHashMap<WebView, WebViewBridgeInstallation>()

    fun existing(webView: WebView): WebViewBridgeInstallation? = synchronized(lock) {
        installations[webView]?.takeIf { it.isInstalled }
    }

    fun put(webView: WebView, installation: WebViewBridgeInstallation) = synchronized(lock) {
        installations[webView] = installation
    }

    fun remove(webView: WebView) = synchronized(lock) {
        installations.remove(webView)
    }

    fun reset() {
        val current = synchronized(lock) { installations.values.toList() }
        for (installation in current) {
            installation.uninstall()
        }
    }
}

internal class WebViewBridgeInstallation(
    webView: WebView,
) {
    val handle = SidebandWebViewBridgeInstallation { uninstall() }

    var isInstalled: Boolean = true
        private set

    private var webView: WebView? = webView
    private val jsInterface = SidebandJsInterface(::handleMessage)
    private var documentStartScript: ScriptHandler? = null

    init {
        install(webView)
    }

    fun uninstall() {
        if (!isInstalled) {
            return
        }
        isInstalled = false
        val current = webView
        current?.removeJavascriptInterface(handlerName)
        documentStartScript?.remove()
        documentStartScript = null
        if (current != null) {
            WebViewBridgeStore.remove(current)
        }
        webView = null
    }

    private fun install(webView: WebView) {
        webView.removeJavascriptInterface(handlerName)
        webView.addJavascriptInterface(jsInterface, handlerName)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            documentStartScript = WebViewCompat.addDocumentStartJavaScript(
                webView,
                javaScriptSource,
                setOf("*"),
            )
        }

        webView.evaluateJavascript(javaScriptSource, null)
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

        val javaScriptSource: String
            get() = """
                (() => {
                	if (window !== window.top) return;
                	if (window.Sideband) return;
                	const handler = window.$handlerName;
                	if (!handler) return;
                	const post = (payload) => handler.postMessage(JSON.stringify(payload));
                	window.Sideband = {
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
                })();
            """.trimIndent()
    }
}

private class SidebandJsInterface(
    private val onMessage: (String) -> Unit,
) {
    @JavascriptInterface
    fun postMessage(body: String) {
        onMessage(body)
    }
}
