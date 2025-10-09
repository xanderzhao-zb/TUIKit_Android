package com.tencent.uikit.app.common.utils

import android.content.Context
import android.graphics.Rect
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import com.tencent.qcloud.tuicore.TUIConfig

object SoftKeyBoardUtil {
    fun hideKeyBoard(token: IBinder) {
        val imm = TUIConfig.getAppContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.hideSoftInputFromWindow(token, 0)
    }

    fun hideKeyBoard(window: Window) {
        val imm = TUIConfig.getAppContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        if (imm != null) {
            if (isSoftInputShown(window)) {
                imm.toggleSoftInput(0, 0)
            }
        }
    }

    fun showKeyBoard(window: Window) {
        val imm = TUIConfig.getAppContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        if (imm != null) {
            if (!isSoftInputShown(window)) {
                imm.toggleSoftInput(0, 0)
            }
        }
    }

    private fun isSoftInputShown(window: Window): Boolean {
        val decorView = window.decorView
        val screenHeight = decorView.height
        val rect = Rect()
        decorView.getWindowVisibleDisplayFrame(rect)
        return screenHeight - rect.bottom - getNavigateBarHeight(window.windowManager) >= 0
    }

    private fun getNavigateBarHeight(windowManager: WindowManager): Int {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val usableHeight = metrics.heightPixels
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val realHeight = metrics.heightPixels
        return if (realHeight > usableHeight) {
            realHeight - usableHeight
        } else {
            0
        }
    }
}