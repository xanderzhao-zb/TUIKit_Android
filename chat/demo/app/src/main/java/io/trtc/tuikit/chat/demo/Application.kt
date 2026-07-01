package io.trtc.tuikit.chat.demo

import io.trtc.tuikit.chat.demo.common.AppConstants

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingActionConfig
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingActionStyle
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingCustomAction
import io.trtc.tuikit.chat.uikit.components.config.AppBuilder
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.atomicx.theme.Theme
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicxcore.api.login.LoginListener
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.demo.login.LocalLoginActivity

class Application : Application() {

    private val loginListener = object : LoginListener() {
        override fun onKickedOffline() {
            redirectToLogin(R.string.demo_force_offline)
        }

        override fun onLoginExpired() {
            redirectToLogin(R.string.demo_login_expired)
        }
    }

    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
        AppBuilder.loadConfig(this)

        applyLanguageFromSettings()

        MMKV.defaultMMKV().decodeBool(AppConstants.KEY_ENABLE_READ_RECEIPT, false).also {
            AppBuilderConfig.enableReadReceipt = it
        }

        applyThemeFromSettings()
        registerChatSettingExtensions()
        LoginStore.shared.addLoginListener(loginListener)
    }

    private fun redirectToLogin(messageResId: Int) {
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER, "")
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TOKEN, "")
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TYPE, "")
        Toast.makeText(this, getString(messageResId), Toast.LENGTH_LONG).show()
        startActivity(Intent(this, LocalLoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
    }

    private fun registerChatSettingExtensions() {
        ChatSettingActionConfig.setCustomActionProvider { actionContext ->
            listOf(
                ChatSettingCustomAction(
                    title = actionContext.context.getString(R.string.demo_report),
                    style = ChatSettingActionStyle.DANGER,
                    onClick = { context -> openReportPage(context) }
                )
            )
        }
    }

    private fun openReportPage(context: Context) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.REPORT_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.demo_open_browser_failed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val themeMode = MMKV.defaultMMKV().decodeInt(AppConstants.KEY_THEME_MODE, AppConstants.THEME_MODE_SYSTEM)
        if (themeMode == AppConstants.THEME_MODE_SYSTEM) {
            applySystemTheme(newConfig)
        }
    }

    private fun applyThemeFromSettings() {
        val themeMode = MMKV.defaultMMKV().decodeInt(AppConstants.KEY_THEME_MODE, AppConstants.THEME_MODE_SYSTEM)
        val themeStore = ThemeStore.shared(this)
        when (themeMode) {
            AppConstants.THEME_MODE_LIGHT -> themeStore.setTheme(Theme.lightTheme(this))
            AppConstants.THEME_MODE_DARK -> themeStore.setTheme(Theme.darkTheme(this))
            else -> applySystemTheme(resources.configuration)
        }
    }

    private fun applyLanguageFromSettings() {
        val languageTag = MMKV.defaultMMKV().decodeString(AppConstants.KEY_APP_LANGUAGE, "").orEmpty()
        val targetLocales = if (languageTag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        if (AppCompatDelegate.getApplicationLocales() != targetLocales) {
            AppCompatDelegate.setApplicationLocales(targetLocales)
        }
    }

    private fun applySystemTheme(config: Configuration) {
        val isNight = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        ThemeStore.shared(this).setTheme(
            if (isNight) Theme.darkTheme(this) else Theme.lightTheme(this)
        )
    }

}

