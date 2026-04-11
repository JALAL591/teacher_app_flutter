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

    // تم استخدام String للأيقونات (Emojis) بدلاً من Drawable لتجنب خطأ "Resource Not Found"
    data class TabItem(val path: String, val icon: String, val label: String)

    private val tabs = listOf(
        TabItem("/", "🏠", "الرئيسية"),
        TabItem("/stats", "📊", "الإحصائيات"),
        TabItem("/sync", "👥", "طلابي"),
        TabItem("/settings", "⚙️", "الإعدادات")
    )

    init {
        // التأكد من أن ملف bottom_nav.xml موجود في مجلد res/layout
        LayoutInflater.from(context).inflate(R.layout.bottom_nav, this, true)
        setupTabs()
    }

    private fun setupTabs() {
        val container = findViewById<LinearLayout>(R.id.tabsContainer)
        val addButton = findViewById<MaterialButton>(R.id.addButton)

        addButton.setOnClickListener {
            onItemSelectedListener?.invoke(-1) // رقم -1 يشير إلى زر الإضافة (+)
        }

        tabs.forEachIndexed { index, tab ->
            // نفخ (Inflate) عنصر القائمة الفردي
            val tabView = LayoutInflater.from(context).inflate(R.layout.bottom_nav_item, container, false)
            val iconView = tabView.findViewById<TextView>(R.id.iconView)
            val labelView = tabView.findViewById<TextView>(R.id.labelView)

            iconView.text = tab.icon
            labelView.text = tab.label

            tabView.setOnClickListener {
                setActiveTab(index)
                onItemSelectedListener?.invoke(index)
            }

            container.addView(tabView)
        }
        
        // تعيين التبويب الأول كنشط افتراضياً
        setActiveTab(0)
    }

    fun setActiveTab(position: Int) {
        currentPosition = position
        val container = findViewById<LinearLayout>(R.id.tabsContainer)
        
        for (i in 0 until container.childCount) {
            val tabView = container.getChildAt(i)
            val iconView = tabView.findViewById<TextView>(R.id.iconView)
            val labelView = tabView.findViewById<TextView>(R.id.labelView)
            
            if (i == position) {
                // استخدام ContextCompat لجلب الألوان بشكل آمن وتجنب مشاكل النسخ القديمة
                val activeColor = ContextCompat.getColor(context, R.color.indigo_600)
                iconView.setTextColor(activeColor)
                labelView.setTextColor(activeColor)
                labelView.alpha = 1f
            } else {
                val inactiveColor = ContextCompat.getColor(context, R.color.slate_400)
                iconView.setTextColor(inactiveColor)
                labelView.setTextColor(inactiveColor)
                labelView.alpha = 0.6f
            }
        }
    }

    fun setOnItemSelectedListener(listener: (Int) -> Unit) {
        onItemSelectedListener = listener
    }
}
