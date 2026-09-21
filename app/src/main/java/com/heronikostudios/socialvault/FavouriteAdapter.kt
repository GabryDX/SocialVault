package com.heronikostudios.socialvault

import android.annotation.SuppressLint
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.heronikostudios.socialvault.databinding.ItemFavouriteBinding

class FavouriteAdapter(
    private var favourites: List<Favourite>,
    private val platformManager: PlatformManager,
    private val onFavouriteClick: (Favourite) -> Unit,
    private val onFavouriteOptionsClick: (Favourite, View) -> Unit
) : RecyclerView.Adapter<FavouriteAdapter.FavouriteViewHolder>() {

    @SuppressLint("NotifyDataSetChanged")
    fun updateFavourites(newFavourites: List<Favourite>) {
        favourites = newFavourites
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavouriteViewHolder {
        val binding = ItemFavouriteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FavouriteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavouriteViewHolder, position: Int) {
        holder.bind(favourites[position])
    }

    override fun getItemCount(): Int = favourites.size

    inner class FavouriteViewHolder(
        private val binding: ItemFavouriteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(favourite: Favourite) {
            binding.tvFavTitle.text = favourite.title
            binding.tvFavUrl.text = favourite.url

            val platform = platformManager.getPlatformById(favourite.platformId)
                ?: platformManager.findMatchingPlatform(favourite.url)
            val iconRes = platform?.iconResId ?: R.drawable.ic_globe
            binding.ivPlatformIcon.setImageResource(iconRes)

            val timeAgo = DateUtils.getRelativeTimeSpanString(
                favourite.createdAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            val platformLabel = platform?.name ?: favourite.platformName.ifBlank { "Social" }
            binding.tvFavMeta.text = "$platformLabel • $timeAgo"

            binding.cardFavourite.setOnClickListener {
                onFavouriteClick(favourite)
            }

            binding.btnFavOptions.setOnClickListener { view ->
                onFavouriteOptionsClick(favourite, view)
            }
        }
    }
}
