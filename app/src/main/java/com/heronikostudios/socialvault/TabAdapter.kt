package com.heronikostudios.socialvault

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.heronikostudios.socialvault.databinding.ItemTabListBinding

class TabAdapter(
    private var tabs: List<Tab>,
    private var activeTabId: String?,
    private val onTabClick: (Tab) -> Unit,
    private val onTabClose: (Tab) -> Unit
) : RecyclerView.Adapter<TabAdapter.TabViewHolder>() {

    fun updateTabs(newTabs: List<Tab>, newActiveTabId: String?) {
        tabs = newTabs
        activeTabId = newActiveTabId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding = ItemTabListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.bind(tabs[position])
    }

    override fun getItemCount(): Int = tabs.size

    inner class TabViewHolder(
        private val binding: ItemTabListBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tab: Tab) {
            binding.tvTabTitle.text = tab.title.ifEmpty { tab.platform.name }
            binding.tvTabUrl.text = tab.currentUrl
            binding.ivTabIcon.setImageResource(tab.platform.iconResId)

            val isActive = tab.id == activeTabId
            if (isActive) {
                binding.cardTab.strokeColor = Color.parseColor("#38BDF8")
                binding.cardTab.strokeWidth = 3
            } else {
                binding.cardTab.strokeColor = Color.parseColor("#334155")
                binding.cardTab.strokeWidth = 1
            }

            binding.cardTab.setOnClickListener {
                onTabClick(tab)
            }

            binding.btnCloseTab.setOnClickListener {
                onTabClose(tab)
            }
        }
    }
}
