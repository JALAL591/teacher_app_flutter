package com.edu.teacher

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

class BottomNav @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var onItemSelectedListener: ((Int) -> Unit)? = null
    private var currentPosition = 0

    private val tabIcons = listOf(
        R.id.tabIcon1, R.id.tabIcon2, R.id.tabIcon3, R.id.tabIcon4, R.id.tabIcon5
    )

    private val tabLabels = listOf(
        R.id.tabLabel1, R.id.tabLabel2, R.id.tabLabel3, R.id.tabLabel4, R.id.tabLabel5
    )

    private val tabClickIds = listOf(R.id.tab1, R.id.tab2, R.id.tab3, R.id.tab4, R.id.tab5)

    private val tabData = listOf(
        Triple(R.drawable.ic_nav_home, R.string.nav_home, 0),
        Triple(R.drawable.ic_nav_stats, R.string.nav_stats, 1),
        Triple(R.drawable.ic_nav_add, R.string.nav_add_lesson, 2),
        Triple(R.drawable.ic_nav_attendance, R.string.nav_attendance, 3),
        Triple(R.drawable.ic_nav_settings, R.string.nav_settings, 4)
    )

    init {
        LayoutInflater.from(context).inflate(R.layout.bottom_nav_modern_2026, this, true)
        setupTabs()
    }

    private fun setupTabs() {
        // Use theme-aware colors
        val inactiveColor = ContextCompat.getColor(context, R.color.nav_inactive)

        tabData.forEachIndexed { index, (iconRes, labelRes, position) ->
            val iconView = findViewById<ImageView>(tabIcons[index])
            val labelView = findViewById<TextView>(tabLabels[index])
            val clickTarget = findViewById<View>(tabClickIds[index])

            iconView?.setImageResource(iconRes)
            // Apply tint to icons for theme consistency
            iconView?.setColorFilter(inactiveColor)
            labelView?.text = context.getString(labelRes)
            labelView?.setTextColor(inactiveColor)

            clickTarget?.isClickable = true
            clickTarget?.setOnClickListener {
                setActiveTab(position)
                onItemSelectedListener?.invoke(position)
            }
        }

        setActiveTab(0)
    }

    fun setActiveTab(position: Int) {
        currentPosition = position

        val activeColor = ContextCompat.getColor(context, R.color.nav_active)
        val inactiveColor = ContextCompat.getColor(context, R.color.nav_inactive)

        tabClickIds.forEachIndexed { index, _ ->
            val iconView = findViewById<ImageView>(tabIcons[index])
            val labelView = findViewById<TextView>(tabLabels[index])

            if (index == position) {
                iconView?.setColorFilter(activeColor)
                labelView?.setTextColor(activeColor)
                labelView?.alpha = 1f
            } else {
                iconView?.setColorFilter(inactiveColor)
                labelView?.setTextColor(inactiveColor)
                labelView?.alpha = 0.7f
            }
        }

        val centerBg = findViewById<View>(R.id.tab3Bg)
        if (position == 2) {
            centerBg?.scaleX = 1.15f
            centerBg?.scaleY = 1.15f
        } else {
            centerBg?.scaleX = 1f
            centerBg?.scaleY = 1f
        }
    }

    fun setOnItemSelectedListener(listener: (Int) -> Unit) {
        onItemSelectedListener = listener
    }

    fun getCurrentPosition(): Int = currentPosition
}