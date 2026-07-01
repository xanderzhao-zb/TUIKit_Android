package io.trtc.tuikit.chat.uikit.components.messageinput.keyboard
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager

internal class WindowSoftInputModeGuard {

    private var guardedActivity: Activity? = null
    private var previousSoftInputMode: Int = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED

    fun apply(context: Context) {
        if (guardedActivity != null) return
        val activity = context.findHostActivity() ?: return
        val window = activity.window ?: return
        val current = window.attributes.softInputMode
        if (current.adjustMode == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING) {
            return
        }
        previousSoftInputMode = current
        guardedActivity = activity
        val adjusted = (current and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        window.setSoftInputMode(adjusted)
    }

    fun restore() {
        val activity = guardedActivity ?: return
        guardedActivity = null
        activity.window?.setSoftInputMode(previousSoftInputMode)
    }

    private val Int.adjustMode: Int
        get() = this and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
}

private tailrec fun Context.findHostActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findHostActivity()
        else -> null
    }
}
