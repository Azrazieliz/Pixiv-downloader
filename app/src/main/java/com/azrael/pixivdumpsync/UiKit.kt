package com.azrael.pixivdumpsync

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.Button
import android.widget.TextView

object UiKit {
    val bg = Color.rgb(10, 8, 10)
    val surface = Color.rgb(24, 18, 22)
    val surfaceAlt = Color.rgb(34, 24, 30)
    val accent = Color.rgb(243, 190, 211)
    val accentPressed = Color.rgb(228, 154, 185)
    val text = Color.rgb(255, 247, 250)
    val muted = Color.rgb(188, 157, 170)
    val line = Color.rgb(61, 43, 52)
    val success = Color.rgb(196, 230, 201)
    val danger = Color.rgb(255, 126, 160)

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun rounded(
        context: Context,
        fill: Int,
        radius: Int = 18,
        stroke: Int? = null,
        strokeWidth: Int = 1
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(context, radius).toFloat()
        if (stroke != null) {
            setStroke(dp(context, strokeWidth), stroke)
        }
    }

    fun ripple(
        context: Context,
        fill: Int,
        rippleColor: Int,
        radius: Int = 14,
        stroke: Int? = null
    ): RippleDrawable = RippleDrawable(
        ColorStateList.valueOf(rippleColor),
        rounded(context, fill, radius, stroke),
        null
    )

    fun stylePrimaryButton(context: Context, button: Button) {
        button.isAllCaps = false
        button.textSize = 16f
        button.typeface = Typeface.DEFAULT_BOLD
        button.setTextColor(Color.rgb(28, 18, 23))
        button.minHeight = dp(context, 54)
        button.background = ripple(
            context,
            accent,
            accentPressed,
            radius = 16
        )
        button.stateListAnimator = null
    }

    fun styleSecondaryButton(context: Context, button: Button) {
        button.isAllCaps = false
        button.textSize = 15f
        button.typeface = Typeface.DEFAULT_BOLD
        button.setTextColor(text)
        button.minHeight = dp(context, 50)
        button.background = ripple(
            context,
            surfaceAlt,
            Color.rgb(55, 38, 47),
            radius = 14,
            stroke = line
        )
        button.stateListAnimator = null
    }

    fun styleDangerButton(context: Context, button: Button) {
        button.isAllCaps = false
        button.textSize = 14f
        button.typeface = Typeface.DEFAULT_BOLD
        button.setTextColor(danger)
        button.minHeight = dp(context, 44)
        button.background = ripple(
            context,
            surfaceAlt,
            Color.rgb(70, 43, 54),
            radius = 12,
            stroke = line
        )
        button.stateListAnimator = null
    }

    fun sectionLabel(view: TextView) {
        view.setTextColor(muted)
        view.textSize = 12f
        view.typeface = Typeface.DEFAULT_BOLD
        view.letterSpacing = 0.08f
    }

    fun title(view: TextView, size: Float = 24f) {
        view.setTextColor(text)
        view.textSize = size
        view.typeface = Typeface.DEFAULT_BOLD
    }

    fun body(view: TextView, size: Float = 14f) {
        view.setTextColor(muted)
        view.textSize = size
    }

    fun setMargins(
        view: View,
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0
    ) {
        val lp = view.layoutParams
        if (lp is android.view.ViewGroup.MarginLayoutParams) {
            lp.setMargins(left, top, right, bottom)
            view.layoutParams = lp
        }
    }
}
