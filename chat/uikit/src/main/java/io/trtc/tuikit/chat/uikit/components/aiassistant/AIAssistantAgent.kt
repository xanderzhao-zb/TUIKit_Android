package io.trtc.tuikit.chat.uikit.components.aiassistant

/**
 * Host-provided AI assistant metadata.
 *
 * UIKit deliberately does not own the directory endpoint or any assistant account.
 */
data class AIAssistantAgent(
    val id: String,
    val name: String,
    val welcome: String,
    val imUserId: String,
    val imNickname: String,
) {
    val conversationID: String
        get() = "c2c_$imUserId"

    val displayName: String
        get() = imNickname.ifBlank { name }.ifBlank { imUserId }
}

fun interface AIAssistantAgentLoader {
    fun load(onResult: (List<AIAssistantAgent>) -> Unit)
}
