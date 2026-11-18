package io.github.kichikuou.xsystem4

import android.os.Bundle
import android.view.MenuItem
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class ManualActivity : ThemedActivity() {
    companion object {
        const val EXTRA_URL = "URL"
    }

    private var manualWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual)

        actionBar?.setDisplayHomeAsUpEnabled(true)

        val url = intent.getStringExtra(EXTRA_URL)
        val webView = findViewById<WebView>(R.id.webview)
        manualWebView = webView
        webView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return url == null || !url.startsWith("file:", ignoreCase = true)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val scheme = request?.url?.scheme
                return !scheme.equals("file", ignoreCase = true)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                actionBar?.title = title
            }
        }
        webView.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = true
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
        }
        if (url != null && url.startsWith("file:", ignoreCase = true)) {
            webView.loadUrl(url)
        }
    }

    override fun onDestroy() {
        manualWebView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.stopLoading()
            wv.webChromeClient = null
            wv.destroy()
        }
        manualWebView = null
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
