package com.heronikostudios.socialvault

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
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
import androidx.appcompat.widget.PopupMenu
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.heronikostudios.socialvault.databinding.ActivityMainBinding
import com.heronikostudios.socialvault.databinding.LayoutTabSwitcherSheetBinding
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var platformManager: PlatformManager
    private val tabManager = TabManager()

    private lateinit var platformAdapter: PlatformAdapter

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var isFullScreenMode: Boolean = false

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            if (uris != null && uris.isNotEmpty() && platformManager.isStripMetadataEnabled()) {
                binding.progressBar.visibility = View.VISIBLE
                Thread {
                    var strippedCount = 0
                    val processedUris = uris.map { uri ->
                        val res = MetadataStripper.stripImageMetadata(this, uri)
                        if (res.wasStripped) strippedCount++
                        res.uri
                    }.toTypedArray()

                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        if (strippedCount > 0) {
                            val msg = if (strippedCount == 1) {
                                "EXIF metadata stripped from photo"
                            } else {
                                "EXIF metadata stripped from $strippedCount photos"
                            }
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                        fileUploadCallback?.onReceiveValue(processedUris)
                        fileUploadCallback = null
                    }
                }.start()
            } else {
                fileUploadCallback?.onReceiveValue(uris)
                fileUploadCallback = null
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        platformManager = PlatformManager(this)
        Thread { MetadataStripper.cleanOldCache(this) }.start()

        setupInsets()
        setupDashboard()
        setupBottomNav()
        setupBackNavigation()
        setupTabListener()

        binding.btnExitFullScreen.setOnClickListener {
            platformManager.setFullScreenEnabled(false)
            applyFullScreenMode(false, showToast = true)
        }

        showDashboard()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            if (isFullScreenMode && tabManager.activeTab != null) {
                view.setPadding(0, 0, 0, 0)
            } else {
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            }
            insets
        }
    }

    private fun setupDashboard() {
        platformAdapter = PlatformAdapter(
            platforms = platformManager.getAllPlatforms().toMutableList(),
            onPlatformClick = { platform ->
                openPlatformInNewTab(platform)
            },
            onPlatformOptionsClick = { platform, anchorView ->
                showPlatformOptions(platform, anchorView)
            }
        )

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            @Suppress("DEPRECATION")
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                    platformAdapter.onItemMove(fromPos, toPos)
                    platformManager.movePlatform(fromPos, toPos)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Drag only
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.animate()?.scaleX(1.05f)?.scaleY(1.05f)?.setDuration(150)?.start()
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            }
        })

        binding.rvPlatformsGrid.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = platformAdapter
        }
        touchHelper.attachToRecyclerView(binding.rvPlatformsGrid)

        binding.btnOpenLink.setOnClickListener {
            showOpenUrlDialog()
        }

        binding.btnAddPlatform.setOnClickListener {
            showAddPlatformDialog()
        }

        binding.btnMoreMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.menu_dashboard, popup.menu)
            val activePlatform = tabManager.activeTab?.platform
            popup.menu.findItem(R.id.menu_strip_metadata)?.isChecked =
                platformManager.isStripMetadataEnabled()
            popup.menu.findItem(R.id.menu_polish_urls)?.isChecked =
                platformManager.isPolishUrlsEnabled()
            popup.menu.findItem(R.id.menu_full_screen)?.apply {
                val isTabOpen = (tabManager.activeTab != null)
                isChecked = if (isTabOpen) isFullScreenMode else platformManager.isFullScreenEnabled()
            }
            popup.menu.findItem(R.id.menu_download_video)?.isVisible =
                (tabManager.activeTab != null)
            popup.menu.findItem(R.id.menu_clear_cache)?.apply {
                isVisible = (activePlatform != null)
                if (activePlatform != null) {
                    title = "Clear Cache for ${activePlatform.name}"
                }
            }
            popup.menu.findItem(R.id.menu_wipe_data)?.apply {
                isVisible = (activePlatform != null)
                if (activePlatform != null) {
                    title = "Wipe Data for ${activePlatform.name}"
                }
            }
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_open_url -> {
                        showOpenUrlDialog()
                        true
                    }
                    R.id.menu_add_platform -> {
                        showAddPlatformDialog()
                        true
                    }
                    R.id.menu_strip_metadata -> {
                        val newState = !platformManager.isStripMetadataEnabled()
                        platformManager.setStripMetadataEnabled(newState)
                        item.isChecked = newState
                        val status = if (newState) "enabled (default)" else "disabled"
                        Toast.makeText(this, "Metadata stripping $status", Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.menu_polish_urls -> {
                        val newState = !platformManager.isPolishUrlsEnabled()
                        platformManager.setPolishUrlsEnabled(newState)
                        item.isChecked = newState
                        val status = if (newState) "enabled (default)" else "disabled"
                        Toast.makeText(this, "URL polishing $status", Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.menu_full_screen -> {
                        val isTabOpen = (tabManager.activeTab != null)
                        val newState = if (isTabOpen) !isFullScreenMode else !platformManager.isFullScreenEnabled()
                        platformManager.setFullScreenEnabled(newState)
                        item.isChecked = newState
                        if (isTabOpen) {
                            applyFullScreenMode(newState, showToast = true)
                        } else {
                            val msg = if (newState) getString(R.string.toast_full_screen_enabled) else getString(R.string.toast_full_screen_disabled)
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    R.id.menu_download_video -> {
                        tabManager.activeTab?.webView?.let { extractAndDownloadVideo(it) }
                        true
                    }
                    R.id.menu_clear_cache -> {
                        tabManager.activeTab?.platform?.let { showClearCacheDialog(it) }
                        true
                    }
                    R.id.menu_wipe_data -> {
                        tabManager.activeTab?.platform?.let { showWipeDataDialog(it) }
                        true
                    }
                    R.id.menu_reset_defaults -> {
                        showResetDefaultsDialog()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun setupBottomNav() {
        binding.btnNavHome.setOnClickListener {
            showDashboard()
        }

        binding.btnNavBack.setOnClickListener {
            val activeTab = tabManager.activeTab
            if (activeTab?.webView?.canGoBack() == true) {
                activeTab.webView.goBack()
            } else if (activeTab != null) {
                showDashboard()
            }
        }

        binding.btnNavForward.setOnClickListener {
            tabManager.activeTab?.webView?.let {
                if (it.canGoForward()) it.goForward()
            }
        }

        binding.btnNavRefresh.setOnClickListener {
            tabManager.activeTab?.webView?.reload()
        }

        binding.btnNavTabsContainer.setOnClickListener {
            showTabSwitcherDialog()
        }
    }

    private fun setupTabListener() {
        tabManager.onTabsChangedListener = {
            updateTabBadge()
            updateNavButtons()
        }
    }

    private fun updateTabBadge() {
        binding.tvTabCountBadge.text = tabManager.count.toString()
    }

    private fun updateNavButtons() {
        val active = tabManager.activeTab
        val isInTab = active != null

        val canGoBack = active?.webView?.canGoBack() == true
        val canGoForward = active?.webView?.canGoForward() == true

        binding.btnNavBack.alpha = if (isInTab) (if (canGoBack) 1.0f else 0.5f) else 0.3f
        binding.btnNavForward.alpha = if (isInTab && canGoForward) 1.0f else 0.3f
        binding.btnNavRefresh.alpha = if (isInTab) 1.0f else 0.3f

        binding.btnNavHome.setColorFilter(
            getColor(if (!isInTab) R.color.accent else R.color.on_surface)
        )
    }

    private fun showDashboard() {
        tabManager.deselectCurrentTab()
        applyFullScreenMode(false)
        binding.webViewContainer.visibility = View.GONE
        binding.dashboardView.visibility = View.VISIBLE
        binding.tvHeaderTitle.text = getString(R.string.app_name)
        binding.tvHeaderSubtitle.text = "Private & Sandboxed Social Hub"
        binding.progressBar.visibility = View.GONE
        updateNavButtons()
    }

    private fun openPlatformInNewTab(platform: Platform, targetUrl: String? = null) {
        val urlToLoad = targetUrl ?: platform.url
        val webView = createConfiguredWebView(platform)
        val tab = Tab(
            id = UUID.randomUUID().toString(),
            platform = platform,
            webView = webView,
            title = platform.name,
            currentUrl = urlToLoad
        )

        binding.webViewContainer.addView(
            webView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        tabManager.addTab(tab)
        switchToTab(tab)
        webView.loadUrl(urlToLoad)
    }

    private fun switchToTab(tab: Tab) {
        tabManager.selectTab(tab.id)
        binding.dashboardView.visibility = View.GONE
        binding.webViewContainer.visibility = View.VISIBLE
        binding.tvHeaderTitle.text = tab.platform.name
        binding.tvHeaderSubtitle.text = tab.platform.url
        updateNavButtons()
        if (platformManager.isFullScreenEnabled()) {
            applyFullScreenMode(true)
        } else {
            applyFullScreenMode(false)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "MissingOnRenderProcessGone")
    private fun createConfiguredWebView(platform: Platform): WebView {
        val webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val cookieManager = PlatformStorageManager.getCookieManagerForPlatform(platform)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            val profileName = PlatformStorageManager.getProfileName(platform)
            val profile = ProfileStore.getInstance().getOrCreateProfile(profileName)
            WebViewCompat.setProfile(webView, profile.name)
        }

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = true
        }

        cookieManager.apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            DownloadHelper.downloadFile(
                context = this@MainActivity,
                url = url,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimeType,
                cookieManager = cookieManager
            )
        }

        webView.setOnLongClickListener { v ->
            val result = (v as? WebView)?.hitTestResult ?: return@setOnLongClickListener false
            val extra = result.extra
            when (result.type) {
                WebView.HitTestResult.IMAGE_TYPE,
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    if (!extra.isNullOrBlank()) {
                        DownloadHelper.showMediaContextMenu(
                            context = this@MainActivity,
                            mediaUrl = extra,
                            isImage = true,
                            userAgent = webView.settings.userAgentString,
                            cookieManager = cookieManager,
                            onOpenInNewTab = { mediaUrl ->
                                openMediaInNewTab(mediaUrl)
                            }
                        )
                        true
                    } else {
                        false
                    }
                }
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                    if (!extra.isNullOrBlank() && DownloadHelper.isMediaUrl(extra)) {
                        val isImg = !extra.contains(".mp4", ignoreCase = true) && !extra.contains(".webm", ignoreCase = true)
                        DownloadHelper.showMediaContextMenu(
                            context = this@MainActivity,
                            mediaUrl = extra,
                            isImage = isImg,
                            userAgent = webView.settings.userAgentString,
                            cookieManager = cookieManager,
                            onOpenInNewTab = { mediaUrl ->
                                openMediaInNewTab(mediaUrl)
                            }
                        )
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (tabManager.activeTab?.webView == view) {
                    if (newProgress in 1..99) {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.progressBar.progress = newProgress
                    } else {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                val currentTab = tabManager.tabs.find { it.webView == view }
                if (currentTab != null && !title.isNullOrBlank()) {
                    currentTab.title = title
                    if (tabManager.activeTab == currentTab) {
                        binding.tvHeaderTitle.text = title
                    }
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
                binding.topBar.visibility = View.GONE
                binding.bottomNavBar.visibility = View.GONE

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

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                val url = uri.toString()
                val scheme = uri.scheme?.lowercase() ?: return false

                // Disallow dangerous, file, or script schemes from navigating or external launching
                if (scheme == "file" || scheme == "content" || scheme == "javascript" || scheme == "data") {
                    return true
                }

                // If it is an allowed web URL within the platform sandbox, continue loading in WebView
                if ((scheme == "http" || scheme == "https") && platform.isDomainAllowed(url)) {
                    return false
                }

                // Safely dispatch external URLs to system handlers
                return try {
                    when (scheme) {
                        "http", "https" -> {
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                addCategory(Intent.CATEGORY_BROWSABLE)
                            }
                            startActivity(intent)
                            true
                        }
                        "intent" -> {
                            val parsedIntent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                                addCategory(Intent.CATEGORY_BROWSABLE)
                                component = null
                                selector = null
                            }
                            startActivity(parsedIntent)
                            true
                        }
                        "mailto", "tel", "sms", "market" -> {
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            startActivity(intent)
                            true
                        }
                        else -> false
                    }
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "Cannot open external link", Toast.LENGTH_SHORT).show()
                    true
                }
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                view?.let { wv ->
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.destroy()
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (tabManager.activeTab?.webView == view) {
                    binding.progressBar.visibility = View.VISIBLE
                    updateNavButtons()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!url.isNullOrBlank()) {
                    tabManager.tabs.find { it.webView == view }?.currentUrl = url
                }
                if (tabManager.activeTab?.webView == view) {
                    binding.progressBar.visibility = View.GONE
                    updateNavButtons()
                }
                CookieManager.getInstance().flush()
            }
        }

        return webView
    }

    private fun hideCustomView() {
        val view = customView ?: return
        binding.customViewContainer.apply {
            removeView(view)
            visibility = View.GONE
        }
        binding.topBar.visibility = View.VISIBLE
        binding.bottomNavBar.visibility = View.VISIBLE

        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (isFullScreenMode && tabManager.activeTab != null) {
            applyFullScreenMode(true)
        } else {
            binding.topBar.visibility = View.VISIBLE
            binding.bottomNavBar.visibility = View.VISIBLE
            setSystemBarsVisible(true)
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    hideCustomView()
                } else {
                    val active = tabManager.activeTab
                    if (active != null) {
                        if (active.webView.canGoBack()) {
                            active.webView.goBack()
                        } else if (isFullScreenMode) {
                            applyFullScreenMode(false, showToast = true)
                        } else {
                            showDashboard()
                        }
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    private fun showTabSwitcherDialog() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = LayoutTabSwitcherSheetBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        lateinit var tabAdapter: TabAdapter

        tabAdapter = TabAdapter(
            tabs = tabManager.tabs,
            activeTabId = tabManager.activeTab?.id,
            onTabClick = { tab ->
                switchToTab(tab)
                dialog.dismiss()
            },
            onTabClose = { tab ->
                val closedTab = tabManager.closeTab(tab.id)
                if (closedTab != null) {
                    binding.webViewContainer.removeView(closedTab.webView)
                }
                if (tabManager.count == 0) {
                    dialog.dismiss()
                    showDashboard()
                } else {
                    sheetBinding.tvSheetTitle.text = "Open Tabs (${tabManager.count})"
                    tabAdapter.updateTabs(tabManager.tabs, tabManager.activeTab?.id)
                    tabManager.activeTab?.let { switchToTab(it) }
                }
            }
        )

        sheetBinding.rvTabs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = tabAdapter
        }

        sheetBinding.tvSheetTitle.text = "Open Tabs (${tabManager.count})"
        sheetBinding.tvEmptyTabs.visibility = if (tabManager.count == 0) View.VISIBLE else View.GONE
        sheetBinding.rvTabs.visibility = if (tabManager.count > 0) View.VISIBLE else View.GONE

        sheetBinding.btnCloseAll.setOnClickListener {
            for (tab in tabManager.tabs) {
                binding.webViewContainer.removeView(tab.webView)
            }
            tabManager.closeAllTabs()
            dialog.dismiss()
            showDashboard()
        }

        dialog.show()
    }

    private fun showAddPlatformDialog(prefilledUrl: String? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_platform, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etName)
        val etUrl = dialogView.findViewById<TextInputEditText>(R.id.etUrl)

        if (!prefilledUrl.isNullOrBlank()) {
            val cleanPrefill = if (platformManager.isPolishUrlsEnabled()) {
                UrlPolisher.polishUrl(prefilledUrl).polishedUrl
            } else {
                prefilledUrl
            }
            etUrl.setText(cleanPrefill)
            val host = try {
                Uri.parse(cleanPrefill).host?.removePrefix("www.")?.removePrefix("m.") ?: ""
            } catch (_: Exception) {
                ""
            }
            if (host.isNotEmpty()) {
                val suggestedName = host.substringBefore('.').replaceFirstChar { it.uppercase() }
                etName.setText(suggestedName)
            }
        }

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_add) { _, _ ->
                val name = etName.text?.toString().orEmpty().trim()
                val rawUrl = etUrl.text?.toString().orEmpty().trim()
                val url = if (platformManager.isPolishUrlsEnabled()) {
                    UrlPolisher.polishUrl(rawUrl).polishedUrl
                } else {
                    rawUrl
                }

                if (name.isNotEmpty() && url.startsWith("https://")) {
                    val newPlatform = platformManager.addCustomPlatform(name, url)
                    if (newPlatform != null) {
                        platformAdapter.updatePlatforms(platformManager.getAllPlatforms())
                        openPlatformInNewTab(newPlatform)
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

    private fun showPlatformOptions(platform: Platform, anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.menu_platform_item, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_open_tab -> {
                    openPlatformInNewTab(platform)
                    true
                }
                R.id.action_clear_cache -> {
                    showClearCacheDialog(platform)
                    true
                }
                R.id.action_wipe_data -> {
                    showWipeDataDialog(platform)
                    true
                }
                R.id.action_remove_platform -> {
                    showDeletePlatformDialog(platform)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showClearCacheDialog(platform: Platform) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_clear_cache_title, platform.name))
            .setMessage(getString(R.string.dialog_clear_cache_msg, platform.name))
            .setPositiveButton(R.string.btn_clear_cache) { _, _ ->
                PlatformStorageManager.clearCacheForPlatform(this, platform, tabManager.tabs)
                Toast.makeText(this, getString(R.string.toast_cache_cleared, platform.name), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showWipeDataDialog(platform: Platform) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_wipe_data_title, platform.name))
            .setMessage(getString(R.string.dialog_wipe_data_msg, platform.name))
            .setPositiveButton(R.string.btn_wipe_data) { _, _ ->
                val matchingTabs = tabManager.tabs.filter { it.platform.id == platform.id }
                for (tab in matchingTabs) {
                    tabManager.closeTab(tab.id)
                }

                if (tabManager.activeTab == null) {
                    binding.dashboardView.visibility = View.VISIBLE
                    binding.webViewContainer.visibility = View.GONE
                    binding.tvHeaderTitle.text = getString(R.string.app_name)
                    binding.tvHeaderSubtitle.text = getString(R.string.desc_open_social_link)
                    updateNavButtons()
                }

                PlatformStorageManager.wipeDataForPlatform(this, platform) {
                    runOnUiThread {
                        Toast.makeText(this, getString(R.string.toast_data_wiped, platform.name), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showDeletePlatformDialog(platform: Platform) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove ${platform.name}?")
            .setMessage("Remove ${platform.name} from your dashboard?\nYou can restore original apps anytime using 'Reset to Defaults'.")
            .setPositiveButton("Remove") { _, _ ->
                platformManager.deletePlatform(platform.id)
                platformAdapter.updatePlatforms(platformManager.getAllPlatforms())
                Toast.makeText(this, "${platform.name} removed", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showResetDefaultsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset to Defaults")
            .setMessage("Reset the dashboard to the default platforms and original order?")
            .setPositiveButton("Reset") { _, _ ->
                val defaults = platformManager.resetToDefaults()
                platformAdapter.updatePlatforms(defaults)
                Toast.makeText(this, "Dashboard reset to defaults", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun applyFullScreenMode(enabled: Boolean, showToast: Boolean = false) {
        isFullScreenMode = enabled
        if (enabled && tabManager.activeTab != null) {
            binding.topBar.visibility = View.GONE
            binding.bottomNavBar.visibility = View.GONE
            binding.cardExitFullScreen.visibility = View.VISIBLE
            setSystemBarsVisible(false)
            ViewCompat.requestApplyInsets(binding.rootLayout)
            if (showToast) {
                Toast.makeText(this, R.string.toast_full_screen_enabled, Toast.LENGTH_SHORT).show()
            }
        } else {
            binding.topBar.visibility = View.VISIBLE
            binding.bottomNavBar.visibility = View.VISIBLE
            binding.cardExitFullScreen.visibility = View.GONE
            setSystemBarsVisible(true)
            ViewCompat.requestApplyInsets(binding.rootLayout)
            if (showToast) {
                Toast.makeText(this, R.string.toast_full_screen_disabled, Toast.LENGTH_SHORT).show()
            }
        }
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

    private fun openMediaInNewTab(mediaUrl: String) {
        val host = try {
            Uri.parse(mediaUrl).host?.lowercase() ?: "media"
        } catch (_: Exception) {
            "media"
        }
        val mediaPlatform = Platform(
            id = "media_" + UUID.randomUUID().toString().take(8),
            name = "Media",
            url = mediaUrl,
            iconType = "globe",
            allowedDomains = listOf(host)
        )
        openPlatformInNewTab(mediaPlatform, mediaUrl)
    }

    private fun showOpenUrlDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_open_url, null)
        val etOpenUrl = dialogView.findViewById<TextInputEditText>(R.id.etOpenUrl)
        val tilUrl = dialogView.findViewById<TextInputLayout>(R.id.tilUrl)

        if (platformManager.isPolishUrlsEnabled()) {
            tilUrl.isHelperTextEnabled = true
            tilUrl.helperText = getString(R.string.helper_url_cleaning)
        } else {
            tilUrl.helperText = null
            tilUrl.isHelperTextEnabled = false
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipData = clipboard?.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val clipText = clipData.getItemAt(0)?.text?.toString()?.trim()
            if (!clipText.isNullOrBlank()) {
                val candidate = extractUrlFromText(clipText) ?: if (clipText.startsWith("http://", ignoreCase = true) || clipText.startsWith("https://", ignoreCase = true)) clipText else null
                if (candidate != null) {
                    val cleanCandidate = if (platformManager.isPolishUrlsEnabled()) {
                        UrlPolisher.polishUrl(candidate).polishedUrl
                    } else {
                        candidate
                    }
                    etOpenUrl.setText(cleanCandidate)
                    etOpenUrl.selectAll()
                }
            }
        }

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_open) { _, _ ->
                val inputUrl = etOpenUrl.text?.toString()?.trim() ?: ""
                if (inputUrl.isNotEmpty()) {
                    openSocialUrlIfSupported(inputUrl)
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun openSocialUrlIfSupported(rawUrl: String): Boolean {
        val normalizedUrl = platformManager.normalizeUrl(rawUrl)
        val urlToOpen = if (platformManager.isPolishUrlsEnabled()) {
            val result = UrlPolisher.polishUrl(normalizedUrl)
            if (result.wasPolished) {
                val count = result.removedParams.size
                val msg = if (count == 1) {
                    "Polished URL (removed 1 tracking parameter)"
                } else {
                    "Polished URL (removed $count tracking parameters)"
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
            result.polishedUrl
        } else {
            normalizedUrl
        }
        val matchedPlatform = platformManager.findMatchingPlatform(urlToOpen)

        return if (matchedPlatform != null) {
            openPlatformInNewTab(matchedPlatform, urlToOpen)
            true
        } else {
            val host = try {
                Uri.parse(urlToOpen).host ?: urlToOpen
            } catch (_: Exception) {
                urlToOpen
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("Unsupported Platform")
                .setMessage("The link ($host) is not from a supported social platform. SocialVault only opens supported platforms to isolate sessions and protect your privacy.")
                .setPositiveButton("OK", null)
                .setNeutralButton("Add as Custom Platform") { _, _ ->
                    showAddPlatformDialog(urlToOpen)
                }
                .show()
            false
        }
    }

    private fun handleIncomingIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return

        when (incomingIntent.action) {
            Intent.ACTION_VIEW -> {
                val dataUri = incomingIntent.dataString
                if (!dataUri.isNullOrBlank()) {
                    openSocialUrlIfSupported(dataUri)
                    incomingIntent.data = null
                }
            }
            Intent.ACTION_SEND -> {
                if (incomingIntent.type == "text/plain") {
                    val sharedText = incomingIntent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank()) {
                        val extractedUrl = extractUrlFromText(sharedText)
                        if (extractedUrl != null) {
                            openSocialUrlIfSupported(extractedUrl)
                        } else {
                            Toast.makeText(this, "No valid link found in shared text", Toast.LENGTH_SHORT).show()
                        }
                        incomingIntent.removeExtra(Intent.EXTRA_TEXT)
                    }
                }
            }
        }
    }

    private fun extractUrlFromText(text: String): String? {
        val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
        val match = urlRegex.find(text)
        return match?.value?.trimEnd('.', ',', ')', ']', ';', '>', '!', '"', '\'')
    }

    private fun extractAndDownloadVideo(webView: WebView) {
        val js = """
            (function() {
                var videos = document.getElementsByTagName('video');
                for (var i = 0; i < videos.length; i++) {
                    var v = videos[i];
                    if (v.currentSrc && v.currentSrc.startsWith('http')) return v.currentSrc;
                    if (v.src && v.src.startsWith('http')) return v.src;
                    var sources = v.getElementsByTagName('source');
                    for (var j = 0; j < sources.length; j++) {
                        if (sources[j].src && sources[j].src.startsWith('http')) return sources[j].src;
                    }
                }
                return null;
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            val cleanResult = result?.trim('"', ' ', '\n')
            if (!cleanResult.isNullOrBlank() && cleanResult != "null" && (cleanResult.startsWith("http://") || cleanResult.startsWith("https://"))) {
                DownloadHelper.downloadFile(
                    context = this@MainActivity,
                    url = cleanResult,
                    userAgent = webView.settings.userAgentString
                )
            } else {
                Toast.makeText(this@MainActivity, "No downloadable video stream found on this page", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tabManager.activeTab?.webView?.onResume()
        CookieManager.getInstance().flush()
    }

    override fun onPause() {
        super.onPause()
        tabManager.activeTab?.webView?.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        super.onDestroy()
        tabManager.closeAllTabs()
        CookieManager.getInstance().flush()
    }
}
