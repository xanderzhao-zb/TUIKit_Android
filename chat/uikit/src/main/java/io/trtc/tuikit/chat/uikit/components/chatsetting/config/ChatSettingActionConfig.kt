package io.trtc.tuikit.chat.uikit.components.chatsetting.config

import android.content.Context

enum class ChatSettingScene {
    C2C,
    GROUP
}

enum class ChatSettingActionStyle {
    NORMAL,
    LINK,
    DANGER
}

data class ChatSettingActionContext(
    val context: Context,
    val scene: ChatSettingScene,
    val userID: String?,
    val groupID: String?
)

data class ChatSettingCustomAction(
    val title: String,
    val style: ChatSettingActionStyle = ChatSettingActionStyle.NORMAL,
    val onClick: (Context) -> Unit
)

fun interface ChatSettingCustomActionProvider {
    fun getActions(actionContext: ChatSettingActionContext): List<ChatSettingCustomAction>
}

object ChatSettingActionConfig {

    @Volatile
    var customActionProvider: ChatSettingCustomActionProvider? = null

    fun setCustomActionProvider(provider: ChatSettingCustomActionProvider?): ChatSettingActionConfig {
        customActionProvider = provider
        return this
    }
}
