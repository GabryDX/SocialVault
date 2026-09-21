package com.heronikostudios.socialvault

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.webkit.CookieManager
import androidx.recyclerview.widget.RecyclerView
import com.heronikostudios.socialvault.databinding.ItemExtractedImageBinding

class ExtractedImageAdapter(
    val images: List<ExtractedImage>,
    private val cookieManager: CookieManager?,
    private val userAgent: String?,
    private val onSelectionChanged: () -> Unit,
    private val onSingleDownloadClick: (ExtractedImage, Int) -> Unit
) : RecyclerView.Adapter<ExtractedImageAdapter.ViewHolder>() {

    private val mainHandler = Handler(Looper.getMainLooper())

    inner class ViewHolder(val binding: ItemExtractedImageBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExtractedImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = images.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = images[position]
        val total = images.size

        with(holder.binding) {
            tvImageTitle.text = if (total > 1) {
                "Image ${position + 1} of $total"
            } else {
                "Image ${position + 1}"
            }

            val formatExt = DownloadHelper.inferMediaFormat(item.url, null, true)?.first?.uppercase() ?: "JPG"
            tvImageDetails.text = "${item.displayResolution} • $formatExt"

            // Prevent unwanted triggers while recycling
            cbSelectImage.setOnCheckedChangeListener(null)
            cbSelectImage.isChecked = item.isSelected

            cbSelectImage.setOnCheckedChangeListener { _, isChecked ->
                item.isSelected = isChecked
                onSelectionChanged()
            }

            cardImageItem.setOnClickListener {
                cbSelectImage.isChecked = !cbSelectImage.isChecked
            }

            btnDownloadSingle.setOnClickListener {
                onSingleDownloadClick(item, position)
            }

            // Reset thumbnail to placeholder while loading
            ivThumbnail.setImageResource(R.drawable.ic_image)
            ivThumbnail.imageTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#64748B")
            )

            // Async load scaled thumbnail
            val currentUrl = item.url
            ivThumbnail.tag = currentUrl

            ImageExtractorHelper.loadThumbnail(
                url = currentUrl,
                cookieManager = cookieManager,
                userAgent = userAgent
            ) { bitmap ->
                mainHandler.post {
                    if (ivThumbnail.tag == currentUrl && bitmap != null) {
                        ivThumbnail.imageTintList = null
                        ivThumbnail.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    fun selectAll(select: Boolean) {
        images.forEach { it.isSelected = select }
        notifyDataSetChanged()
        onSelectionChanged()
    }

    val selectedImages: List<ExtractedImage>
        get() = images.filter { it.isSelected }
}
