package com.example.floodguard

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class InputScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.scrollViewStyle,
) : ScrollView(context, attrs, defStyleAttr) {

    private val initialPaddingLeft = paddingLeft
    private val initialPaddingTop = paddingTop
    private val initialPaddingRight = paddingRight
    private val initialPaddingBottom = paddingBottom

    init {
        isFillViewport = true
        clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.setPadding(
                initialPaddingLeft,
                initialPaddingTop,
                initialPaddingRight,
                initialPaddingBottom + imeBottom,
            )

            if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
                post {
                    val focusedView = findFocus()
                    if (focusedView != null && focusedView !== this) {
                        focusedView.requestRectangleOnScreen(
                            Rect(0, 0, focusedView.width, focusedView.height),
                            false,
                        )
                    }
                }
            }
            insets
        }
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        val keyboardVisible = ViewCompat.getRootWindowInsets(this)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        super.requestDisallowInterceptTouchEvent(disallowIntercept && !keyboardVisible)
    }
}
