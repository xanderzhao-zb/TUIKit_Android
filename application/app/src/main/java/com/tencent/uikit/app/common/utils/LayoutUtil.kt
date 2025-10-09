package com.tencent.uikit.app.common.utils

import android.content.Context
import android.content.res.Configuration
import android.view.View
import com.tencent.qcloud.tuicore.ServiceInitializer

object LayoutUtil {
    fun isRTL(): Boolean {
        val context: Context = ServiceInitializer.getAppContext()
        val configuration: Configuration = context.resources.configuration
        val layoutDirection = configuration.layoutDirection
        return layoutDirection == View.LAYOUT_DIRECTION_RTL
    }
}