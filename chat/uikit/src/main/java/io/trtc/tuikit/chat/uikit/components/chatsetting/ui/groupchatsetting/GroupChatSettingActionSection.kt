package io.trtc.tuikit.chat.uikit.components.chatsetting.ui.groupchatsetting
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingActionConfig
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingActionContext
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingActionStyle
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingCustomAction
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingScene
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupPermission
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.GroupMemberPickerDialog
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.SettingRowButton
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.GroupChatSettingViewModel
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole
import io.trtc.tuikit.atomicxcore.api.group.GroupType

internal class GroupChatSettingActionSection(
    private val context: Context,
    private val createDivider: () -> View,
    private val createSpacer: () -> View,
    private val canPerformAction: (GroupType, GroupMemberRole, GroupPermission) -> Boolean,
    private val onGroupDeletedProvider: () -> (() -> Unit)?
) {
    fun rebuild(
        actionSection: LinearLayout,
        actionSpacer: View,
        viewModel: GroupChatSettingViewModel,
        groupType: GroupType,
        selfRole: GroupMemberRole
    ) {
        actionSection.removeAllViews()

        val builtInRows = mutableListOf<SettingRowButton>()
        if (canPerformAction(groupType, selfRole, GroupPermission.TRANSFER_OWNER)) {
            builtInRows.add(createTransferOwnerRow(viewModel))
        }

        if (canPerformAction(groupType, selfRole, GroupPermission.CLEAR_HISTORY_MESSAGES)) {
            builtInRows.add(createClearHistoryRow(viewModel))
        }

        if (canPerformAction(groupType, selfRole, GroupPermission.DELETE_AND_QUIT)) {
            builtInRows.add(createDeleteAndQuitRow(viewModel))
        }

        if (canPerformAction(groupType, selfRole, GroupPermission.DISMISS_GROUP)) {
            builtInRows.add(createDismissGroupRow(viewModel))
        }

        val customRows = customActions(viewModel).map { createCustomActionRow(it) }

        builtInRows.forEachIndexed { index, row ->
            actionSection.addView(row)
            if (index != builtInRows.lastIndex) {
                actionSection.addView(createDivider())
            }
        }

        if (customRows.isNotEmpty()) {
            if (builtInRows.isNotEmpty()) {
                actionSection.addView(createSpacer())
            }
            customRows.forEachIndexed { index, row ->
                actionSection.addView(row)
                if (index != customRows.lastIndex) {
                    actionSection.addView(createDivider())
                }
            }
        }

        val hasActions = builtInRows.isNotEmpty() || customRows.isNotEmpty()
        actionSection.visibility = if (hasActions) View.VISIBLE else View.GONE
        actionSpacer.visibility = if (hasActions) View.VISIBLE else View.GONE
    }

    private fun customActions(viewModel: GroupChatSettingViewModel): List<ChatSettingCustomAction> {
        val provider = ChatSettingActionConfig.customActionProvider ?: return emptyList()
        return provider.getActions(
            ChatSettingActionContext(
                context = context,
                scene = ChatSettingScene.GROUP,
                userID = null,
                groupID = viewModel.groupID
            )
        )
    }

    private fun createCustomActionRow(action: ChatSettingCustomAction): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(action.title)
            setButtonStyle(
                when (action.style) {
                    ChatSettingActionStyle.LINK -> SettingRowButton.Style.LINK
                    ChatSettingActionStyle.DANGER -> SettingRowButton.Style.DANGER
                    ChatSettingActionStyle.NORMAL -> SettingRowButton.Style.NORMAL
                }
            )
            setOnClickListener { action.onClick(context) }
        }
    }

    private fun createTransferOwnerRow(viewModel: GroupChatSettingViewModel): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_transfer_group_owner))
            setButtonStyle(SettingRowButton.Style.LINK)
            setOnClickListener {
                viewModel.loadAllGroupMembers {
                    val candidates = viewModel.memberList.value
                        .filter { it.role != GroupMemberRole.OWNER }
                    GroupMemberPickerDialog(
                        context = context,
                        title = context.getString(R.string.chat_setting_transfer_group_owner),
                        candidates = candidates,
                        maxSelection = 1,
                        onConfirm = { selected ->
                            val member = selected.firstOrNull() ?: return@GroupMemberPickerDialog
                            AtomicAlertDialog(context).apply {
                                init {
                                    content = context.getString(R.string.chat_setting_tansfer_owner_tips)
                                    confirmButton(
                                        context.getString(R.string.uikit_confirm),
                                        type = AtomicAlertDialog.TextColorPreset.RED
                                    ) { _ ->
                                        viewModel.changeOwner(member.userID)
                                    }
                                    cancelButton(context.getString(R.string.uikit_cancel))
                                }
                                show()
                            }
                        }
                    ).show()
                }
            }
        }
    }

    private fun createClearHistoryRow(viewModel: GroupChatSettingViewModel): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_clear_history_messages))
            setDangerStyle(true)
            setOnClickListener {
                AtomicAlertDialog(context).apply {
                    init {
                        content = context.getString(R.string.chat_setting_clear_group_history_messages_tips)
                        confirmButton(context.getString(R.string.uikit_confirm)) { _ ->
                            viewModel.clearChatHistory()
                        }
                        cancelButton(context.getString(R.string.uikit_cancel))
                    }
                    show()
                }
            }
        }
    }

    private fun createDeleteAndQuitRow(viewModel: GroupChatSettingViewModel): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_delete_and_quit))
            setDangerStyle(true)
            setOnClickListener {
                AtomicAlertDialog(context).apply {
                    init {
                        content = context.getString(R.string.chat_setting_delete_and_quit_tips)
                        confirmButton(
                            context.getString(R.string.uikit_confirm),
                            type = AtomicAlertDialog.TextColorPreset.RED
                        ) { _ ->
                            viewModel.quitGroup(
                                onSuccess = { onGroupDeletedProvider()?.invoke() },
                                onFailure = { _, desc ->
                                    AtomicToast.show(context, desc, style = AtomicToast.Style.ERROR)
                                }
                            )
                        }
                        cancelButton(context.getString(R.string.uikit_cancel))
                    }
                    show()
                }
            }
        }
    }

    private fun createDismissGroupRow(viewModel: GroupChatSettingViewModel): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_dismiss_group))
            setDangerStyle(true)
            setOnClickListener {
                AtomicAlertDialog(context).apply {
                    init {
                        content = context.getString(R.string.chat_setting_dismiss_group_tips)
                        confirmButton(
                            context.getString(R.string.uikit_confirm),
                            type = AtomicAlertDialog.TextColorPreset.RED
                        ) { _ ->
                            viewModel.dismissGroup(
                                onSuccess = { onGroupDeletedProvider()?.invoke() },
                                onFailure = { _, desc ->
                                    AtomicToast.show(context, desc, style = AtomicToast.Style.ERROR)
                                }
                            )
                        }
                        cancelButton(context.getString(R.string.uikit_cancel))
                    }
                    show()
                }
            }
        }
    }
}
