package com.chaomixian.vflow.ui.shortcut

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.card.MaterialCardView

/**
 * 图标选择器适配器
 */
class IconSelectorAdapter(
    private val onIconSelected: (String?) -> Unit,
    private val onCustomImageSelected: () -> Unit
) : RecyclerView.Adapter<IconSelectorAdapter.IconViewHolder>() {

    companion object {
        private const val TYPE_ICON = 0
        private const val TYPE_CUSTOM_IMAGE = 1
    }

    // 可用的图标资源列表（资源名称）
    private val availableIcons = listOf(
        "ic_shortcut_play",
        "rounded_play_arrow_24",
        "rounded_activity_zone_24",
        "rounded_ads_click_24",
        "rounded_battery_android_frame_full_24",
        "rounded_bluetooth_24",
        "rounded_brightness_5_24",
        "rounded_calculate_24",
        "rounded_call_to_action_24",
        "rounded_check_circle_24",
        "rounded_content_copy_24",
        "rounded_content_paste_24",
        "rounded_convert_to_text_24",
        "rounded_dashboard_2_edit_24",
        "rounded_dataset_24",
        "rounded_download_24",
        "rounded_earbuds_24",
        "rounded_fullscreen_portrait_24",
        "rounded_hexagon_nodes_24",
        "rounded_image_search_24",
        "rounded_keyboard_24",
        "rounded_logout_24",
        "rounded_output_24",
        "rounded_pause_24",
        "rounded_photo_24",
        "rounded_preview_24",
        "rounded_public_24",
        "rounded_save_24",
        "rounded_search_24",
        "rounded_settings_24",
        "rounded_skip_next_24",
        "rounded_sms_24",
        "rounded_stop_circle_24",
        "rounded_swap_calls_24",
        "rounded_terminal_24",
        "rounded_turn_slight_right_24",
        "rounded_wifi_tethering_24",
    )

    private var selectedIconRes: String? = null
    private var isCustomImage = false
    private var customImageBitmap: android.graphics.Bitmap? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_icon_selector, parent, false)
        return IconViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
        if (position == availableIcons.size) {
            // 自定义图片选项
            holder.bindCustomImage(isCustomImage, customImageBitmap)
        } else {
            // 普通图标选项
            val iconRes = availableIcons[position]
            holder.bind(iconRes, iconRes == selectedIconRes && !isCustomImage)
        }
    }

    override fun getItemCount(): Int = availableIcons.size + 1 // +1 for custom image option

    override fun getItemViewType(position: Int): Int {
        return if (position == availableIcons.size) TYPE_CUSTOM_IMAGE else TYPE_ICON
    }

    fun setSelectedIcon(iconRes: String?) {
        val previousSelected = selectedIconRes
        selectedIconRes = iconRes
        isCustomImage = false
        customImageBitmap = null

        // 更新之前选中项的状态
        previousSelected?.let {
            val previousPosition = availableIcons.indexOf(it)
            if (previousPosition >= 0) {
                notifyItemChanged(previousPosition)
            }
        }

        // 更新当前选中项的状态
        selectedIconRes?.let {
            val currentPosition = availableIcons.indexOf(it)
            if (currentPosition >= 0) {
                notifyItemChanged(currentPosition)
            }
        }

        // 更新自定义图片选项
        notifyItemChanged(availableIcons.size)

        onIconSelected(iconRes)
    }

    fun setCustomImage(imagePath: String) {
        selectedIconRes = imagePath
        isCustomImage = true

        // 加载自定义图片的缩略图用于显示
        try {
            val path = if (imagePath.startsWith("file://")) {
                imagePath.substring(7)
            } else {
                imagePath
            }
            val file = java.io.File(path)
            if (file.exists()) {
                // 加载缩小的图片作为预览
                val options = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = 4 // 缩小图片以减少内存使用
                }
                customImageBitmap = android.graphics.BitmapFactory.decodeFile(file.path, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            customImageBitmap = null
        }

        // 清除之前选中的图标
        val previousPosition = availableIcons.indexOf(selectedIconRes?.takeIf { !isCustomImage })
        if (previousPosition >= 0) {
            notifyItemChanged(previousPosition)
        }

        // 更新自定义图片选项
        notifyItemChanged(availableIcons.size)

        onIconSelected(imagePath)
    }

    inner class IconViewHolder(itemView: View, private val viewType: Int) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val iconContainer: View = itemView.findViewById(R.id.icon_container)
        private val imageView: ImageView = itemView.findViewById(R.id.image_view_icon)

        fun bind(iconRes: String, isSelected: Boolean) {
            // 获取资源 ID
            val resId = itemView.context.resources.getIdentifier(
                iconRes,
                "drawable",
                itemView.context.packageName
            )

            if (resId != 0) {
                imageView.setImageResource(resId)
            }

            // 更新选中状态
            updateSelectionStyle(isSelected)

            cardView.setOnClickListener {
                setSelectedIcon(iconRes)
            }
        }

        fun bindCustomImage(isSelected: Boolean, customBitmap: android.graphics.Bitmap? = null) {
            // 如果有自定义图片且被选中，显示自定义图片；否则显示相册图标
            if (isSelected && customBitmap != null) {
                imageView.setImageBitmap(customBitmap)
                // 清除 tint 以显示原始图片
                imageView.imageTintList = null
            } else {
                imageView.setImageResource(R.drawable.rounded_add_photo_alternate_24)
                // 恢复 tint
                imageView.imageTintList = android.content.res.ColorStateList.valueOf(
                    MaterialColors.getColor(
                        itemView.context,
                        com.google.android.material.R.attr.colorOnSurface,
                        0
                    )
                )
            }

            // 更新选中状态
            updateSelectionStyle(isSelected)

            // 为相册图标使用不同的背景色，使其与普通图标区分
            if (!isSelected) {
                // 使用 SecondaryContainer 作为特殊背景色
                val specialBgColor = MaterialColors.getColor(
                    itemView.context,
                    com.google.android.material.R.attr.colorSecondaryContainer,
                    0
                )
                iconContainer.setBackgroundColor(specialBgColor)

                // 同时设置图标的颜色为 OnSecondaryContainer
                val iconColor = MaterialColors.getColor(
                    itemView.context,
                    com.google.android.material.R.attr.colorOnSecondaryContainer,
                    0
                )
                imageView.imageTintList = android.content.res.ColorStateList.valueOf(iconColor)
            }

            cardView.setOnClickListener {
                onCustomImageSelected()
            }
        }

        private fun updateSelectionStyle(isSelected: Boolean) {
            if (isSelected) {
                val accentColor = MaterialColors.getColor(
                    itemView.context,
                    com.google.android.material.R.attr.colorPrimary,
                    0
                )
                cardView.strokeColor = accentColor
                iconContainer.setBackgroundColor(ColorUtils.setAlphaComponent(accentColor, 25))
            } else {
                val surfaceColor = MaterialColors.getColor(
                    itemView.context,
                    com.google.android.material.R.attr.colorSurfaceContainerHighest,
                    0
                )
                cardView.strokeColor = surfaceColor
                iconContainer.setBackgroundColor(surfaceColor)
            }
        }
    }
}
