package com.example.floodguard

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Custom ScrollView that handles window insets for the on-screen software keyboard (IME).
 * Automatically scrolls to ensure the active input field remains visible when typing.
 */
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
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bottomInset = if (imeBottom > 0) imeBottom else systemBars.bottom

            view.setPadding(
                initialPaddingLeft,
                initialPaddingTop + systemBars.top,
                initialPaddingRight,
                initialPaddingBottom + bottomInset,
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
