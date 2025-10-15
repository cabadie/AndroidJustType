package com.justtype.nativeapp.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatButton

class SquareButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.buttonStyle
) : AppCompatButton(context, attrs, defStyleAttr) {
    init {
        // Improve vertical centering for multi-line text grids
        includeFontPadding = false
        gravity = Gravity.CENTER
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Measure using width for both dimensions to enforce a square
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
        val size = measuredWidth
        setMeasuredDimension(size, size)
    }
}


