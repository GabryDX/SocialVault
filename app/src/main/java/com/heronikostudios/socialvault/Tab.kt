package com.heronikostudios.socialvault

import android.webkit.WebView

data class Tab(
    val id: String,
    val platform: Platform,
    val webView: WebView,
    var title: String = platform.name,
    var currentUrl: String = platform.url
)
