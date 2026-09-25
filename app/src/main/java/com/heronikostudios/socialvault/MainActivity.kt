package com.heronikostudios.socialvault

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.SoundEffectConstants
import kotlin.math.hypot
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import java.io.ByteArrayInputStream
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.graphics.Color
import com.heronikostudios.socialvault.databinding.ActivityMainBinding
import com.heronikostudios.socialvault.databinding.DialogEditFavouriteBinding
import com.heronikostudios.socialvault.databinding.LayoutCobaltSheetBinding
import com.heronikostudios.socialvault.databinding.LayoutDownloadImagesSheetBinding
import com.heronikostudios.socialvault.databinding.LayoutStoryDownloadSheetBinding
import com.heronikostudios.socialvault.databinding.LayoutFavouritesSheetBinding
import com.heronikostudios.socialvault.databinding.LayoutTabSwitcherSheetBinding
import java.util.UUID

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PRIVACY_INJECTION_SCRIPT =
            """(function(){try{if(!window.__gpc_injected){window.__gpc_injected=true;Object.defineProperty(navigator,'globalPrivacyControl',{value:true,writable:false,configurable:false});Object.defineProperty(navigator,'doNotTrack',{value:'1',writable:false,configurable:false});}if(!document.getElementById('__sv_safe_area_fix')){const s=document.createElement('style');s.id='__sv_safe_area_fix';s.textContent=':root { --safe-area-inset-bottom: 0px !important; --sab: 0px !important; }';(document.head||document.documentElement).appendChild(s);}if(window.visualViewport&&!window.__sv_viewport_bound){window.__sv_viewport_bound=true;window.visualViewport.addEventListener('resize',function(){var a=document.activeElement;if(a&&(a.tagName==='INPUT'||a.tagName==='TEXTAREA'||a.isContentEditable)){if(a.scrollIntoViewIfNeeded){a.scrollIntoViewIfNeeded();}else{a.scrollIntoView({block:'nearest'});}}});}}catch(e){}})();"""
        private const val DETECT_VIDEO_ORIENTATION_SCRIPT =
            """(function(){try{var v=document.fullscreenElement||document.webkitFullscreenElement;if(!v||v.tagName!=='VIDEO'){var videos=document.getElementsByTagName('video');for(var i=0;i<videos.length;i++){if(!videos[i].paused&&videos[i].videoWidth>0&&videos[i].videoHeight>0){v=videos[i];break;}}if(!v&&videos.length>0&&videos[0].videoWidth>0&&videos[0].videoHeight>0){v=videos[0];}}if(v&&v.videoWidth>0&&v.videoHeight>0){return (v.videoWidth>=v.videoHeight)?'landscape':'portrait';}}catch(e){}return '';})();"""
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var platformManager: PlatformManager
    private val tabManager = TabManager()

    private lateinit var platformAdapter: PlatformAdapter
    private lateinit var favouritesManager: FavouritesManager
    private lateinit var dashboardFavouriteAdapter: FavouriteAdapter

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = window.attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            window.attributes = params
        }

        platformManager = PlatformManager(this)
        favouritesManager = FavouritesManager(this)
        favouritesManager.addChangeListener {
            runOnUiThread {
                updateDashboardFavourites()
                updateFavouriteButtonState()
            }
        }
        applySecureScreenMode(platformManager.isSecureScreenEnabled())

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            WebView.startSafeBrowsing(this) { _ -> }
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.START_SAFE_BROWSING)) {
            WebViewCompat.startSafeBrowsing(this) { _ -> }
        }

        Thread { MetadataStripper.cleanOldCache(this) }.start()

        setupInsets()
        setupDashboard()
        setupBottomNav()
        setupBackNavigation()
        setupTabListener()

        setupFullScreenControls()

        showDashboard()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private var cachedCutoutTop = 0
    private var isImeVisible: Boolean = false

    private fun updateBottomNavVisibility() {
        val shouldShow = !isImeVisible && !isFullScreenMode && customView == null
        binding.bottomNavBar.visibility = if (shouldShow) View.VISIBLE else View.GONE
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val directCutoutTop = maxOf(cutout.top, insets.displayCutout?.safeInsetTop ?: 0)
            if (directCutoutTop > 0) {
                cachedCutoutTop = maxOf(cachedCutoutTop, directCutoutTop)
            }

            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val previouslyImeVisible = isImeVisible
            isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime()) || (imeInsets.bottom > navBars.bottom)
            updateBottomNavVisibility()

            if (!previouslyImeVisible && isImeVisible) {
                view.postDelayed({
                    tabManager.activeTab?.webView?.evaluateJavascript(
                        """(function(){var a=document.activeElement;if(a&&(a.tagName==='INPUT'||a.tagName==='TEXTAREA'||a.isContentEditable)){if(a.scrollIntoViewIfNeeded){a.scrollIntoViewIfNeeded();}else{a.scrollIntoView({block:'nearest'});}}})();""",
                        null
                    )
                }, 150)
            }

            if (isFullScreenMode && tabManager.activeTab != null) {
                val topPadding = if (directCutoutTop > 0) directCutoutTop else cachedCutoutTop
                val leftPadding = maxOf(navBars.left, cutout.left)
                val rightPadding = maxOf(navBars.right, cutout.right)
                val bottomPadding = if (isImeVisible) {
                    maxOf(imeInsets.bottom, navBars.bottom, cutout.bottom)
                } else {
                    maxOf(navBars.bottom, cutout.bottom)
                }
                view.setPadding(leftPadding, topPadding, rightPadding, bottomPadding)
            } else {
                val topPadding = systemBars.top
                val leftPadding = systemBars.left
                val rightPadding = systemBars.right
                val bottomPadding = if (isImeVisible) {
                    maxOf(imeInsets.bottom, systemBars.bottom)
                } else {
                    systemBars.bottom
                }
                view.setPadding(leftPadding, topPadding, rightPadding, bottomPadding)
            }
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.webViewContainer) { _, _ ->
            WindowInsetsCompat.CONSUMED
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
                    platformManager.movePlatform(fromPos, toPos, persistImmediately = false)
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
                platformManager.persistPlatformsOrder()
            }
        })

        binding.rvPlatformsGrid.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = platformAdapter
            setHasFixedSize(true)
        }
        touchHelper.attachToRecyclerView(binding.rvPlatformsGrid)

        binding.btnResetDefaultsDashboard.setOnClickListener {
            showResetDefaultsDialog()
        }

        dashboardFavouriteAdapter = FavouriteAdapter(
            favourites = emptyList(),
            platformManager = platformManager,
            onFavouriteClick = { fav ->
                openFavourite(fav)
            },
            onFavouriteOptionsClick = { fav, anchorView ->
                showFavouriteItemOptions(fav, anchorView, onUpdated = {
                    updateDashboardFavourites()
                })
            }
        )

        binding.rvDashboardFavourites.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = dashboardFavouriteAdapter
            setHasFixedSize(true)
        }

        binding.btnViewAllFavourites.setOnClickListener {
            showFavouritesSheet()
        }

        binding.btnFavourite.setOnClickListener {
            toggleFavouriteCurrentPage()
        }

        binding.btnFavourite.setOnLongClickListener {
            val currentTab = tabManager.activeTab
            val currentUrl = currentTab?.webView?.url ?: currentTab?.currentUrl
            val fav = favouritesManager.getFavouriteForUrl(currentUrl)
            if (fav != null) {
                showEditFavouriteDialog(fav) {
                    updateDashboardFavourites()
                    updateFavouriteButtonState()
                }
            } else {
                showFavouritesSheet()
            }
            true
        }

        updateDashboardFavourites()

        binding.btnShareLink.setOnClickListener {
            shareCurrentLink(copyOnly = false)
        }

        binding.btnShareLink.setOnLongClickListener {
            shareCurrentLink(copyOnly = true)
            true
        }

        binding.btnOpenLink.setOnClickListener {
            showOpenUrlDialog()
        }

        binding.btnAddPlatform.setOnClickListener {
            showAddPlatformDialog()
        }

        binding.btnMoreMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.menu_dashboard, popup.menu)
            val isTabOpen = (tabManager.activeTab != null)
            popup.menu.findItem(R.id.menu_share_link)?.isVisible = isTabOpen
            popup.menu.findItem(R.id.menu_copy_link)?.isVisible = isTabOpen
            popup.menu.findItem(R.id.menu_strip_metadata)?.isChecked =
                platformManager.isStripMetadataEnabled()
            popup.menu.findItem(R.id.menu_polish_urls)?.isChecked =
                platformManager.isPolishUrlsEnabled()
            popup.menu.findItem(R.id.menu_block_trackers)?.isChecked =
                platformManager.isBlockTrackersEnabled()
            popup.menu.findItem(R.id.menu_secure_screen)?.isChecked =
                platformManager.isSecureScreenEnabled()
            popup.menu.findItem(R.id.menu_full_screen)?.apply {
                isChecked = if (isTabOpen) isFullScreenMode else platformManager.isFullScreenEnabled()
            }
            val currentUrl = tabManager.activeTab?.webView?.url ?: tabManager.activeTab?.currentUrl
            val isFav = favouritesManager.isFavourite(currentUrl)
            popup.menu.findItem(R.id.menu_toggle_favourite)?.apply {
                isVisible = isTabOpen
                title = if (isFav) getString(R.string.action_unfavourite) else getString(R.string.action_favourite)
                setIcon(if (isFav) R.drawable.ic_star else R.drawable.ic_star_border)
            }
            popup.menu.findItem(R.id.menu_download_images)?.isVisible = isTabOpen
            popup.menu.findItem(R.id.menu_download_video)?.isVisible = isTabOpen
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_share_link -> {
                        shareCurrentLink(copyOnly = false)
                        true
                    }
                    R.id.menu_copy_link -> {
                        shareCurrentLink(copyOnly = true)
                        true
                    }
                    R.id.menu_toggle_favourite -> {
                        toggleFavouriteCurrentPage()
                        true
                    }
                    R.id.menu_view_favourites -> {
                        showFavouritesSheet()
                        true
                    }
                    R.id.menu_open_url -> {
                        showOpenUrlDialog()
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
                    R.id.menu_block_trackers -> {
                        val newState = !platformManager.isBlockTrackersEnabled()
                        platformManager.setBlockTrackersEnabled(newState)
                        item.isChecked = newState
                        val msg = if (newState) getString(R.string.toast_trackers_enabled) else getString(R.string.toast_trackers_disabled)
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.menu_secure_screen -> {
                        val newState = !platformManager.isSecureScreenEnabled()
                        platformManager.setSecureScreenEnabled(newState)
                        item.isChecked = newState
                        applySecureScreenMode(newState)
                        val msg = if (newState) getString(R.string.toast_secure_screen_enabled) else getString(R.string.toast_secure_screen_disabled)
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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
                    R.id.menu_download_images -> {
                        tabManager.activeTab?.webView?.let { extractAndDownloadImages(it) }
                        true
                    }
                    R.id.menu_download_video -> {
                        tabManager.activeTab?.webView?.let { extractAndDownloadVideo(it) }
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
        binding.btnShareLink.visibility = View.GONE
        binding.btnFavourite.visibility = View.GONE
        binding.btnOpenLink.visibility = View.VISIBLE
        binding.btnAddPlatform.visibility = View.VISIBLE
        binding.tvHeaderTitle.text = getString(R.string.app_name)
        binding.tvHeaderSubtitle.text = "Private & Sandboxed Social Hub"
        binding.progressBar.visibility = View.GONE
        updateNavButtons()
        updateDashboardFavourites()
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
        binding.btnShareLink.visibility = View.VISIBLE
        binding.btnFavourite.visibility = View.VISIBLE
        binding.btnOpenLink.visibility = View.GONE
        binding.btnAddPlatform.visibility = View.GONE
        binding.tvHeaderTitle.text = tab.platform.name
        binding.tvHeaderSubtitle.text = tab.platform.url
        updateNavButtons()
        updateFavouriteButtonState()
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
        ViewCompat.setOnApplyWindowInsetsListener(webView) { _, _ ->
            WindowInsetsCompat.CONSUMED
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
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(webView.settings, true)
        }

        cookieManager.apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, platformManager.isThirdPartyCookiesEnabled())
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

        var lastTouchX = 0f
        var lastTouchY = 0f
        webView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            false
        }

        webView.setOnLongClickListener { v ->
            val result = (v as? WebView)?.hitTestResult
            val extra = result?.extra
            val handled = when (result?.type) {
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
                        val isImg = DownloadHelper.isImageUrl(extra)
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

            if (handled) return@setOnLongClickListener true

            val currentUrl = webView.url ?: ""
            val isStory = currentUrl.contains("/stories/")
            if (isStory) {
                detectAndDownloadActiveMedia(webView, platform)
                return@setOnLongClickListener true
            }

            // Fallback for modern sites with overlay divs / touch blockers:
            // Query elements at touch point directly using elementsFromPoint(cssX, cssY)
            val density = resources.displayMetrics.density
            val cssX = (lastTouchX / density).toInt()
            val cssY = (lastTouchY / density).toInt()
            val js = """
                (function(x, y) {
                    try {
                        var elements = document.elementsFromPoint(x, y);
                        if (!elements || elements.length === 0) return null;
                        for (var i = 0; i < elements.length; i++) {
                            var el = elements[i];
                            var tag = el.tagName.toUpperCase();
                            if (tag === 'IMG') {
                                var isrc = el.currentSrc || el.src;
                                if (isrc && isrc.indexOf('http') === 0) return JSON.stringify({ url: isrc, isImage: true });
                            }
                            if (tag === 'VIDEO') {
                                var vsrc = el.currentSrc || el.src || (el.querySelector('source') ? el.querySelector('source').src : '');
                                if (vsrc && vsrc.indexOf('http') === 0) return JSON.stringify({ url: vsrc, isImage: false });
                            }
                            var blocked = ['BODY', 'HTML', 'MAIN', 'SECTION', 'ARTICLE', 'NAV', 'HEADER', 'FOOTER', 'FORM'];
                            if (blocked.indexOf(tag) === -1 && el.children && el.children.length <= 2) {
                                var cImg = el.querySelector(':scope > img');
                                if (cImg) {
                                    var cisrc = cImg.currentSrc || cImg.src;
                                    if (cisrc && cisrc.indexOf('http') === 0) return JSON.stringify({ url: cisrc, isImage: true });
                                }
                                var cVid = el.querySelector(':scope > video');
                                if (cVid) {
                                    var cvsrc = cVid.currentSrc || cVid.src || (cVid.querySelector('source') ? cVid.querySelector('source').src : '');
                                    if (cvsrc && cvsrc.indexOf('http') === 0) return JSON.stringify({ url: cvsrc, isImage: false });
                                }
                            }
                            var bg = window.getComputedStyle(el).backgroundImage;
                            if (bg && bg.indexOf('url(') !== -1) {
                                var m = bg.match(/url\(['"]?(https?:\/\/[^'"]+)['"]?\)/);
                                if (m && m[1]) return JSON.stringify({ url: m[1], isImage: true });
                            }
                        }
                    } catch(e) {}
                    return null;
                })($cssX, $cssY);
            """.trimIndent()

            webView.evaluateJavascript(js) { res ->
                val clean = res?.trim('"', ' ', '\n')
                if (!clean.isNullOrBlank() && clean != "null") {
                    try {
                        val obj = org.json.JSONObject(clean.replace("\\\"", "\""))
                        val url = obj.optString("url", "")
                        val isImage = obj.optBoolean("isImage", true)
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            DownloadHelper.showMediaContextMenu(
                                context = this@MainActivity,
                                mediaUrl = url,
                                isImage = isImage,
                                userAgent = webView.settings.userAgentString,
                                cookieManager = cookieManager,
                                onOpenInNewTab = { mediaUrl ->
                                    openMediaInNewTab(mediaUrl)
                                }
                            )
                        }
                    } catch (_: Exception) {
                        if (clean.startsWith("http://") || clean.startsWith("https://")) {
                            DownloadHelper.showMediaContextMenu(
                                context = this@MainActivity,
                                mediaUrl = clean,
                                isImage = true,
                                userAgent = webView.settings.userAgentString,
                                cookieManager = cookieManager,
                                onOpenInNewTab = { mediaUrl ->
                                    openMediaInNewTab(mediaUrl)
                                }
                            )
                        }
                    }
                }
            }
            true
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
                updateBottomNavVisibility()

                applyFullscreenOrientation(platform, webView)
                setSystemBarsVisible(false)
                showFullScreenControls(true)
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

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                // Explicit defense-in-depth: disallow web geolocation requests unconditionally
                callback?.invoke(origin, false, false)
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                // Explicit defense-in-depth: disallow web camera, mic, and sensor permissions
                request?.deny()
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

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (platformManager.isBlockTrackersEnabled()) {
                    val uri = request?.url
                    if (uri != null && TrackerBlocker.isTracker(uri, platform)) {
                        return WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            200,
                            "OK",
                            emptyMap(),
                            ByteArrayInputStream(TrackerBlocker.EMPTY_BYTES)
                        )
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            private fun injectPrivacyControls(view: WebView?) {
                view?.evaluateJavascript(PRIVACY_INJECTION_SCRIPT, null)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                injectPrivacyControls(view)
                if (tabManager.activeTab?.webView == view) {
                    binding.progressBar.visibility = View.VISIBLE
                    updateNavButtons()
                    updateFavouriteButtonState()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                injectPrivacyControls(view)
                if (!url.isNullOrBlank()) {
                    tabManager.tabs.find { it.webView == view }?.currentUrl = url
                }
                if (tabManager.activeTab?.webView == view) {
                    binding.progressBar.visibility = View.GONE
                    updateNavButtons()
                    updateFavouriteButtonState()
                }
            }
        }

        return webView
    }

    private fun applyFullscreenOrientation(platform: Platform, webView: WebView) {
        val currentUrl = webView.url ?: platform.url
        when (FullscreenOrientationHelper.determineTargetOrientation(platform.id, currentUrl)) {
            ScreenOrientationTarget.PORTRAIT -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            ScreenOrientationTarget.SENSOR_LANDSCAPE -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            ScreenOrientationTarget.DYNAMIC_EVALUATION -> {
                webView.evaluateJavascript(DETECT_VIDEO_ORIENTATION_SCRIPT) { rawResult ->
                    if (customView == null) return@evaluateJavascript
                    val result = rawResult?.trim('"', ' ', '\'')?.lowercase()
                    when (result) {
                        "portrait" -> {
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                        "landscape" -> {
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                        else -> {
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }
                }
            }
        }
    }

    private fun hideCustomView() {
        val view = customView ?: return
        binding.customViewContainer.apply {
            removeView(view)
            visibility = View.GONE
        }

        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (isFullScreenMode && tabManager.activeTab != null) {
            applyFullScreenMode(true)
        } else {
            binding.topBar.visibility = View.VISIBLE
            updateBottomNavVisibility()
            setSystemBarsVisible(true)
            showFullScreenControls(false)
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
                    showDashboard()
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
            .setTitle(R.string.dialog_reset_defaults_title)
            .setMessage(R.string.dialog_reset_defaults_msg)
            .setPositiveButton(R.string.btn_reset) { _, _ ->
                val defaults = platformManager.resetToDefaults()
                platformAdapter.updatePlatforms(defaults)
                Toast.makeText(this, R.string.toast_dashboard_reset, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun applySecureScreenMode(enabled: Boolean) {
        if (enabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun applyFullScreenMode(enabled: Boolean, showToast: Boolean = false) {
        isFullScreenMode = enabled
        if (enabled && tabManager.activeTab != null) {
            binding.topBar.visibility = View.GONE
            updateBottomNavVisibility()
            showFullScreenControls(true)
            // Keep the system navigation bar (bottom buttons) visible while hiding status bar
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.statusBars())
                show(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            ViewCompat.requestApplyInsets(binding.rootLayout)
            if (showToast) {
                Toast.makeText(this, R.string.toast_full_screen_enabled, Toast.LENGTH_SHORT).show()
            }
        } else {
            if (customView == null) {
                binding.topBar.visibility = View.VISIBLE
                updateBottomNavVisibility()
                showFullScreenControls(false)
                setSystemBarsVisible(true)
                ViewCompat.requestApplyInsets(binding.rootLayout)
            }
            if (showToast) {
                Toast.makeText(this, R.string.toast_full_screen_disabled, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFullScreenControls(visible: Boolean) {
        if (visible) {
            binding.cardFullScreenControls.apply {
                translationX = 0f
                translationY = 0f
                alpha = 0.45f
                visibility = View.VISIBLE
            }
        } else {
            if (!isFullScreenMode && customView == null) {
                binding.cardFullScreenControls.visibility = View.GONE
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFullScreenControls() {
        val card = binding.cardFullScreenControls
        var dX = 0f
        var dY = 0f
        var isDragging = false
        var startX = 0f
        var startY = 0f
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        card.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    startX = event.rawX
                    startY = event.rawY
                    isDragging = false
                    view.animate().alpha(1.0f).setDuration(150).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val distance = hypot(event.rawX - startX, event.rawY - startY)
                    if (distance > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val parent = view.parent as? View
                        if (parent != null && parent.width > view.width && parent.height > view.height) {
                            val minX = 0f
                            val maxX = (parent.width - view.width).toFloat()
                            val minY = 0f
                            val maxY = (parent.height - view.height).toFloat()
                            view.x = (event.rawX + dX).coerceIn(minX, maxX)
                            view.y = (event.rawY + dY).coerceIn(minY, maxY)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.animate().alpha(0.45f).setDuration(300).start()
                    if (!isDragging) {
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                        if (event.x < view.width / 2f) {
                            handleFullScreenDownload()
                        } else {
                            handleFullScreenExit()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.animate().alpha(0.45f).setDuration(300).start()
                    true
                }
                else -> false
            }
        }
    }

    private fun handleFullScreenExit() {
        if (customView != null) {
            hideCustomView()
        } else if (isFullScreenMode) {
            platformManager.setFullScreenEnabled(false)
            applyFullScreenMode(false, showToast = true)
        }
    }

    private fun handleFullScreenDownload() {
        val activeTab = tabManager.activeTab ?: return
        val webView = activeTab.webView
        val platform = activeTab.platform
        val currentUrl = webView.url ?: activeTab.currentUrl ?: ""

        if (platform?.id == "youtube" || YouTubeStreamHelper.isYouTubeVideoUrl(currentUrl)) {
            handleYouTubeDownload(currentUrl)
            return
        }

        if (platform?.id == "x" || TwitterStreamHelper.isTwitterUrl(currentUrl)) {
            extractAndDownloadVideo(webView)
            return
        }

        detectAndDownloadActiveMedia(webView, platform)
    }

    private fun detectAndDownloadActiveMedia(webView: WebView, platform: Platform?) {
        Toast.makeText(this, R.string.toast_detecting_media, Toast.LENGTH_SHORT).show()

        webView.evaluateJavascript(ImageExtractorHelper.DETECT_ACTIVE_MEDIA_SCRIPT) { rawJson ->
            val activeMedia = ImageExtractorHelper.parseActiveMedia(rawJson)
            if (activeMedia != null && (activeMedia.hasVideo || activeMedia.hasImage)) {
                showStoryDownloadSheet(activeMedia, platform, webView)
            } else {
                extractAndDownloadImages(webView)
            }
        }
    }

    private fun showStoryDownloadSheet(
        activeMedia: ActiveMedia,
        platform: Platform?,
        webView: WebView
    ) {
        val sheetBinding = LayoutStoryDownloadSheetBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheetBinding.root)

        val isInstagram = platform?.id == "instagram" || (webView.url ?: "").contains("instagram.com")
        val cookieManager = platform?.let { PlatformStorageManager.getCookieManagerForPlatform(it) }
            ?: CookieManager.getInstance()
        val userAgent = webView.settings.userAgentString

        // Configure Video Option
        if (activeMedia.hasVideo && !activeMedia.videoUrl.isNullOrBlank()) {
            sheetBinding.cardDownloadVideo.isEnabled = true
            sheetBinding.cardDownloadVideo.alpha = 1.0f
            sheetBinding.tvVideoSubtitle.text = getString(R.string.desc_download_story_video)
            sheetBinding.cardDownloadVideo.setOnClickListener {
                dialog.dismiss()
                val filename = if (isInstagram) {
                    ImageExtractorHelper.generateStoryFileName("instagram", isVideo = true)
                } else {
                    val prefix = platform?.name?.replace(" ", "") ?: "Media"
                    "${prefix}_video_${System.currentTimeMillis()}.mp4"
                }
                Toast.makeText(this, R.string.toast_downloading_story_video, Toast.LENGTH_SHORT).show()
                DownloadHelper.downloadFile(
                    context = this,
                    url = activeMedia.videoUrl,
                    userAgent = userAgent,
                    cookieManager = cookieManager,
                    customFileName = filename
                )
            }
        } else {
            sheetBinding.cardDownloadVideo.isEnabled = false
            sheetBinding.cardDownloadVideo.alpha = 0.45f
            sheetBinding.tvVideoSubtitle.text = getString(R.string.desc_no_story_video)
            sheetBinding.ivVideoArrow.visibility = View.INVISIBLE
        }

        // Configure Image Option
        if (activeMedia.hasImage && !activeMedia.imageUrl.isNullOrBlank()) {
            sheetBinding.cardDownloadImage.isEnabled = true
            sheetBinding.cardDownloadImage.alpha = 1.0f
            sheetBinding.tvImageSubtitle.text = getString(R.string.desc_download_story_photo)
            sheetBinding.cardDownloadImage.setOnClickListener {
                dialog.dismiss()
                val format = DownloadHelper.inferMediaFormat(activeMedia.imageUrl, null, true)
                val ext = format?.first ?: "jpg"
                val filename = if (isInstagram) {
                    ImageExtractorHelper.generateStoryFileName("instagram", isVideo = false, ext = ext)
                } else {
                    val prefix = platform?.name?.replace(" ", "") ?: "Media"
                    "${prefix}_photo_${System.currentTimeMillis()}.$ext"
                }
                Toast.makeText(this, R.string.toast_downloading_story_photo, Toast.LENGTH_SHORT).show()
                DownloadHelper.downloadFile(
                    context = this,
                    url = activeMedia.imageUrl,
                    userAgent = userAgent,
                    cookieManager = cookieManager,
                    customFileName = filename
                )
            }
        } else {
            sheetBinding.cardDownloadImage.isEnabled = false
            sheetBinding.cardDownloadImage.alpha = 0.45f
            sheetBinding.tvImageSubtitle.text = getString(R.string.desc_no_story_photo)
            sheetBinding.ivImageArrow.visibility = View.INVISIBLE
        }

        sheetBinding.btnStorySheetClose.setOnClickListener {
            dialog.dismiss()
        }

        sheetBinding.btnScanAllImages.setOnClickListener {
            dialog.dismiss()
            extractAndDownloadImages(webView)
        }

        dialog.show()
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
        val currentTab = tabManager.activeTab
        val currentUrl = webView.url ?: currentTab?.currentUrl ?: ""
        val isYouTube = currentTab?.platform?.id == "youtube" ||
                currentUrl.contains("youtube.com", ignoreCase = true) ||
                currentUrl.contains("youtu.be", ignoreCase = true)
        val isTwitter = currentTab?.platform?.id == "x" ||
                TwitterStreamHelper.isTwitterUrl(currentUrl)

        if (isYouTube) {
            webView.evaluateJavascript("window.location.href") { locResult ->
                val jsUrl = locResult?.trim('"', ' ', '\n')?.takeIf { it.startsWith("http") }
                val effectiveUrl = jsUrl ?: webView.url ?: currentTab?.currentUrl ?: ""
                handleYouTubeDownload(effectiveUrl)
            }
            return
        }

        if (isTwitter) {
            val twitterJs = """
                (function() {
                    var videos = document.getElementsByTagName('video');
                    function findStatusLink(el) {
                        if (!el) return null;
                        var article = el.closest('article');
                        if (article) {
                            var timeEl = article.querySelector('time');
                            var timeA = timeEl ? timeEl.closest('a') : null;
                            if (timeA && timeA.href && timeA.href.indexOf('/status/') !== -1) {
                                return timeA.href;
                            }
                            var anyStatusA = article.querySelector('a[href*="/status/"]');
                            if (anyStatusA && anyStatusA.href) {
                                return anyStatusA.href;
                            }
                        }
                        return null;
                    }

                    // 1. Actively playing video
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        if (!v.paused && v.currentTime > 0 && !v.ended) {
                            var link = findStatusLink(v);
                            if (link) return link;
                        }
                    }

                    // 2. Visible video in viewport
                    var winHeight = window.innerHeight || document.documentElement.clientHeight;
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        var rect = v.getBoundingClientRect();
                        if (rect.bottom > 0 && rect.top < winHeight) {
                            var link = findStatusLink(v);
                            if (link) return link;
                        }
                    }

                    // 3. Any video inside an article
                    for (var i = 0; i < videos.length; i++) {
                        var link = findStatusLink(videos[i]);
                        if (link) return link;
                    }

                    // 4. Current page URL if it contains a status permalink
                    if (window.location.href.indexOf('/status/') !== -1) {
                        return window.location.href;
                    }

                    return window.location.href;
                })();
            """.trimIndent()

            webView.evaluateJavascript(twitterJs) { result ->
                val jsUrl = result?.trim('"', ' ', '\n')?.takeIf { it.startsWith("http") }
                val effectiveUrl = jsUrl ?: webView.url ?: currentTab?.currentUrl ?: ""
                handleTwitterDownload(effectiveUrl)
            }
            return
        }

        webView.evaluateJavascript(ImageExtractorHelper.DETECT_ACTIVE_MEDIA_SCRIPT) { rawJson ->
            val activeMedia = ImageExtractorHelper.parseActiveMedia(rawJson)
            if (activeMedia != null && activeMedia.hasVideo && !activeMedia.videoUrl.isNullOrBlank()) {
                DownloadHelper.downloadFile(
                    context = this@MainActivity,
                    url = activeMedia.videoUrl,
                    userAgent = webView.settings.userAgentString
                )
            } else {
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
                        val pageUrl = webView.url ?: currentTab?.currentUrl
                        if (!pageUrl.isNullOrBlank() && (pageUrl.startsWith("http://") || pageUrl.startsWith("https://"))) {
                            Toast.makeText(this@MainActivity, R.string.no_video_found_try_cobalt, Toast.LENGTH_SHORT).show()
                            openInAppCobaltDownloader(pageUrl)
                        } else {
                            Toast.makeText(this@MainActivity, "No downloadable video stream found on this page", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun extractAndDownloadImages(webView: WebView) {
        val currentTab = tabManager.activeTab
        val platform = currentTab?.platform
        Toast.makeText(this, R.string.toast_extracting_images, Toast.LENGTH_SHORT).show()

        webView.evaluateJavascript(ImageExtractorHelper.EXTRACTION_SCRIPT) { jsonResult ->
            val images = ImageExtractorHelper.parseExtractedImages(jsonResult, platform?.id)
            if (images.isNotEmpty()) {
                showDownloadImagesSheet(images, platform, webView)
            } else {
                Toast.makeText(this, R.string.toast_no_images_found, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDownloadImagesSheet(
        images: List<ExtractedImage>,
        platform: Platform?,
        webView: WebView
    ) {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = LayoutDownloadImagesSheetBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val cookieManager = platform?.let { PlatformStorageManager.getCookieManagerForPlatform(it) }
            ?: CookieManager.getInstance()
        val userAgent = webView.settings.userAgentString

        sheetBinding.tvImagesSheetSubtitle.text = if (images.size == 1) {
            "Found 1 image on this page"
        } else {
            "Found ${images.size} images • Tap to select or bulk download"
        }

        fun updateDownloadButtonText(selectedCount: Int) {
            if (selectedCount == images.size && images.size > 1) {
                sheetBinding.btnDownloadSelected.text = getString(R.string.btn_download_all, selectedCount)
                sheetBinding.btnToggleSelectAll.text = getString(R.string.btn_deselect_all)
            } else {
                sheetBinding.btnDownloadSelected.text = getString(R.string.btn_download_selected, selectedCount)
                sheetBinding.btnToggleSelectAll.text = if (selectedCount == 0) {
                    getString(R.string.btn_select_all)
                } else {
                    getString(R.string.btn_deselect_all)
                }
            }
            sheetBinding.btnDownloadSelected.isEnabled = selectedCount > 0
        }

        lateinit var adapter: ExtractedImageAdapter

        adapter = ExtractedImageAdapter(
            images = images,
            cookieManager = cookieManager,
            userAgent = userAgent,
            onSelectionChanged = {
                updateDownloadButtonText(adapter.selectedImages.size)
            },
            onSingleDownloadClick = { item, position ->
                val safeName = ImageExtractorHelper.generateSafeFileName(
                    item = item,
                    platformId = platform?.id,
                    index = position,
                    total = images.size
                )
                DownloadHelper.downloadFile(
                    context = this@MainActivity,
                    url = item.url,
                    userAgent = userAgent,
                    cookieManager = cookieManager,
                    customFileName = safeName,
                    isImage = true
                )
            }
        )

        sheetBinding.rvExtractedImages.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = adapter
            setHasFixedSize(true)
        }

        updateDownloadButtonText(images.size)

        sheetBinding.btnToggleSelectAll.setOnClickListener {
            val allSelected = adapter.selectedImages.size == images.size
            adapter.selectAll(!allSelected)
        }

        sheetBinding.btnCancelDownload.setOnClickListener {
            dialog.dismiss()
        }

        sheetBinding.btnDownloadSelected.setOnClickListener {
            val toDownload = adapter.selectedImages
            if (toDownload.isEmpty()) return@setOnClickListener

            val total = toDownload.size
            Toast.makeText(
                this,
                getString(R.string.toast_downloading_images, total),
                Toast.LENGTH_SHORT
            ).show()

            for ((idx, item) in toDownload.withIndex()) {
                val safeName = ImageExtractorHelper.generateSafeFileName(
                    item = item,
                    platformId = platform?.id,
                    index = idx,
                    total = total
                )
                DownloadHelper.downloadFile(
                    context = this@MainActivity,
                    url = item.url,
                    userAgent = userAgent,
                    cookieManager = cookieManager,
                    customFileName = safeName,
                    isImage = true
                )
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun handleYouTubeDownload(videoUrl: String) {
        val cleanUrl = if (platformManager.isPolishUrlsEnabled()) {
            UrlPolisher.polishUrl(videoUrl).polishedUrl
        } else {
            videoUrl
        }

        if (!YouTubeStreamHelper.isYouTubeVideoUrl(cleanUrl)) {
            Toast.makeText(this, R.string.yt_no_video_open, Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, R.string.yt_extracting_streams, Toast.LENGTH_SHORT).show()

        Thread {
            val result = YouTubeStreamHelper.extractStreams(cleanUrl)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result.isSuccess) {
                    val items = result.getOrNull()
                    if (!items.isNullOrEmpty()) {
                        showYouTubeStreamSelectionDialog(items, cleanUrl)
                    } else {
                        showYouTubeDownloadOptions(cleanUrl, isFallback = true)
                    }
                } else {
                    showYouTubeDownloadOptions(cleanUrl, isFallback = true)
                }
            }
        }.start()
    }

    private fun showYouTubeStreamSelectionDialog(items: List<YouTubeStreamItem>, videoUrl: String) {
        val videoTitle = items.first().title
        val displayOptions = items.map { "${it.resolution} • ${it.formatName}" }.toMutableList()
        displayOptions.add(getString(R.string.yt_option_more_external))

        val titleText = if (videoTitle.isNotBlank() && videoTitle != "YouTube Video") {
            val truncatedTitle = if (videoTitle.length > 75) videoTitle.take(72).trimEnd() + "…" else videoTitle
            SpannableStringBuilder().apply {
                append(getString(R.string.yt_select_quality_title))
                append("\n")
                val start = length
                append(truncatedTitle)
                setSpan(
                    RelativeSizeSpan(0.75f),
                    start,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } else {
            getString(R.string.yt_select_quality_title)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(titleText)
            .setItems(displayOptions.toTypedArray()) { _, which ->
                if (which < items.size) {
                    val selectedItem = items[which]
                    DownloadHelper.downloadFile(
                        context = this@MainActivity,
                        url = selectedItem.url,
                        userAgent = tabManager.activeTab?.webView?.settings?.userAgentString,
                        mimeType = selectedItem.mimeType,
                        customFileName = selectedItem.safeFileName
                    )
                } else {
                    showYouTubeDownloadOptions(videoUrl, isFallback = false)
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showYouTubeDownloadOptions(videoUrl: String, isFallback: Boolean = false) {
        val cleanUrl = if (platformManager.isPolishUrlsEnabled()) {
            UrlPolisher.polishUrl(videoUrl).polishedUrl
        } else {
            videoUrl
        }

        val messageRes = if (isFallback) R.string.yt_extract_failed else R.string.yt_download_message

        val options = arrayOf(
            getString(R.string.yt_download_cobalt),
            getString(R.string.yt_download_external_app),
            getString(R.string.yt_download_copy_link)
        )

        val titleText = SpannableStringBuilder().apply {
            append(getString(R.string.yt_download_title))
            append("\n")
            val start = length
            append(getString(messageRes))
            setSpan(
                RelativeSizeSpan(0.75f),
                start,
                length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(titleText)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        openInAppCobaltDownloader(cleanUrl)
                    }
                    1 -> {
                        // Open with dedicated video downloader app (Seal, NewPipe, YTDLnis, etc.)
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl))
                        val chooser = Intent.createChooser(intent, getString(R.string.yt_download_chooser_title))
                        try {
                            startActivity(chooser)
                        } catch (e: Exception) {
                            Toast.makeText(this, "No compatible downloader app found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    2 -> {
                        // Copy clean link to clipboard
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("Clean YouTube Link", cleanUrl)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(this, R.string.toast_clean_link_copied, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openInAppCobaltDownloader(mediaUrl: String) {
        val cleanUrl = if (platformManager.isPolishUrlsEnabled()) {
            UrlPolisher.polishUrl(mediaUrl).polishedUrl
        } else {
            mediaUrl
        }

        val sheetDialog = BottomSheetDialog(this)
        val sheetBinding = LayoutCobaltSheetBinding.inflate(layoutInflater)
        sheetDialog.setContentView(sheetBinding.root)

        // Make the bottom sheet responsive (approx 82% of screen height)
        val displayMetrics = resources.displayMetrics
        val targetHeight = (displayMetrics.heightPixels * 0.82).toInt()
        sheetBinding.cobaltWebViewContainer.layoutParams.height = targetHeight

        sheetDialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isHideable = true
        }

        val webView = sheetBinding.cobaltWebView
        val profileName = "sv_profile_cobalt"
        val cookieManager = if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            val profile = ProfileStore.getInstance().getOrCreateProfile(profileName)
            WebViewCompat.setProfile(webView, profile.name)
            profile.cookieManager
        } else {
            CookieManager.getInstance()
        }

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
        }

        cookieManager.apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true) // Required for Cobalt web UI and Turnstile challenge
        }

        webView.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
            DownloadHelper.downloadFile(
                context = this@MainActivity,
                url = downloadUrl,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimeType,
                cookieManager = cookieManager
            )
            Toast.makeText(this@MainActivity, R.string.toast_cobalt_download_started, Toast.LENGTH_SHORT).show()
            sheetDialog.dismiss()
        }

        sheetBinding.btnCobaltClose.setOnClickListener {
            sheetDialog.dismiss()
        }

        sheetBinding.btnCobaltReload.setOnClickListener {
            webView.reload()
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress in 1..99) {
                    sheetBinding.cobaltProgressBar.visibility = View.VISIBLE
                    sheetBinding.cobaltProgressBar.progress = newProgress
                } else {
                    sheetBinding.cobaltProgressBar.visibility = View.GONE
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

                // If navigating to a direct media stream or download URL, intercept and download
                if (DownloadHelper.isMediaUrl(url)) {
                    DownloadHelper.downloadFile(
                        context = this@MainActivity,
                        url = url,
                        cookieManager = cookieManager
                    )
                    Toast.makeText(this@MainActivity, R.string.toast_cobalt_download_started, Toast.LENGTH_SHORT).show()
                    sheetDialog.dismiss()
                    return true
                }

                val host = uri.host?.lowercase().orEmpty()
                val configuredHost = try {
                    java.net.URI(platformManager.getCobaltInstanceUrl()).host?.lowercase().orEmpty()
                } catch (_: Exception) {
                    "cobalt.tools"
                }

                if (host.contains("cobalt.tools") || host == configuredHost || host.endsWith(".$configuredHost") || host.contains("cloudflare")) {
                    return false
                }

                // If Cobalt redirects or tunnels to a download CDN, intercept and download
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    DownloadHelper.downloadFile(
                        context = this@MainActivity,
                        url = url,
                        cookieManager = cookieManager
                    )
                    Toast.makeText(this@MainActivity, R.string.toast_cobalt_download_started, Toast.LENGTH_SHORT).show()
                    sheetDialog.dismiss()
                    return true
                }

                return false
            }
        }

        val baseInstance = platformManager.getCobaltInstanceUrl().trimEnd('/')
        val cobaltUrl = "$baseInstance/?u=" + Uri.encode(cleanUrl)
        webView.loadUrl(cobaltUrl)

        sheetDialog.setOnDismissListener {
            webView.stopLoading()
            webView.destroy()
        }

        sheetDialog.show()
    }

    private fun handleTwitterDownload(postUrl: String) {
        val cleanUrl = if (platformManager.isPolishUrlsEnabled()) {
            UrlPolisher.polishUrl(postUrl).polishedUrl
        } else {
            postUrl
        }

        val tweetId = TwitterStreamHelper.extractTweetId(cleanUrl)
        if (tweetId == null) {
            Toast.makeText(this, R.string.x_no_video_open, Toast.LENGTH_SHORT).show()
            showTwitterDownloadOptions(cleanUrl, isFallback = true)
            return
        }

        Toast.makeText(this, R.string.x_extracting_streams, Toast.LENGTH_SHORT).show()

        Thread {
            val result = TwitterStreamHelper.extractStreams(cleanUrl)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result.isSuccess) {
                    val items = result.getOrNull()
                    if (!items.isNullOrEmpty()) {
                        showTwitterStreamSelectionDialog(items, cleanUrl)
                    } else {
                        showTwitterDownloadOptions(cleanUrl, isFallback = true)
                    }
                } else {
                    showTwitterDownloadOptions(cleanUrl, isFallback = true)
                }
            }
        }.start()
    }

    private fun showTwitterStreamSelectionDialog(items: List<TwitterStreamItem>, postUrl: String) {
        val videoTitle = items.first().title
        val displayOptions = items.map { it.formatName }.toMutableList()
        displayOptions.add(getString(R.string.yt_option_more_external))

        val titleText = if (videoTitle.isNotBlank() && videoTitle != "X Post") {
            val truncatedTitle = if (videoTitle.length > 75) videoTitle.take(72).trimEnd() + "…" else videoTitle
            SpannableStringBuilder().apply {
                append(getString(R.string.x_select_quality_title))
                append("\n")
                val start = length
                append(truncatedTitle)
                setSpan(
                    RelativeSizeSpan(0.75f),
                    start,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } else {
            getString(R.string.x_select_quality_title)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(titleText)
            .setItems(displayOptions.toTypedArray()) { _, which ->
                if (which < items.size) {
                    val selectedItem = items[which]
                    DownloadHelper.downloadFile(
                        context = this@MainActivity,
                        url = selectedItem.url,
                        userAgent = tabManager.activeTab?.webView?.settings?.userAgentString,
                        mimeType = selectedItem.mimeType,
                        customFileName = selectedItem.safeFileName
                    )
                } else {
                    showTwitterDownloadOptions(postUrl, isFallback = false)
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showTwitterDownloadOptions(postUrl: String, isFallback: Boolean = false) {
        val cleanUrl = if (platformManager.isPolishUrlsEnabled()) {
            UrlPolisher.polishUrl(postUrl).polishedUrl
        } else {
            postUrl
        }

        val messageRes = if (isFallback) R.string.x_extract_failed else R.string.x_download_message

        val options = arrayOf(
            getString(R.string.x_download_cobalt),
            getString(R.string.x_download_external_app),
            getString(R.string.x_download_copy_link)
        )

        val titleText = SpannableStringBuilder().apply {
            append(getString(R.string.x_download_title))
            append("\n")
            val start = length
            append(getString(messageRes))
            setSpan(
                RelativeSizeSpan(0.75f),
                start,
                length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(titleText)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        openInAppCobaltDownloader(cleanUrl)
                    }
                    1 -> {
                        // Open with dedicated video downloader app (Seal, YTDLnis, etc.)
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl))
                        val chooser = Intent.createChooser(intent, getString(R.string.x_download_chooser_title))
                        try {
                            startActivity(chooser)
                        } catch (_: Exception) {
                            Toast.makeText(this, "No compatible downloader app found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    2 -> {
                        // Copy clean link to clipboard
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("Clean X Link", cleanUrl)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(this, R.string.toast_clean_link_copied, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun shareCurrentLink(copyOnly: Boolean = false) {
        val currentTab = tabManager.activeTab
        if (currentTab == null) {
            Toast.makeText(this, R.string.toast_no_link_to_share, Toast.LENGTH_SHORT).show()
            return
        }
        val currentUrl = currentTab.webView.url ?: currentTab.currentUrl
        if (currentUrl.isNullOrBlank()) {
            Toast.makeText(this, R.string.toast_no_link_to_share, Toast.LENGTH_SHORT).show()
            return
        }

        val urlToShare = if (platformManager.isPolishUrlsEnabled()) {
            UrlPolisher.polishUrl(currentUrl).polishedUrl
        } else {
            currentUrl
        }

        if (copyOnly) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Clean Link", urlToShare)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(this, R.string.toast_clean_link_copied, Toast.LENGTH_SHORT).show()
        } else {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, urlToShare)
                val pageTitle = currentTab.webView.title
                if (!pageTitle.isNullOrBlank()) {
                    putExtra(Intent.EXTRA_SUBJECT, pageTitle)
                }
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_link_chooser_title)))
        }
    }

    private fun updateDashboardFavourites() {
        val allFavourites = favouritesManager.getAllFavourites()
        if (allFavourites.isEmpty()) {
            binding.cardFavouritesEmpty.visibility = View.VISIBLE
            binding.rvDashboardFavourites.visibility = View.GONE
            binding.btnViewAllFavourites.visibility = View.GONE
        } else {
            binding.cardFavouritesEmpty.visibility = View.GONE
            binding.rvDashboardFavourites.visibility = View.VISIBLE
            val previewList = allFavourites.take(5)
            dashboardFavouriteAdapter.updateFavourites(previewList)
            binding.btnViewAllFavourites.visibility = if (allFavourites.size > 5) View.VISIBLE else View.GONE
        }
    }

    private fun updateFavouriteButtonState() {
        val currentTab = tabManager.activeTab
        if (currentTab == null || binding.dashboardView.visibility == View.VISIBLE) {
            binding.btnFavourite.visibility = View.GONE
            return
        }
        binding.btnFavourite.visibility = View.VISIBLE
        val currentUrl = currentTab.webView.url ?: currentTab.currentUrl
        val isFav = favouritesManager.isFavourite(currentUrl)
        if (isFav) {
            binding.btnFavourite.setImageResource(R.drawable.ic_star)
            binding.btnFavourite.setColorFilter(Color.parseColor("#EAB308"))
            binding.btnFavourite.contentDescription = getString(R.string.action_unfavourite)
        } else {
            binding.btnFavourite.setImageResource(R.drawable.ic_star_border)
            binding.btnFavourite.setColorFilter(getColor(R.color.on_surface))
            binding.btnFavourite.contentDescription = getString(R.string.action_favourite)
        }
    }

    private fun toggleFavouriteCurrentPage() {
        val currentTab = tabManager.activeTab
        if (currentTab == null) {
            Toast.makeText(this, "No active page to bookmark", Toast.LENGTH_SHORT).show()
            return
        }
        val currentUrl = currentTab.webView.url ?: currentTab.currentUrl
        if (currentUrl.isNullOrBlank()) {
            Toast.makeText(this, "No active page to bookmark", Toast.LENGTH_SHORT).show()
            return
        }

        if (favouritesManager.isFavourite(currentUrl)) {
            favouritesManager.removeFavouriteByUrl(currentUrl)
            Toast.makeText(this, R.string.toast_favourite_removed, Toast.LENGTH_SHORT).show()
        } else {
            val title = currentTab.webView.title ?: currentTab.title
            val platform = currentTab.platform
            favouritesManager.addFavourite(
                title = if (!title.isNullOrBlank()) title else platform.name,
                url = currentUrl,
                platform = platform
            )
            Toast.makeText(this, R.string.toast_favourite_added, Toast.LENGTH_SHORT).show()
        }
        updateFavouriteButtonState()
        updateDashboardFavourites()
    }

    private fun openFavourite(favourite: Favourite) {
        val platform = platformManager.getPlatformById(favourite.platformId)
            ?: platformManager.findMatchingPlatform(favourite.url)

        if (platform != null) {
            val existingTab = tabManager.tabs.find { it.platform.id == platform.id }
            if (existingTab != null) {
                switchToTab(existingTab)
                existingTab.webView.loadUrl(favourite.url)
            } else {
                openPlatformInNewTab(platform, favourite.url)
            }
        } else {
            val host = try {
                Uri.parse(favourite.url).host ?: ""
            } catch (_: Exception) {
                ""
            }
            val genericPlatform = Platform(
                id = favourite.platformId.ifBlank { "custom_" + UUID.randomUUID().toString().take(8) },
                name = favourite.platformName.ifBlank { "Web" },
                url = favourite.url,
                iconType = "globe",
                allowedDomains = if (host.isNotBlank()) listOf(host) else emptyList(),
                isCustom = true
            )
            openPlatformInNewTab(genericPlatform, favourite.url)
        }
    }

    private fun showFavouritesSheet() {
        val sheetDialog = BottomSheetDialog(this)
        val sheetBinding = LayoutFavouritesSheetBinding.inflate(layoutInflater)
        sheetDialog.setContentView(sheetBinding.root)

        val sheetAdapter = FavouriteAdapter(
            favourites = favouritesManager.getAllFavourites(),
            platformManager = platformManager,
            onFavouriteClick = { fav ->
                sheetDialog.dismiss()
                openFavourite(fav)
            },
            onFavouriteOptionsClick = { fav, anchorView ->
                showFavouriteItemOptions(fav, anchorView, onUpdated = {
                    updateFavouritesSheetState(sheetBinding)
                    updateDashboardFavourites()
                })
            }
        )

        sheetBinding.rvFavourites.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = sheetAdapter
        }

        sheetBinding.btnClearAllFavourites.setOnClickListener {
            if (favouritesManager.getFavouritesCount() == 0) return@setOnClickListener
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_clear_all_fav_title)
                .setMessage(R.string.dialog_clear_all_fav_msg)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_clear_all) { _, _ ->
                    favouritesManager.clearAll()
                    updateFavouritesSheetState(sheetBinding)
                    updateDashboardFavourites()
                    updateFavouriteButtonState()
                    Toast.makeText(this, "Favourites cleared", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        updateFavouritesSheetState(sheetBinding)
        sheetDialog.show()
    }

    private fun updateFavouritesSheetState(sheetBinding: LayoutFavouritesSheetBinding) {
        val list = favouritesManager.getAllFavourites()
        (sheetBinding.rvFavourites.adapter as? FavouriteAdapter)?.updateFavourites(list)
        if (list.isEmpty()) {
            sheetBinding.layoutEmptyFavourites.visibility = View.VISIBLE
            sheetBinding.rvFavourites.visibility = View.GONE
            sheetBinding.btnClearAllFavourites.visibility = View.GONE
        } else {
            sheetBinding.layoutEmptyFavourites.visibility = View.GONE
            sheetBinding.rvFavourites.visibility = View.VISIBLE
            sheetBinding.btnClearAllFavourites.visibility = View.VISIBLE
        }
    }

    private fun showFavouriteItemOptions(
        favourite: Favourite,
        anchorView: View,
        onUpdated: () -> Unit
    ) {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.menu_favourite_item, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_fav_open -> {
                    openFavourite(favourite)
                    true
                }
                R.id.action_fav_copy_link -> {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("Clean Link", favourite.url)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(this, R.string.toast_clean_link_copied, Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_fav_share -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, favourite.url)
                        if (favourite.title.isNotBlank()) {
                            putExtra(Intent.EXTRA_SUBJECT, favourite.title)
                        }
                    }
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share_link_chooser_title)))
                    true
                }
                R.id.action_fav_edit_title -> {
                    showEditFavouriteDialog(favourite) {
                        onUpdated()
                    }
                    true
                }
                R.id.action_fav_delete -> {
                    favouritesManager.removeFavourite(favourite.id)
                    Toast.makeText(this, R.string.toast_favourite_removed, Toast.LENGTH_SHORT).show()
                    onUpdated()
                    updateFavouriteButtonState()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showEditFavouriteDialog(
        favourite: Favourite,
        onSaved: (() -> Unit)? = null
    ) {
        val dialogBinding = DialogEditFavouriteBinding.inflate(layoutInflater)
        dialogBinding.etFavTitle.setText(favourite.title)
        dialogBinding.tvFavUrlPreview.text = favourite.url

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_edit_fav_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val newTitle = dialogBinding.etFavTitle.text?.toString()?.trim().orEmpty()
                if (newTitle.isNotBlank()) {
                    favouritesManager.updateTitle(favourite.id, newTitle)
                    Toast.makeText(this, R.string.toast_favourite_updated, Toast.LENGTH_SHORT).show()
                    onSaved?.invoke()
                }
            }
            .show()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isFullScreenMode || customView != null) {
            binding.cardFullScreenControls.translationX = 0f
            binding.cardFullScreenControls.translationY = 0f
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
