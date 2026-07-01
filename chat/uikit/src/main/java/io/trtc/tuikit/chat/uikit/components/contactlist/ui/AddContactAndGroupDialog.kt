package io.trtc.tuikit.chat.uikit.components.contactlist.ui
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.WindowThemeUtil
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.AddContactFlowStep
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.AddContactNavigator
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.SelfWordingProvider
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.buildAddContactActionCard
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.buildAddContactCardRow
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.buildAddContactInfoCard
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.buildAddContactMultilineInputCard
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.buildAddContactSearchEmptyView
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.buildAddContactSearchResultView
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.buildAddContactSectionSpacer
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.buildAddContactSectionTitle
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.displayName
import io.trtc.tuikit.chat.uikit.components.common.expandTouchTarget
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.findContactListViewModelStoreOwner
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.hideKeyboard
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.setAfterTextChangedListener
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddContactAndGroupViewModel
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddContactAndGroupViewModelFactory
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddType
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.ContactRequestResultPolicy
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class AddContactAndGroupDialog(
    context: Context,
    private val addType: AddType,
    private val contactStore: ContactStore = ContactStore.shared,
    private val groupStore: GroupStore = GroupStore.shared,
    private val initialContactInfo: ContactInfo? = null
) : Dialog(context, android.R.style.Theme_NoTitleBar) {

    private val viewModel: AddContactAndGroupViewModel by lazy {
        val owner = context.findContactListViewModelStoreOwner()
            ?: error("AddContactAndGroupDialog requires a ViewModelStoreOwner host context.")
        val key = "${AddContactAndGroupViewModel::class.java.name}:${System.identityHashCode(this)}"
        ViewModelProvider(owner, AddContactAndGroupViewModelFactory(contactStore, groupStore))
            .get(key, AddContactAndGroupViewModel::class.java)
    }
    private var dialogScope: CoroutineScope? = null
    private var searchStateJob: Job? = null

    private lateinit var rootLayout: LinearLayout
    private lateinit var navBar: FrameLayout
    private lateinit var divider: View
    private lateinit var headerTitle: TextView
    private lateinit var headerBackIcon: ImageView
    private lateinit var contentContainer: FrameLayout

    private var currentStep = AddContactFlowStep.SEARCH
    private var selectedResult: ContactInfo? = null
    private var addFriendWordingDraft: String? = null
    private var addFriendRemarkDraft: String? = null
    private var groupJoinMessageDraft: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val colors = getColors()
        val dm = context.resources.displayMetrics

        rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setBackgroundColor(colors.bgColorOperate)
            fitsSystemWindows = true
        }

        val navBarHeight = dp2px(56f, dm).toInt()
        navBar = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                navBarHeight
            )
            setPadding(dp2px(16f, dm).toInt(), 0, dp2px(16f, dm).toInt(), 0)
            setBackgroundColor(colors.bgColorOperate)
        }

        val backRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            )
            contentDescription = context.getString(R.string.contact_list_back)
            setOnClickListener { handleBack() }
        }

        val backIconSize = dp2px(16f, dm).toInt()
        headerBackIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(backIconSize, backIconSize)
            setImageResource(R.drawable.uikit_ic_back)
            imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        backRow.addView(headerBackIcon)
        navBar.addView(backRow)
        backRow.expandTouchTarget()

        headerTitle = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorPrimary)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }
        navBar.addView(headerTitle)
        rootLayout.addView(navBar)

        divider = View(context).apply {
            setBackgroundColor(colors.strokeColorSecondary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(0.5f, dm).toInt().coerceAtLeast(1)
            )
        }
        rootLayout.addView(divider)

        contentContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        rootLayout.addView(contentContainer)

        setContentView(
            rootLayout,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            WindowThemeUtil.applyDialogSystemBarStyle(this, colors)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        val preset = initialContactInfo
        if (preset != null) {
            selectedResult = preset
            showContactDetailStep()
        } else {
            showSearchStep()
        }
    }

    override fun onStart() {
        super.onStart()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        dialogScope = scope

        scope.launch {
            viewModel.uiState.collectLatest { state ->
                state.requestResult?.let { result ->
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    viewModel.clearRequestResult()
                    if (ContactRequestResultPolicy.shouldDismissAfterRequest(result.isSuccess)) {
                        dismiss()
                    }
                }
            }
        }

        scope.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                refreshCurrentStep()
            }
        }

        if (currentStep == AddContactFlowStep.SEARCH && initialContactInfo == null) {
            showSearchStep()
        }
    }

    private fun refreshCurrentStep() {
        when (currentStep) {
            AddContactFlowStep.SEARCH -> if (initialContactInfo == null) showSearchStep()
            AddContactFlowStep.CONTACT_DETAIL -> showContactDetailStep()
            AddContactFlowStep.ADD_FRIEND_FORM -> showAddFriendFormStep()
            AddContactFlowStep.GROUP_JOIN_FORM -> showGroupJoinFormStep()
            else -> if (initialContactInfo == null) showSearchStep()
        }
    }

    override fun onStop() {
        super.onStop()
        searchStateJob?.cancel()
        searchStateJob = null
        dialogScope?.cancel()
        dialogScope = null
    }

    override fun onBackPressed() {
        handleBack()
    }

    private fun handleBack() {
        when (AddContactNavigator.backAction(currentStep, initialContactInfo != null)) {
            AddContactNavigator.BackAction.DISMISS -> {
                if (currentStep == AddContactFlowStep.SEARCH) {
                    viewModel.clearSearchResults()
                }
                dismiss()
            }
            AddContactNavigator.BackAction.SHOW_SEARCH -> showSearchStep()
            AddContactNavigator.BackAction.SHOW_CONTACT_DETAIL -> showContactDetailStep()
            else -> {
                if (currentStep == AddContactFlowStep.SEARCH) {
                    viewModel.clearSearchResults()
                }
                dismiss()
            }
        }
    }

    private fun updateHeader() {
        val titleRes = AddContactNavigator.titleRes(currentStep, addType)
        headerTitle.text = context.getString(titleRes)
        applyWindowTheme()
    }

    private fun showSearchStep() {
        currentStep = AddContactFlowStep.SEARCH
        updateHeader()
        contentContainer.removeAllViews()

        val colors = getColors()
        val dm = context.resources.displayMetrics

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(colors.bgColorOperate)
        }

        val searchBarContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(
                dp2px(16f, dm).toInt(), dp2px(12f, dm).toInt(),
                dp2px(16f, dm).toInt(), dp2px(12f, dm).toInt()
            )
        }

        val inputContainer = FrameLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(
                0, dp2px(36f, dm).toInt(), 1f
            )
            background = GradientDrawable().apply {
                setColor(colors.bgColorInput)
                cornerRadius = dp2px(10f, dm)
            }
        }

        val searchInput = EditText(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            textDirection = View.TEXT_DIRECTION_LOCALE
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(colors.textColorPrimary)
            setHintTextColor(colors.textColorTertiary)
            hint = if (addType == AddType.CONTACT) {
                context.getString(R.string.contact_list_user_id)
            } else {
                context.getString(R.string.contact_list_group_id)
            }
            background = null
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            val horizontalPadding = dp2px(36f, dm).toInt()
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        inputContainer.addView(searchInput)

        val searchIcon = ImageView(context).apply {
            setImageResource(R.drawable.contact_list_ic_search)
            setColorFilter(colors.textColorTertiary)
            val iconSize = dp2px(15f, dm).toInt()
            layoutParams = FrameLayout.LayoutParams(
                iconSize, iconSize,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply {
                marginStart = dp2px(8f, dm).toInt()
            }
        }
        inputContainer.addView(searchIcon)

        val clearButton = ImageView(context).apply {
            setImageResource(R.drawable.contact_list_ic_search_clear)
            setColorFilter(colors.textColorPrimary)
            val iconSize = dp2px(16f, dm).toInt()
            layoutParams = FrameLayout.LayoutParams(
                iconSize, iconSize,
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply {
                marginEnd = dp2px(10f, dm).toInt()
            }
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            setOnClickListener { searchInput.setText("") }
        }
        inputContainer.addView(clearButton)

        searchBarContainer.addView(inputContainer)

        container.addView(searchBarContainer)

        val myIdTextView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(colors.textColorSecondary)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    dp2px(16f, dm).toInt(),
                    dp2px(80f, dm).toInt(),
                    dp2px(16f, dm).toInt(),
                    0
                )
            }
            visibility = View.GONE
        }
        container.addView(myIdTextView)

        val resultContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(resultContainer)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                viewModel.updateSearchKeyword(text)
                val notEmpty = text.isNotEmpty()
                clearButton.visibility = if (notEmpty) View.VISIBLE else View.GONE
                if (!notEmpty) {
                    resultContainer.removeAllViews()
                }
            }
        })
        searchInput.setText(viewModel.uiState.value.searchKeyword)

        val performSearch = {
            searchInput.hideKeyboard()
            if (addType == AddType.CONTACT) {
                viewModel.searchContact()
            } else {
                viewModel.searchGroup()
            }
        }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        contentContainer.addView(container)

        searchStateJob?.cancel()
        searchStateJob = dialogScope?.launch {
            viewModel.uiState.collectLatest { state ->
                if (currentStep != AddContactFlowStep.SEARCH) return@collectLatest

                if (addType == AddType.CONTACT && state.currentUserId.isNotEmpty() && state.searchKeyword.isEmpty()) {
                    myIdTextView.text = context.getString(
                        R.string.contact_list_label_value_format,
                        context.getString(R.string.contact_list_my_user_id),
                        state.currentUserId
                    )
                    myIdTextView.visibility = View.VISIBLE
                } else {
                    myIdTextView.visibility = View.GONE
                }

                resultContainer.removeAllViews()
                val info = if (addType == AddType.CONTACT) state.addFriendInfo else state.joinGroupInfo
                if (info != null) {
                    resultContainer.addView(
                        buildAddContactSearchResultView(
                            context = context,
                            colors = getColors(),
                            addType = addType,
                            result = info,
                            isJoinGroupAlready = state.isJoinGroupAlready
                        ) { result ->
                            if (addType == AddType.CONTACT && result.isFriend == true) return@buildAddContactSearchResultView
                            if (addType == AddType.GROUP && state.isJoinGroupAlready) return@buildAddContactSearchResultView
                            selectedResult = result
                            clearFormDrafts()
                            if (addType == AddType.CONTACT) {
                                showContactDetailStep()
                            } else {
                                showGroupJoinFormStep()
                            }
                        }
                    )
                } else if (!state.isSearching && state.searchKeyword.isNotEmpty()) {
                    resultContainer.addView(buildAddContactSearchEmptyView(context, getColors()))
                }
            }
        }
    }

    private fun showContactDetailStep() {
        currentStep = AddContactFlowStep.CONTACT_DETAIL
        updateHeader()
        contentContainer.removeAllViews()

        val result = selectedResult ?: return
        val colors = getColors()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(colors.bgColorDefault)
        }

        container.addView(buildAddContactInfoCard(context, colors, addType, result))

        container.addView(buildAddContactSectionSpacer(context, colors))

        container.addView(
            buildAddContactActionCard(
                context = context,
                colors = colors,
                text = context.getString(R.string.contact_list_add_contact),
                textColor = colors.textColorLink
            ) { showAddFriendFormStep() }
        )

        contentContainer.addView(container)
    }

    private fun showAddFriendFormStep() {
        currentStep = AddContactFlowStep.ADD_FRIEND_FORM
        updateHeader()
        contentContainer.removeAllViews()

        val result = selectedResult ?: return
        val colors = getColors()

        val scrollView = ScrollView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(colors.bgColorDefault)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(colors.bgColorDefault)
        }

        container.addView(buildAddContactInfoCard(context, colors, addType, result))

        container.addView(
            buildAddContactSectionTitle(context, colors, context.getString(R.string.contact_list_fill_validation_message))
        )

        val defaultWording = SelfWordingProvider.defaultWording(context)

        val wordingInput = buildAddContactMultilineInputCard(
            context = context,
            colors = colors,
            defaultValue = addFriendWordingDraft ?: defaultWording,
            minHeightDp = 120f
        )
        wordingInput.second.setAfterTextChangedListener {
            addFriendWordingDraft = it
        }
        container.addView(wordingInput.first)

        container.addView(buildAddContactSectionSpacer(context, colors))

        val remarkInput = buildAddContactCardRow(
            context = context,
            colors = colors,
            label = context.getString(R.string.contact_list_remark),
            defaultValue = addFriendRemarkDraft ?: result.displayName,
            editable = true
        )
        remarkInput.second.setAfterTextChangedListener {
            addFriendRemarkDraft = it
        }
        container.addView(remarkInput.first)

        container.addView(buildAddContactSectionSpacer(context, colors))

        container.addView(
            buildAddContactActionCard(
                context = context,
                colors = colors,
                text = context.getString(R.string.contact_list_send),
                textColor = colors.textColorLink
            ) {
                viewModel.addFriend(
                    result = result,
                    addWording = wordingInput.second.text.toString(),
                    remark = remarkInput.second.text.toString(),
                    successMessage = context.getString(R.string.contact_list_add_friend_success),
                    failureMessageMapper = { _, desc -> desc }
                )
            }
        )

        scrollView.addView(container)
        contentContainer.addView(scrollView)
    }

    private fun showGroupJoinFormStep() {
        currentStep = AddContactFlowStep.GROUP_JOIN_FORM
        updateHeader()
        contentContainer.removeAllViews()

        val result = selectedResult ?: return
        val colors = getColors()

        val scrollView = ScrollView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(colors.bgColorDefault)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.bgColorDefault)
        }

        container.addView(buildAddContactInfoCard(context, colors, addType, result))

        container.addView(
            buildAddContactSectionTitle(context, colors, context.getString(R.string.contact_list_fill_validation_message))
        )

        val defaultWording = SelfWordingProvider.defaultWording(context)

        val wordingInput = buildAddContactMultilineInputCard(
            context = context,
            colors = colors,
            defaultValue = groupJoinMessageDraft ?: defaultWording,
            minHeightDp = 120f
        )
        wordingInput.second.setAfterTextChangedListener {
            groupJoinMessageDraft = it
        }
        container.addView(wordingInput.first)

        container.addView(buildAddContactSectionSpacer(context, colors))

        container.addView(
            buildAddContactActionCard(
                context = context,
                colors = colors,
                text = context.getString(R.string.contact_list_send),
                textColor = colors.textColorLink
            ) {
                viewModel.joinGroup(
                    result = result,
                    message = wordingInput.second.text.toString(),
                    successMessage = context.getString(R.string.contact_list_join_group_request_sent),
                    failureMessageMapper = { _, desc -> desc }
                )
            }
        )

        scrollView.addView(container)
        contentContainer.addView(scrollView)
    }

    private fun clearFormDrafts() {
        addFriendWordingDraft = null
        addFriendRemarkDraft = null
        groupJoinMessageDraft = null
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }

    private fun applyWindowTheme() {
        val colors = getColors()
        if (::rootLayout.isInitialized) {
            rootLayout.setBackgroundColor(colors.bgColorOperate)
        }
        if (::navBar.isInitialized) {
            navBar.setBackgroundColor(colors.bgColorOperate)
        }
        if (::divider.isInitialized) {
            divider.setBackgroundColor(colors.strokeColorSecondary)
        }
        if (::headerTitle.isInitialized) {
            headerTitle.setTextColor(colors.textColorPrimary)
        }
        if (::headerBackIcon.isInitialized) {
            headerBackIcon.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        }
        window?.let { WindowThemeUtil.applyDialogSystemBarStyle(it, colors) }
    }
}
