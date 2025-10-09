package com.tencent.uikit.app.common.utils

import android.graphics.Bitmap
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.tencent.qcloud.tuicore.TUILogin
import com.tencent.qcloud.tuicore.TUIThemeManager
import com.tencent.uikit.app.R
import java.util.concurrent.ExecutionException

object GlideEngine {

    fun clear(imageView: ImageView) {
        Glide.with(TUILogin.getAppContext()).clear(imageView)
    }

    fun loadUserIcon(imageView: ImageView, uri: Any?) {
        loadUserIcon(imageView, uri, 0)
    }

    fun loadUserIcon(imageView: ImageView, uri: Any?, radius: Int) {
        Glide.with(TUILogin.getAppContext())
            .load(uri)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(TUIThemeManager.getAttrResId(TUILogin.getAppContext(), R.drawable.app_default_user_icon_light))
            .apply(RequestOptions().centerCrop().error(TUIThemeManager.getAttrResId(TUILogin.getAppContext(), R.drawable.app_default_user_icon_light)))
            .into(imageView)
    }

    @Throws(InterruptedException::class, ExecutionException::class)
    fun loadBitmap(imageUrl: Any?, targetImageSize: Int): Bitmap {
        if (imageUrl == null) {
            throw IllegalArgumentException("Image URL cannot be null")
        }
        return Glide.with(TUILogin.getAppContext())
            .asBitmap()
            .load(imageUrl)
            .apply(RequestOptions().error(TUIThemeManager.getAttrResId(TUILogin.getAppContext(), R.drawable.app_default_user_icon_light)))
            .into(targetImageSize, targetImageSize)
            .get()
    }
}