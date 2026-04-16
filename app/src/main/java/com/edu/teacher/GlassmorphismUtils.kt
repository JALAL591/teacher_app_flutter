package com.edu.teacher

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * Utility class to apply Glassmorphism effects to views
 * Supports Android 12+ (API 31+) with RenderEffect
 */
object GlassmorphismUtils {

    /**
     * Apply background blur effect to a view (Android 12+)
     * @param view The view to apply blur effect to
     * @param radius Blur radius (recommended: 10f-25f)
     */
    fun applyBlurEffect(view: View, radius: Float = 15f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            )
        }
    }

    /**
     * Apply blur effect with different horizontal and vertical radius
     * @param view The view to apply blur effect to
     * @param radiusX Horizontal blur radius
     * @param radiusY Vertical blur radius
     */
    fun applyBlurEffect(view: View, radiusX: Float, radiusY: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(
                RenderEffect.createBlurEffect(radiusX, radiusY, Shader.TileMode.CLAMP)
            )
        }
    }

    /**
     * Remove blur effect from a view
     */
    fun removeBlurEffect(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(null)
        }
    }

    /**
     * Check if device supports RenderEffect blur
     */
    fun isBlurSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}
