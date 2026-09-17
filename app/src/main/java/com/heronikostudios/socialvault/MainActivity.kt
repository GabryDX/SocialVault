package com.heronikostudios.socialvault

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.heronikostudios.socialvault.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var platformManager: PlatformManager

    private var currentPlatform: Platform? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            fileUploadCallback?.onReceiveValue(uris)
            fileUploadCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        platformManager = PlatformManager(this)

        setupInsets()
        setupWebView()
        setupTopBar()
        setupBackNavigation()
        setupPlatformChips()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupTopBar() {
        binding.btnRefresh.setOnClickListener {
            binding.webView.reload()
        }

        binding.btnAddPlatform.setOnClickListener {
            showAddPlatformDialog()
        }

        binding.swipeRefresh.setOnRefreshListener {
            binding.webView.reload()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(binding.webView, true)
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress in 1..99) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = newProgress
                } else {
                    binding.progressBar.visibility = View.GONE
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback

                binding.customViewContainer.apply {
                    addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    visibility = View.VISIBLE
                }
                binding.swipeRefresh.visibility = View.GONE
                binding.topBar.visibility = View.GONE
                binding.chipScrollView.visibility = View.GONE

                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                setSystemBarsVisible(false)
            }

            override fun onHideCustomView() {
                hideCustomView()
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                }

                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (_: Exception) {
                    fileUploadCallback = null
                    false
                }
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                val platform = currentPlatform ?: return false

                return if (platform.isDomainAllowed(url)) {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "Cannot open external link", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                CookieManager.getInstance().flush()
            }
        }
    }

    private fun hideCustomView() {
        val view = customView ?: return
        binding.customViewContainer.apply {
            removeView(view)
            visibility = View.GONE
        }
        binding.swipeRefresh.visibility = View.VISIBLE
        binding.topBar.visibility = View.VISIBLE
        binding.chipScrollView.visibility = View.VISIBLE

        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        setSystemBarsVisible(true)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    hideCustomView()
                } else if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun setupPlatformChips() {
        binding.platformChipGroup.removeAllViews()
        val platforms = platformManager.getAllPlatforms()
        val lastSelectedId = platformManager.getLastSelectedPlatformId()

        var selectedChip: Chip? = null

        for (platform in platforms) {
            val chip = Chip(this).apply {
                text = platform.name
                isCheckable = true
                isClickable = true
                tag = platform.id

                setOnClickListener {
                    selectPlatform(platform)
                }

                if (platform.isCustom) {
                    setOnLongClickListener {
                        showDeletePlatformDialog(platform)
                        true
                    }
                }
            }
            binding.platformChipGroup.addView(chip)

            if (platform.id == lastSelectedId) {
                selectedChip = chip
            }
        }

        val targetPlatform = platforms.firstOrNull { it.id == lastSelectedId } ?: platforms.first()
        (selectedChip ?: binding.platformChipGroup.getChildAt(0) as? Chip)?.isChecked = true
        selectPlatform(targetPlatform)
    }

    private fun selectPlatform(platform: Platform) {
        if (currentPlatform?.id == platform.id) return
        currentPlatform = platform
        platformManager.setLastSelectedPlatformId(platform.id)
        binding.webView.loadUrl(platform.url)
    }

    private fun showAddPlatformDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_platform, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etName)
        val etUrl = dialogView.findViewById<TextInputEditText>(R.id.etUrl)

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_add) { _, _ ->
                val name = etName.text?.toString().orEmpty().trim()
                val url = etUrl.text?.toString().orEmpty().trim()

                if (name.isNotEmpty() && url.startsWith("https://")) {
                    val newPlatform = platformManager.addCustomPlatform(name, url)
                    if (newPlatform != null) {
                        setupPlatformChips()
                        selectPlatform(newPlatform)
                    } else {
                        Toast.makeText(this, R.string.invalid_url_error, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, R.string.invalid_url_error, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showDeletePlatformDialog(platform: Platform) {
        MaterialAlertDialogBuilder(this)
            .setTitle(platform.name)
            .setMessage("Do you want to delete this custom platform?")
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                platformManager.removeCustomPlatform(platform.id)
                setupPlatformChips()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun setSystemBarsVisible(visible: Boolean) {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            if (visible) {
                show(WindowInsetsCompat.Type.systemBars())
            } else {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        CookieManager.getInstance().flush()
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.webView.destroy()
        CookieManager.getInstance().flush()
    }
}
