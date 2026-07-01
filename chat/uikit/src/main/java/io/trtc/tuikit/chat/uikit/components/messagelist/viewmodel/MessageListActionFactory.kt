package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageUIAction
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.AuxiliaryTextVisibilityStore
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageListMessageSummaryFormatter
import io.trtc.tuikit.atomicxcore.api.message.AudioMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageActionStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload

internal data class MessageListActionCallbacks(
    val onEnterMultiSelectMode: (MessageInfo) -> Unit,
    val onForwardSingleMessage: (MessageInfo) -> Unit,
    val onConvertVoiceToText: (MessageInfo) -> Unit,
    val onTranslateText: (MessageInfo) -> Unit,
    val onQuoteMessage: (MessageInfo, String) -> Unit,
    val onListenFromHere: (MessageInfo) -> Unit
)

internal class MessageListActionFactory(
    private val config: MessageListConfigProtocol,
    private val latestMessageProvider: (MessageInfo) -> MessageInfo,
    private val auxiliaryTextVisibilityStore: AuxiliaryTextVisibilityStore,
    private val callbacks: MessageListActionCallbacks
) {
    fun create(context: Context, messageInfo: MessageInfo): List<MessageUIAction> {
        val latestMessage = latestMessageProvider(messageInfo)
        val messageActionStore = MessageActionStore.create(latestMessage)
        val actions = mutableListOf<MessageUIAction>()

        if (config.isSupportMultiSelect) {
            actions.add(
                MessageUIAction(
                    name = context.getString(R.string.message_list_menu_multi_select),
                    icon = R.drawable.message_list_menu_multi_select_icon,
                    order = ACTION_ORDER_MULTI_SELECT,
                    action = { callbacks.onEnterMultiSelectMode(messageInfo) }
                )
            )
        }

        if (config.isSupportForward && latestMessage.status == MessageStatus.SEND_SUCCESS) {
            actions.add(
                MessageUIAction(
                    name = context.getString(R.string.message_list_menu_forward),
                    icon = R.drawable.message_list_menu_forward_icon,
                    order = ACTION_ORDER_FORWARD,
                    action = callbacks.onForwardSingleMessage
                )
            )
        }

        if (shouldShowQuoteAction(latestMessage)) {
            actions.add(
                MessageUIAction(
                    name = context.getString(R.string.message_list_menu_quote),
                    icon = R.drawable.message_list_menu_quote_icon,
                    order = ACTION_ORDER_QUOTE,
                    action = {
                        val summary = MessageListMessageSummaryFormatter(config).format(
                            context = context,
                            message = latestMessage
                        )
                        callbacks.onQuoteMessage(latestMessage, summary)
                    }
                )
            )
        }

        if (latestMessage.messageType == MessageType.TEXT && config.isSupportCopy) {
            actions.add(
                MessageUIAction(
                    name = context.getString(R.string.message_list_menu_copy),
                    icon = R.drawable.message_list_menu_copy_icon,
                    order = ACTION_ORDER_COPY,
                    action = { msg ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = (msg.messagePayload as? TextMessagePayload)?.text
                        val clip = ClipData.newPlainText("Copied Text", text)
                        clipboard.setPrimaryClip(clip)
                    }
                )
            )
        }

        if (MessageRecallActionPolicy.shouldShowRecall(
                isSentBySelf = latestMessage.isSentBySelf,
                status = latestMessage.status,
                timestamp = latestMessage.timestamp,
                isSupportRecall = config.isSupportRecall,
                currentTimeMs = System.currentTimeMillis()
            )
        ) {
            actions.add(
                MessageUIAction(
                    name = context.getString(R.string.message_list_menu_recall),
                    icon = R.drawable.message_list_menu_recall_icon,
                    order = ACTION_ORDER_RECALL,
                    action = { messageActionStore.revoke() }
                )
            )
        }

        if (config.isSupportDelete) {
            actions.add(
                MessageUIAction(
                    name = context.getString(R.string.message_list_menu_delete),
                    dangerousAction = true,
                    icon = R.drawable.message_list_menu_delete_icon,
                    order = ACTION_ORDER_DELETE,
                    action = { messageActionStore.delete() }
                )
            )
        }

        if (latestMessage.messageType == MessageType.AUDIO &&
            latestMessage.status == MessageStatus.SEND_SUCCESS
        ) {
            val msgId = latestMessage.msgID ?: ""
            val asrText = (latestMessage.messagePayload as? AudioMessagePayload)?.asrText
            val isHidden = auxiliaryTextVisibilityStore.isHidden(msgId)
            if (asrText.isNullOrEmpty() || isHidden) {
                actions.add(
                    MessageUIAction(
                        name = context.getString(R.string.message_list_menu_convert_to_text),
                        icon = R.drawable.message_list_menu_convert_icon,
                        order = ACTION_ORDER_CONVERT_TO_TEXT,
                        action = callbacks.onConvertVoiceToText
                    )
                )
            }
        }

        if (latestMessage.messageType == MessageType.TEXT &&
            latestMessage.status == MessageStatus.SEND_SUCCESS
        ) {
            val msgId = latestMessage.msgID ?: ""
            val translatedText = (latestMessage.messagePayload as? TextMessagePayload)?.translatedText
            val isHidden = auxiliaryTextVisibilityStore.isHidden(msgId)
            if (translatedText.isNullOrEmpty() || isHidden) {
                actions.add(
                    MessageUIAction(
                        name = context.getString(R.string.message_list_menu_translate),
                        icon = R.drawable.message_list_menu_translate_icon,
                        order = ACTION_ORDER_TRANSLATE,
                        action = callbacks.onTranslateText
                    )
                )
            }
        }

        actions.add(
            MessageUIAction(
                name = context.getString(R.string.voice_message_listen_from_here),
                icon = R.drawable.message_list_menu_listen_from_here_icon,
                order = ACTION_ORDER_LISTEN_FROM_HERE,
                action = { callbacks.onListenFromHere(messageInfo) }
            )
        )

        return actions
    }

    private fun shouldShowQuoteAction(message: MessageInfo): Boolean {
        return config.isSupportQuote &&
            message.status == MessageStatus.SEND_SUCCESS &&
            message.status != MessageStatus.VIOLATION
    }

    private companion object {
        const val ACTION_ORDER_MULTI_SELECT = 100
        const val ACTION_ORDER_FORWARD = 200
        const val ACTION_ORDER_QUOTE = 250
        const val ACTION_ORDER_COPY = 300
        const val ACTION_ORDER_RECALL = 400
        const val ACTION_ORDER_DELETE = 500
        const val ACTION_ORDER_CONVERT_TO_TEXT = 600
        const val ACTION_ORDER_TRANSLATE = 700
        const val ACTION_ORDER_LISTEN_FROM_HERE = 800
    }
}

internal object MessageRecallActionPolicy {
    private const val RECALL_WINDOW_SECONDS = 120L

    fun shouldShowRecall(
        isSentBySelf: Boolean,
        status: MessageStatus,
        timestamp: Long?,
        isSupportRecall: Boolean,
        currentTimeMs: Long,
    ): Boolean {
        if (!isSupportRecall || !isSentBySelf || status != MessageStatus.SEND_SUCCESS) {
            return false
        }
        val messageTime = timestamp ?: return true
        if (messageTime <= 0L) {
            return true
        }
        val timeDifferenceSeconds = (currentTimeMs - messageTime * 1000L) / 1000L
        return timeDifferenceSeconds <= RECALL_WINDOW_SECONDS
    }
}
