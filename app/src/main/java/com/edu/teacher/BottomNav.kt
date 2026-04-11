package com.edu.teacher

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class BottomNav @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var onItemSelectedListener: ((Int) -> Unit)? = null
    private var currentPosition = 0

    data class TabItem(val path: String, val icon: String, val label: String)

    private val tabs = listOf(
        TabItem("/", "🏠", "الرئيسية"),
        TabItem("/stats", "📊", "الإحصائيات"),
        TabItem("/sync", "👥", "طلابي"),
        TabItem("/settings", "⚙️", "الإعدادات")
    )

    init {
        LayoutInflater.from(context).inflate(R.layout.bottom_nav, this, true)
        // استخدام post لضمان أن النظام انتهى من رسم الواجهة قبل تشغيل setupTabs
        post {
            setupTabs()
        }
    }

    private fun setupTabs() {
        val container = findViewById<LinearLayout>(R.id.tabsContainer)
        val addButton = findViewById<MaterialButton>(R.id.addButton)

        // إضافة ? لمنع الانهيار إذا لم يجد الزر
        addButton?.setOnClickListener {
            onItemSelectedListener?.invoke(-1)
        }

        // تنظيف الحاوية لمنع تكرار العناصر عند إعادة التشغيل
        container?.removeAllViews()

        tabs.forEachIndexed { index, tab ->
            val tabView = LayoutInflater.from(context).inflate(R.layout.bottom_nav_item, container, false)
            val iconView = tabView.findViewById<TextView>(R.id.iconView)
            val labelView = tabView.findViewById<TextView>(R.id.labelView)

            // تعبئة البيانات مع حماية ضد الـ Null
            iconView?.text = tab.icon
            labelView?.text = tab.label

            tabView.setOnClickListener {
                setActiveTab(index)
                onItemSelectedListener?.invoke(index)
            }

            container?.addView(tabView)
        }
        
        setActiveTab(0)
    }

    fun setActiveTab(position: Int) {
        currentPosition = position
        val container = findViewById<LinearLayout>(R.id.tabsContainer) ?: return
        
        for (i in 0 until container.childCount) {
            val tabView = container.getChildAt(i)
            // استخدام ? بعد اسم المتغير هو أهم تعديل لمنع الانهيار (Crash Safe)
            val iconView = tabView?.findViewById<TextView>(R.id.iconView)
            val labelView = tabView?.findViewById<TextView>(R.id.labelView)
            
            if (i == position) {
                val activeColor = ContextCompat.getColor(context, R.color.indigo_600)
                iconView?.setTextColor(activeColor)
                labelView?.setTextColor(activeColor)
                labelView?.alpha = 1f
            } else {
                val inactiveColor = ContextCompat.getColor(context, R.color.slate_400)
                iconView?.setTextColor(inactiveColor)
                labelView?.setTextColor(inactiveColor)
                labelView?.alpha = 0.6f
            }
        }
    }

    fun setOnItemSelectedListener(listener: (Int) -> Unit) {
        onItemSelectedListener = listener
    }
}
