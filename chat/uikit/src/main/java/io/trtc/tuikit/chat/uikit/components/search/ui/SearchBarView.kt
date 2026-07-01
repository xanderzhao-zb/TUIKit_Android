package io.trtc.tuikit.chat.uikit.components.search.ui
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.expandTouchTarget
import io.trtc.tuikit.atomicx.theme.ThemeStore

class SearchBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    val editText: EditText
    private val cancelButton: TextView
    private val searchIcon: ImageView
    private val inputContainer: FrameLayout
    private val clearButton: ImageView
    private val handler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    private val debounceDelay = 300L
    private var suppressTextCallback = false

    var onQueryChange: ((String) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val dp16 = dpToPx(16)
        setPadding(dp16, dpToPx(10), dp16, dpToPx(10))

        inputContainer = FrameLayout(context).apply {
            val inputHeight = dpToPx(40)
            layoutParams = LayoutParams(0, inputHeight, 1f)
        }
        addView(inputContainer)

        editText = EditText(context).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = null
            isSingleLine = true
            val horizontalPadding = dpToPx(36)
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            hint = context.getString(R.string.search_search_text)
        }
        inputContainer.addView(editText)

        searchIcon = ImageView(context).apply {
            val iconSize = dpToPx(15)
            layoutParams = FrameLayout.LayoutParams(
                iconSize,
                iconSize,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply {
                marginStart = dpToPx(8)
            }
        }
        inputContainer.addView(searchIcon)

        clearButton = ImageView(context).apply {
            val iconSize = dpToPx(16)
            layoutParams = FrameLayout.LayoutParams(
                iconSize,
                iconSize,
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply {
                marginEnd = dpToPx(10)
            }
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            setOnClickListener {
                clearQuery()
            }
        }
        inputContainer.addView(clearButton)
        clearButton.expandTouchTarget()

        cancelButton = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            text = context.getString(R.string.uikit_cancel)
            val dp12 = dpToPx(12)
            setPaddingRelative(dp12, 0, 0, 0)
            setOnClickListener {
                clearQuery()
                onCancel?.invoke()
            }
        }
        addView(cancelButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        cancelButton.expandTouchTarget()

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (suppressTextCallback) {
                    updateClearButtonVisibility(s)
                    return
                }
                debounceRunnable?.let { handler.removeCallbacks(it) }
                updateClearButtonVisibility(s)
                debounceRunnable = Runnable {
                    onQueryChange?.invoke(s?.toString() ?: "")
                }
                handler.postDelayed(debounceRunnable!!, debounceDelay)
            }
        })

        applyTheme()
    }

    fun setQuery(query: String, notify: Boolean = false) {
        if (editText.text.toString() != query) {
            suppressTextCallback = !notify
            editText.setText(query)
            suppressTextCallback = false
            editText.setSelection(query.length)
        }
        updateClearButtonVisibility(editText.text)
    }

    fun requestFocusAndShowKeyboard() {
        editText.post {
            editText.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText.windowToken, 0)
        editText.clearFocus()
    }

    fun applyTheme() {
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        setBackgroundColor(colors.bgColorOperate)
        searchIcon.setImageResource(R.drawable.search_ic_search)
        searchIcon.setColorFilter(colors.textColorTertiary)
        editText.setTextColor(colors.textColorPrimary)
        editText.setHintTextColor(colors.textColorTertiary)
        clearButton.setImageResource(R.drawable.search_ic_search_clear)
        clearButton.setColorFilter(colors.textColorPrimary)
        cancelButton.setTextColor(colors.textColorLink)

        val bg = GradientDrawable().apply {
            setColor(colors.bgColorInput)
            cornerRadius = dpToPx(10).toFloat()
        }
        inputContainer.background = bg
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    private fun updateClearButtonVisibility(text: CharSequence?) {
        val visible = !text.isNullOrEmpty()
        clearButton.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            clearButton.expandTouchTarget()
        }
    }

    private fun clearQuery() {
        setQuery("")
        debounceRunnable?.let { handler.removeCallbacks(it) }
        debounceRunnable = null
        onQueryChange?.invoke("")
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        debounceRunnable?.let { handler.removeCallbacks(it) }
    }
}
