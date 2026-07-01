package io.trtc.tuikit.chat.uikit.components.contactlist.ui
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
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.theme.ThemeStore

internal class ContactListSearchBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    val editText: EditText

    private val inputContainer: FrameLayout
    private val searchIcon: ImageView
    private val clearButton: ImageView
    private val handler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    private val debounceDelay = 300L

    var onQueryChange: ((String) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE

        val dp16 = dpToPx(16)
        setPadding(dp16, dp16, dp16, dp16)

        inputContainer = FrameLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            val inputHeight = dpToPx(36)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, inputHeight)
        }
        addView(inputContainer)

        editText = EditText(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            textDirection = View.TEXT_DIRECTION_LOCALE
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

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) = Unit

            override fun afterTextChanged(s: Editable?) {
                updateClearButtonVisibility(s)
                debounceRunnable?.let { handler.removeCallbacks(it) }
                debounceRunnable = Runnable {
                    onQueryChange?.invoke(s?.toString() ?: "")
                }
                handler.postDelayed(debounceRunnable!!, debounceDelay)
            }
        })

        applyTheme()
    }

    fun setQuery(query: String) {
        if (editText.text.toString() != query) {
            editText.setText(query)
            editText.setSelection(query.length)
        }
        updateClearButtonVisibility(editText.text)
    }

    fun setHint(hintText: CharSequence) {
        editText.hint = hintText
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

        val backgroundDrawable = GradientDrawable().apply {
            setColor(colors.bgColorInput)
            cornerRadius = dpToPx(10).toFloat()
        }
        inputContainer.background = backgroundDrawable

        searchIcon.setImageResource(R.drawable.contact_list_ic_search)
        searchIcon.setColorFilter(colors.textColorTertiary)

        clearButton.setImageResource(R.drawable.contact_list_ic_search_clear)
        clearButton.setColorFilter(colors.textColorPrimary)

        editText.setTextColor(colors.textColorPrimary)
        editText.setHintTextColor(colors.textColorTertiary)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        debounceRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun updateClearButtonVisibility(text: CharSequence?) {
        clearButton.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun clearQuery() {
        setQuery("")
        debounceRunnable?.let { handler.removeCallbacks(it) }
        debounceRunnable = null
        onQueryChange?.invoke("")
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}