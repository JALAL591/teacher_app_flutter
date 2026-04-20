package com.edu.student.ui

import com.edu.teacher.R
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
        R.id.tabIcon1, R.id.tabIcon2, R.id.tabIcon3
    )

    private val tabLabels = listOf(
        R.id.tabLabel1, R.id.tabLabel2, R.id.tabLabel3
    )

    private val tabClickIds = listOf(R.id.tab1, R.id.tab2, R.id.tab3)

    private val tabData = listOf(
        Triple(R.drawable.ic_home_student, R.string.nav_home, 0),
        Triple(R.drawable.ic_stats_student, R.string.nav_stats, 1),
        Triple(R.drawable.ic_settings_student, R.string.nav_settings, 2)
    )

    init {
        LayoutInflater.from(context).inflate(R.layout.bottom_nav_student_modern, this, true)
        setupTabs()
    }

    private fun setupTabs() {
        val inactiveColor = ContextCompat.getColor(context, R.color.nav_inactive)

        tabData.forEachIndexed { index, (iconRes, labelRes, position) ->
            val iconView = findViewById<ImageView>(tabIcons[index])
            val labelView = findViewById<TextView>(tabLabels[index])
            val clickTarget = findViewById<View>(tabClickIds[index])

            iconView?.setImageResource(iconRes)
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
    }

    fun setOnItemSelectedListener(listener: (Int) -> Unit) {
        onItemSelectedListener = listener
    }

    fun getCurrentPosition(): Int = currentPosition
}