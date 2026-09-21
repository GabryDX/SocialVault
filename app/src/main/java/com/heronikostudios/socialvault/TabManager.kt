package com.heronikostudios.socialvault

import android.view.View

class TabManager {

    private val _tabs = mutableListOf<Tab>()
    val tabs: List<Tab> get() = _tabs

    var activeTab: Tab? = null
        private set

    var onTabsChangedListener: (() -> Unit)? = null

    fun addTab(tab: Tab): Tab {
        _tabs.add(tab)
        selectTab(tab.id)
        onTabsChangedListener?.invoke()
        return tab
    }

    fun selectTab(tabId: String): Tab? {
        val target = _tabs.find { it.id == tabId } ?: return null

        // Pause the previous active tab
        if (activeTab != null && activeTab?.id != target.id) {
            activeTab?.webView?.apply {
                onPause()
                visibility = View.GONE
            }
        }

        activeTab = target
        target.webView.apply {
            visibility = View.VISIBLE
            onResume()
        }

        onTabsChangedListener?.invoke()
        return target
    }

    fun deselectCurrentTab() {
        activeTab?.webView?.apply {
            onPause()
            visibility = View.GONE
        }
        activeTab = null
        onTabsChangedListener?.invoke()
    }

    fun closeTab(tabId: String): Tab? {
        val index = _tabs.indexOfFirst { it.id == tabId }
        if (index == -1) return null

        val removed = _tabs.removeAt(index)
        removed.webView.apply {
            stopLoading()
            visibility = View.GONE
            (parent as? android.view.ViewGroup)?.removeView(this)
            destroy()
        }

        if (activeTab?.id == tabId) {
            activeTab = if (_tabs.isNotEmpty()) {
                val nextIndex = (index - 1).coerceAtLeast(0)
                val nextTab = _tabs[nextIndex]
                nextTab.webView.apply {
                    visibility = View.VISIBLE
                    onResume()
                }
                nextTab
            } else {
                null
            }
        }

        onTabsChangedListener?.invoke()
        return removed
    }

    fun closeAllTabs() {
        for (tab in _tabs) {
            tab.webView.apply {
                stopLoading()
                visibility = View.GONE
                (parent as? android.view.ViewGroup)?.removeView(this)
                destroy()
            }
        }
        _tabs.clear()
        activeTab = null
        onTabsChangedListener?.invoke()
    }

    val count: Int get() = _tabs.size
}
