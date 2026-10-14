package io.trtc.tuikit.chat.demo.chat

internal object ChatTitleResolver {
    fun resolve(
        conversationTitle: String? = null,
        conversationID: String
    ): String {
        val catalogName = io.trtc.tuikit.chat.demo.ai.SupportAgentCatalog
            .displayNameForConversation(conversationID)
        val fallback = catalogName ?: conversationID
        return conversationTitle.takeUnless { it.isNullOrBlank() } ?: fallback
    }
}
