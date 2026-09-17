package com.heronikostudios.socialvault

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.heronikostudios.socialvault.databinding.ItemPlatformGridBinding
import java.net.URI
import java.util.Collections

class PlatformAdapter(
    private var platforms: MutableList<Platform>,
    private val onPlatformClick: (Platform) -> Unit,
    private val onPlatformLongClick: (Platform) -> Unit
) : RecyclerView.Adapter<PlatformAdapter.PlatformViewHolder>() {

    fun updatePlatforms(newPlatforms: List<Platform>) {
        platforms = newPlatforms.toMutableList()
        notifyDataSetChanged()
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(platforms, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(platforms, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlatformViewHolder {
        val binding = ItemPlatformGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlatformViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlatformViewHolder, position: Int) {
        holder.bind(platforms[position])
    }

    override fun getItemCount(): Int = platforms.size

    inner class PlatformViewHolder(
        private val binding: ItemPlatformGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(platform: Platform) {
            binding.tvPlatformName.text = platform.name

            val domain = try {
                URI(platform.url).host ?: platform.url
            } catch (_: Exception) {
                platform.url
            }
            binding.tvPlatformDomain.text = domain
            binding.ivPlatformIcon.setImageResource(platform.iconResId)

            binding.cardPlatform.setOnClickListener {
                onPlatformClick(platform)
            }

            binding.cardPlatform.setOnLongClickListener {
                onPlatformLongClick(platform)
                true
            }
        }
    }
}
