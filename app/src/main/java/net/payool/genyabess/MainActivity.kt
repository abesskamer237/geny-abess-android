package net.payool.genyabess

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : Activity() {
    private val home = "https://geny.payool.net/"
    private val internalHosts = setOf("geny.payool.net", "www.geny.payool.net")
    private lateinit var web: WebView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var progress: ProgressBar
    private lateinit var offlinePanel: LinearLayout
    private lateinit var splashPanel: LinearLayout
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val fileRequest = 1042
    private lateinit var connectivity: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var hasLoadedPage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(20, 35, 59)
        window.navigationBarColor = Color.rgb(20, 35, 59)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.WHITE)
        swipe = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(Color.rgb(51, 214, 170), Color.rgb(20, 35, 59))
        }
        web = WebView(this)
        web.setBackgroundColor(Color.WHITE)
        swipe.addView(web, ViewGroup.LayoutParams(-1, -1))
        swipe.setOnChildScrollUpCallback { _, _ -> web.scrollY > 0 }
        swipe.setOnRefreshListener { if (isOnline()) web.reload() else showOffline(true) }

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(51, 214, 170))
            max = 100
        }
        root.addView(swipe, FrameLayout.LayoutParams(-1, -1))
        root.addView(progress, FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP))

        offlinePanel = messagePanel(
            icon = android.R.drawable.stat_notify_error,
            title = "Pas de connexion Internet",
            message = "Vérifie ta connexion puis réessaie."
        ).apply { visibility = View.GONE }
        val retry = actionButton("Réessayer") {
            if (isOnline()) {
                offlinePanel.visibility = View.GONE
                loadHome()
            } else Toast.makeText(this, "Toujours hors connexion", Toast.LENGTH_SHORT).show()
        }
        offlinePanel.addView(retry)
        root.addView(offlinePanel, FrameLayout.LayoutParams(-1, -1))

        splashPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(20, 35, 59))
            setPadding(dp(28), dp(28), dp(28), dp(28))
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_launcher)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(112), dp(112)))
            addView(TextView(this@MainActivity).apply {
                text = "Geny-Abess"
                textSize = 28f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dp(18), 0, dp(6))
            })
            addView(TextView(this@MainActivity).apply {
                text = "Simple. Rapide. Connecté."
                textSize = 14f
                setTextColor(Color.rgb(180, 199, 214))
                gravity = Gravity.CENTER
            })
            addView(ProgressBar(this@MainActivity).apply {
                indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.rgb(51, 214, 170))
            }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { topMargin = dp(28) })
        }
        root.addView(splashPanel, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        configureWebView()
        registerConnectivity()
        if (isOnline()) {
            if (savedInstanceState == null) loadHome() else {
                val restored = web.restoreState(savedInstanceState)
                if (restored == null) loadHome() else {
                    hasLoadedPage = true
                    splashPanel.visibility = View.GONE
                }
            }
        } else {
            splashPanel.visibility = View.GONE
            showOffline(false)
        }
    }

    private fun configureWebView() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        // Third-party cookies remain disabled unless the website explicitly requires them.

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme?.lowercase().orEmpty()
                val host = uri.host?.lowercase().orEmpty()
                if (scheme == "https" && host in internalHosts) return false

                // Payment providers and other sites are opened outside the app's trusted WebView.
                if (scheme == "https" || scheme == "http" || scheme == "mailto" || scheme == "tel") {
                    openExternal(uri)
                    return true
                }
                if (scheme == "intent") {
                    try {
                        val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                        intent.component = null
                        intent.selector = null
                        startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "Impossible d'ouvrir ce lien", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
                progress.progress = 5
                swipe.isRefreshing = false
                offlinePanel.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView, url: String) {
                hasLoadedPage = true
                progress.visibility = View.GONE
                swipe.isRefreshing = false
                splashPanel.animate().alpha(0f).setDuration(180).withEndAction {
                    splashPanel.visibility = View.GONE
                    splashPanel.alpha = 1f
                }.start()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && !isOnline()) showOffline(true)
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                // Never bypass certificate errors, especially on payment/login pages.
                handler.cancel()
                if (error.primaryError != SslError.SSL_UNTRUSTED && error.primaryError != SslError.SSL_EXPIRED &&
                    error.primaryError != SslError.SSL_IDMISMATCH && error.primaryError != SslError.SSL_NOTYETVALID) {
                    Toast.makeText(this@MainActivity, "Connexion HTTPS non sécurisée", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Certificat HTTPS invalide. Connexion annulée.", Toast.LENGTH_LONG).show()
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    startActivityForResult(params.createIntent(), fileRequest)
                    true
                } catch (_: ActivityNotFoundException) {
                    fileCallback = null
                    callback.onReceiveValue(null)
                    Toast.makeText(this@MainActivity, "Aucun sélecteur de fichiers disponible", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }

        web.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(url, userAgent, contentDisposition, mimeType)
        })
    }

    private fun startDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        try {
            val uri = Uri.parse(url)
            if (uri.scheme != "https") {
                Toast.makeText(this, "Téléchargement bloqué : HTTPS obligatoire", Toast.LENGTH_LONG).show()
                return
            }
            val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(uri)
                .setTitle(filename)
                .setDescription("Téléchargement Geny-Abess")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            val cookies = CookieManager.getInstance().getCookie(url)
            if (!cookies.isNullOrBlank()) request.addRequestHeader("Cookie", cookies)
            request.addRequestHeader("User-Agent", userAgent ?: "Geny-Abess Android")
            if (!mimeType.isNullOrBlank()) request.setMimeType(mimeType)
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, "Téléchargement lancé : $filename", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Téléchargement impossible. Réessaie depuis le site.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Aucune application ne peut ouvrir ce lien", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadHome() {
        if (!isOnline()) {
            showOffline(false)
            return
        }
        offlinePanel.visibility = View.GONE
        splashPanel.visibility = if (hasLoadedPage) View.GONE else View.VISIBLE
        splashPanel.alpha = 1f
        web.loadUrl(home)
    }

    private fun isOnline(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun registerConnectivity() {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    if (offlinePanel.visibility == View.VISIBLE) {
                        offlinePanel.visibility = View.GONE
                        loadHome()
                    }
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    if (!isOnline()) showOffline(true)
                }
            }
        }
        networkCallback = callback
        try { connectivity.registerDefaultNetworkCallback(callback) } catch (_: Exception) { networkCallback = null }
    }

    private fun showOffline(fromError: Boolean) {
        swipe.isRefreshing = false
        progress.visibility = View.GONE
        if (!hasLoadedPage || fromError) {
            offlinePanel.visibility = View.VISIBLE
            splashPanel.visibility = View.GONE
        }
    }

    private fun messagePanel(icon: Int, title: String, message: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(247, 249, 252))
            setPadding(dp(30), dp(30), dp(30), dp(30))
            addView(ImageView(this@MainActivity).apply {
                setImageResource(icon)
                imageTintList = android.content.res.ColorStateList.valueOf(Color.rgb(51, 214, 170))
            }, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 21f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(20, 35, 59))
                gravity = Gravity.CENTER
                setPadding(0, dp(18), 0, dp(8))
            })
            addView(TextView(this@MainActivity).apply {
                text = message
                textSize = 15f
                setTextColor(Color.rgb(85, 101, 120))
                gravity = Gravity.CENTER
            })
        }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.rgb(20, 35, 59))
            background = GradientDrawable().apply {
                setColor(Color.rgb(51, 214, 170))
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(-2, dp(48)).apply {
                topMargin = dp(22)
                gravity = Gravity.CENTER
            }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Android SDK")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == fileRequest) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        }
    }

    @Deprecated("Deprecated in Android SDK")
    override fun onBackPressed() {
        when {
            offlinePanel.visibility == View.VISIBLE && hasLoadedPage -> offlinePanel.visibility = View.GONE
            web.canGoBack() -> web.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        web.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        networkCallback?.let { try { connectivity.unregisterNetworkCallback(it) } catch (_: Exception) {} }
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }
}
