package io.trtc.tuikit.chat.demo.main

import io.trtc.tuikit.chat.demo.common.BadgeDragPolicy
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.settings.SettingsPageView

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.ContactFlowLauncher
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.group.GetGroupInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupEvent
import io.trtc.tuikit.atomicxcore.api.group.GroupInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.search.SearchActivity
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarBadgeView
import io.trtc.tuikit.chat.uikit.pages.ContactsPageView
import io.trtc.tuikit.chat.uikit.pages.ConversationsPageView
import io.trtc.tuikit.chat.uikit.pages.PopupMenuHelper
import io.trtc.tuikit.chat.uikit.pages.PopupMenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

data class TabItem(
    val title: Int,
    val menuItemId: Int
)

private const val BADGE_HORIZONTAL_OFFSET_DP = 4
private const val BADGE_CLEAR_DRAG_THRESHOLD_DP = 48

class MainActivity : BaseActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var bottomNavContainer: FrameLayout
    private lateinit var messageUnreadBadge: AvatarBadgeView
    private lateinit var mainContainer: LinearLayout
    private lateinit var tabDivider: View

    private var conversationsAddButton: ImageView? = null
    private var contactsAddButton: ImageView? = null
    private var isDraggingMessageBadge = false
    private var messageBadgeDragStartRawX = 0f
    private var messageBadgeDragStartRawY = 0f
    private var messageBadgeAnchorLayoutListener: View.OnLayoutChangeListener? = null
    private var trackedMessageBadgeAnchorView: View? = null

    private val themeStore by lazy { ThemeStore.shared(this) }
    private val conversationListStore by lazy { ConversationListStore.create() }
    private val contactStore by lazy { ContactStore.shared }
    private val groupStore by lazy { GroupStore.shared }
    private var mainScope: CoroutineScope? = null

    private val tabItems = listOf(
        TabItem(R.string.demo_tab_messages, R.id.demo_tab_messages),
        TabItem(R.string.demo_tab_contacts, R.id.demo_tab_contacts),
        TabItem(R.string.demo_tab_me, R.id.demo_tab_me)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }

        setContentView(R.layout.demo_activity_main)

        mainContainer = findViewById(R.id.demo_mainContainer)
        viewPager = findViewById(R.id.demo_viewPager)
        bottomNavContainer = findViewById(R.id.demo_bottomNavContainer)
        bottomNav = findViewById(R.id.demo_bottomNav)
        messageUnreadBadge = findViewById(R.id.demo_messageUnreadBadge)
        tabDivider = findViewById(R.id.demo_tabDivider)

        ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            bottomNav.updatePadding(bottom = systemBars.bottom)
            insets
        }

        setupViewPager()
        setupBottomNav()
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)
        refreshUnreadCounts()
    }

    override fun onStart() {
        super.onStart()
        mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        refreshUnreadCounts()

        mainScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyColors(state.currentTheme.tokens.color)
            }
        }
        mainScope?.launch {
            conversationListStore.state.totalUnreadCount.collectLatest { unreadCount ->
                updateTabBadge(R.id.demo_tab_messages, unreadCount.toInt())
            }
        }
        mainScope?.launch {
            combine(
                contactStore.state.friendApplicationUnreadCount,
                groupStore.state.unreadApplicationCount
            ) { friendApplicationUnreadCount, groupApplicationUnreadCount ->
                friendApplicationUnreadCount + groupApplicationUnreadCount
            }.collectLatest { unreadCount ->
                updateTabBadge(R.id.demo_tab_contacts, unreadCount)
            }
        }
        mainScope?.launch {
            groupStore.groupEventFlow.collectLatest { event ->
                when (event) {
                    is GroupEvent.OnKickedFromGroup -> showGroupEventToast(
                        groupID = event.groupID,
                        messageResId = R.string.demo_group_event_kicked
                    )
                    is GroupEvent.OnGroupDismissed -> showGroupEventToast(
                        groupID = event.groupID,
                        messageResId = R.string.demo_group_event_dismissed
                    )
                    else -> Unit
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        mainScope?.cancel()
        mainScope = null
    }

    private fun applyColors(colors: ColorTokens) {
        mainContainer.setBackgroundColor(colors.bgColorOperate)
        bottomNav.setBackgroundColor(colors.bgColorBottomBar)
        tabDivider.setBackgroundColor(colors.strokeColorPrimary)

        conversationsAddButton?.setColorFilter(colors.textColorPrimary)
        contactsAddButton?.setColorFilter(colors.textColorPrimary)

        val tabColorStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(colors.textColorLink, colors.textColorSecondary)
        )
        bottomNav.itemIconTintList = tabColorStateList
        bottomNav.itemTextColor = tabColorStateList
        applyTabBadgeColors(colors)
        if (::messageUnreadBadge.isInitialized) {
            messageUnreadBadge.updateColors()
        }
    }

    private fun setupViewPager() {
        val pages = listOf(
            createConversationsPage(),
            createContactsPage(),
            createMePage()
        )

        viewPager.adapter = TabPagerAdapter(pages)
        viewPager.isUserInputEnabled = false
        viewPager.offscreenPageLimit = pages.size

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNav.menu.getItem(position).isChecked = true
            }
        })
    }

    private fun setupBottomNav() {
        bottomNav.itemTextAppearanceActive =
            com.google.android.material.R.style.TextAppearance_Material3_LabelSmall
        bottomNav.itemTextAppearanceInactive =
            com.google.android.material.R.style.TextAppearance_Material3_LabelSmall

        bottomNav.setOnItemSelectedListener { item ->
            val index = tabItems.indexOfFirst { it.menuItemId == item.itemId }
            if (index >= 0) {
                viewPager.setCurrentItem(index, false)
                true
            } else {
                false
            }
        }
        setupMessageBadgeDrag()
    }

    private fun setupMessageBadgeDrag() {
        val badgeElevationPx = 24.dpToPx().toFloat()
        messageUnreadBadge.elevation = badgeElevationPx
        messageUnreadBadge.translationZ = badgeElevationPx
        messageUnreadBadge.isClickable = true
        messageUnreadBadge.isFocusable = true
        bottomNavContainer.clipChildren = false
        bottomNavContainer.clipToPadding = false
        mainContainer.clipChildren = false
        mainContainer.clipToPadding = false
        bottomNavContainer.bringChildToFront(messageUnreadBadge)
        messageUnreadBadge.setOnTouchListener { badgeView, event ->
            handleMessageBadgeTouch(badgeView, event)
        }
        bottomNav.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
            if (messageUnreadBadge.visibility == View.VISIBLE && !isDraggingMessageBadge) {
                positionMessageUnreadBadge()
            }
        }
    }

    private fun handleMessageBadgeTouch(badgeView: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (badgeView.visibility != View.VISIBLE) {
                    return false
                }
                isDraggingMessageBadge = true
                messageBadgeDragStartRawX = event.rawX
                messageBadgeDragStartRawY = event.rawY
                badgeView.parent?.requestDisallowInterceptTouchEvent(true)
                badgeView.animate().cancel()
                badgeView.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120L).start()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDraggingMessageBadge) {
                    return false
                }
                updateDraggingMessageBadge(badgeView, event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDraggingMessageBadge) {
                    return false
                }
                val shouldClear = BadgeDragPolicy.shouldClearUnread(
                    messageBadgeDragStartRawY,
                    event.rawY,
                    BADGE_CLEAR_DRAG_THRESHOLD_DP.dpToPx()
                )
                isDraggingMessageBadge = false
                badgeView.parent?.requestDisallowInterceptTouchEvent(false)
                if (shouldClear) {
                    clearAllUnreadByBadgeDrag(badgeView)
                } else {
                    resetMessageBadgeDrag(badgeView)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!isDraggingMessageBadge) {
                    return false
                }
                isDraggingMessageBadge = false
                badgeView.parent?.requestDisallowInterceptTouchEvent(false)
                resetMessageBadgeDrag(badgeView)
                return true
            }
        }
        return false
    }

    private fun updateDraggingMessageBadge(badgeView: View, rawX: Float, rawY: Float) {
        val thresholdPx = BADGE_CLEAR_DRAG_THRESHOLD_DP.dpToPx()
        val dragOffset = BadgeDragPolicy.badgeDragOffset(
            messageBadgeDragStartRawX,
            messageBadgeDragStartRawY,
            rawX,
            rawY
        )
        val progress = (abs(dragOffset.y).toFloat() / thresholdPx).coerceIn(0f, 1f)
        val scale = 1f + progress * 0.2f
        badgeView.translationX = dragOffset.x.toFloat()
        badgeView.translationY = dragOffset.y.toFloat()
        badgeView.scaleX = scale
        badgeView.scaleY = scale
    }

    private fun resetMessageBadgeDrag(badgeView: View) {
        badgeView.animate().cancel()
        badgeView.animate()
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(180L)
            .start()
    }

    private fun clearAllUnreadByBadgeDrag(badgeView: View) {
        badgeView.animate().cancel()
        badgeView.animate()
            .translationX(0f)
            .translationY(-BADGE_CLEAR_DRAG_THRESHOLD_DP.dpToPx().toFloat() * 1.5f)
            .scaleX(0.6f)
            .scaleY(0.6f)
            .alpha(0f)
            .setDuration(180L)
            .withEndAction {
                badgeView.visibility = View.INVISIBLE
                badgeView.translationX = 0f
                badgeView.translationY = 0f
                badgeView.scaleX = 1f
                badgeView.scaleY = 1f
                badgeView.alpha = 1f
            }
            .start()
        runCatching {
            conversationListStore.clearConversationUnreadCount("")
        }.onSuccess {
            Toast.makeText(this, R.string.demo_clear_all_unread_success, Toast.LENGTH_SHORT).show()
        }.onFailure {
            resetMessageBadgeDrag(badgeView)
        }
    }

    private fun refreshUnreadCounts() {
        groupStore.loadApplications(object : CompletionHandler {
            override fun onSuccess() {
            }

            override fun onFailure(code: Int, desc: String) {
            }
        })
        contactStore.loadFriendApplications(object : CompletionHandler {
            override fun onSuccess() {
            }

            override fun onFailure(code: Int, desc: String) {
            }
        })
    }

    private fun showGroupEventToast(groupID: String, messageResId: Int) {
        val fallbackName = groupID.ifEmpty { getString(R.string.demo_group_event_unknown_group) }
        groupStore.getGroupInfo(
            groupID,
            object : GetGroupInfoCompletionHandler {
                override fun onSuccess(groupInfo: GroupInfo) {
                    val groupName = groupInfo.groupName?.takeIf { it.isNotEmpty() } ?: fallbackName
                    Toast.makeText(this@MainActivity, getString(messageResId, groupName), Toast.LENGTH_LONG).show()
                }

                override fun onFailure(code: Int, desc: String) {
                    Toast.makeText(this@MainActivity, getString(messageResId, fallbackName), Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun applyTabBadgeColors(colors: ColorTokens) {
        bottomNav.getBadge(R.id.demo_tab_contacts)?.let { badge ->
            configureTabBadge(badge, colors)
        }
    }

    private fun configureTabBadge(
        badge: BadgeDrawable,
        colors: ColorTokens
    ) {
        badge.backgroundColor = colors.textColorError
        badge.badgeTextColor = 0xFFFFFFFF.toInt()
        badge.badgeGravity = BadgeDrawable.TOP_END
        badge.horizontalOffset = BADGE_HORIZONTAL_OFFSET_DP.dpToPx()
        badge.verticalOffset = 0
        badge.maxCharacterCount = 3
    }

    private fun updateTabBadge(menuItemId: Int, unreadCount: Int) {
        if (menuItemId == R.id.demo_tab_messages) {
            updateMessageTabBadge(unreadCount)
            return
        }
        if (unreadCount > 0) {
            val badge = bottomNav.getOrCreateBadge(menuItemId)
            configureTabBadge(badge, themeStore.themeState.value.currentTheme.tokens.color)
            badge.isVisible = true
            badge.number = unreadCount
        } else {
            bottomNav.removeBadge(menuItemId)
        }
    }

    private fun updateMessageTabBadge(unreadCount: Int) {
        bottomNav.removeBadge(R.id.demo_tab_messages)
        if (unreadCount <= 0) {
            if (!isDraggingMessageBadge) {
                messageUnreadBadge.visibility = View.INVISIBLE
            }
            return
        }
        if (isDraggingMessageBadge) {
            return
        }
        val text = if (unreadCount > 99) "99+" else unreadCount.toString()
        messageUnreadBadge.setType(AvatarBadgeView.BadgeType.Text)
        messageUnreadBadge.setText(text)
        messageUnreadBadge.updateColors()
        messageUnreadBadge.alpha = 1f
        messageUnreadBadge.scaleX = 1f
        messageUnreadBadge.scaleY = 1f
        messageUnreadBadge.translationX = 0f
        messageUnreadBadge.translationY = 0f
        showMessageBadgeWhenReady()
    }

    private fun showMessageBadgeWhenReady() {
        val itemView = findMessageTabItemView()
        if (itemView == null || itemView.width == 0) {
            bottomNav.post { showMessageBadgeWhenReady() }
            return
        }
        val anchorView = findMessageBadgeAnchorView(itemView)
        if (anchorView.width == 0) {
            bottomNav.post { showMessageBadgeWhenReady() }
            return
        }
        attachMessageBadgeAnchorLayoutListener(anchorView)
        val unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        messageUnreadBadge.measure(unspec, unspec)
        applyMessageBadgeLayoutParams(anchorView)
        if (messageUnreadBadge.visibility != View.VISIBLE) {
            messageUnreadBadge.visibility = View.VISIBLE
        }
    }

    private fun applyMessageBadgeLayoutParams(anchorView: View) {
        val anchorLoc = IntArray(2).also { anchorView.getLocationInWindow(it) }
        val containerLoc = IntArray(2).also { bottomNavContainer.getLocationInWindow(it) }
        val anchorLeftInContainer = anchorLoc[0] - containerLoc[0]
        val anchorTopInContainer = anchorLoc[1] - containerLoc[1]
        val isRtl = bottomNav.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val badgeW = messageUnreadBadge.width.takeIf { it > 0 }
            ?: messageUnreadBadge.measuredWidth.takeIf { it > 0 }
            ?: return
        val badgeH = messageUnreadBadge.height.takeIf { it > 0 }
            ?: messageUnreadBadge.measuredHeight.takeIf { it > 0 }
            ?: return
        val badgePosition = BadgeDragPolicy.badgeTopEndPosition(
            anchorLeft = anchorLeftInContainer,
            anchorTop = anchorTopInContainer,
            anchorWidth = anchorView.width,
            badgeWidth = badgeW,
            badgeHeight = badgeH,
            horizontalOffsetPx = 0,
            verticalOffsetPx = 0,
            isRtl = isRtl
        )
        val existing = messageUnreadBadge.layoutParams as? FrameLayout.LayoutParams
        if (existing != null &&
            existing.leftMargin == badgePosition.left &&
            existing.topMargin == badgePosition.top &&
            existing.rightMargin == 0 &&
            existing.gravity == (Gravity.TOP or Gravity.LEFT) &&
            existing.width == FrameLayout.LayoutParams.WRAP_CONTENT &&
            existing.height == FrameLayout.LayoutParams.WRAP_CONTENT
        ) {
            return
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            leftMargin = badgePosition.left
            topMargin = badgePosition.top
            rightMargin = 0
            bottomMargin = 0
        }
        messageUnreadBadge.layoutParams = params
    }

    private fun attachMessageBadgeAnchorLayoutListener(anchorView: View) {
        if (trackedMessageBadgeAnchorView === anchorView) return
        trackedMessageBadgeAnchorView?.let { prev ->
            messageBadgeAnchorLayoutListener?.let { prev.removeOnLayoutChangeListener(it) }
        }
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (messageUnreadBadge.visibility == View.VISIBLE && !isDraggingMessageBadge) {
                positionMessageUnreadBadge()
            }
        }
        anchorView.addOnLayoutChangeListener(listener)
        trackedMessageBadgeAnchorView = anchorView
        messageBadgeAnchorLayoutListener = listener
    }

    private fun positionMessageUnreadBadge() {
        if (messageUnreadBadge.visibility != View.VISIBLE) return
        val itemView = findMessageTabItemView() ?: return
        val anchorView = findMessageBadgeAnchorView(itemView)
        if (anchorView.width == 0) return
        attachMessageBadgeAnchorLayoutListener(anchorView)
        applyMessageBadgeLayoutParams(anchorView)
    }

    private fun findMessageTabItemView(): View? {
        val itemIndex = tabItems.indexOfFirst { it.menuItemId == R.id.demo_tab_messages }
        if (itemIndex < 0) {
            return null
        }
        val menuView = (0 until bottomNav.childCount)
            .map { bottomNav.getChildAt(it) }
            .filterIsInstance<ViewGroup>()
            .firstOrNull { it.childCount >= tabItems.size }
            ?: return null
        return menuView.getChildAt(itemIndex)
    }

    private fun findMessageBadgeAnchorView(itemView: View): View {
        return itemView.findViewById<View>(com.google.android.material.R.id.navigation_bar_item_icon_view)
            ?: findFirstImageView(itemView)
            ?: itemView
    }

    private fun findFirstImageView(view: View): ImageView? {
        if (view is ImageView) {
            return view
        }
        if (view !is ViewGroup) {
            return null
        }
        for (index in 0 until view.childCount) {
            findFirstImageView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun createConversationsPage(): View {
        val page = ConversationsPageView(this)
        page.setHeaderTitle(getString(R.string.demo_page_conversations_title))
        val addButton = ImageView(this).apply {
            setImageResource(io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_add_circle)
            layoutParams = ViewGroup.LayoutParams(24.dpToPx(), 24.dpToPx())
        }
        conversationsAddButton = addButton
        addButton.setOnClickListener { anchor ->
            PopupMenuHelper(this).show(
                anchor,
                listOf(
                    PopupMenuItem(
                        title = getString(R.string.demo_start_chat),
                        onClick = { page.onStartChatClick?.invoke() },
                        iconResId = io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_user_add
                    ),
                    PopupMenuItem(
                        title = getString(R.string.demo_create_group),
                        onClick = { page.onCreateGroupClick?.invoke() },
                        iconResId = io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_chat_add
                    )
                )
            )
        }
        page.setHeaderRightAction(addButton)
        page.onConversationClick = { conversationID ->
            ChatActivity.start(this, conversationID)
        }
        page.onSearchClick = {
            SearchActivity.Companion.start(this)
        }
        page.onStartChatClick = {
            ContactFlowLauncher.showStartSingleChatPage(this) { conversationId ->
                ChatActivity.start(this, conversationId)
            }
        }
        page.onCreateGroupClick = {
            ContactFlowLauncher.showCreateGroupChatPage(this) { conversationId ->
                ChatActivity.start(this, conversationId)
            }
        }
        return page
    }

    private fun createContactsPage(): View {
        val page = ContactsPageView(this)
        page.setHeaderTitle(getString(R.string.demo_page_contacts_title))
        val addButton = ImageView(this).apply {
            setImageResource(io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_add_circle)
            layoutParams = ViewGroup.LayoutParams(24.dpToPx(), 24.dpToPx())
        }
        contactsAddButton = addButton
        addButton.setOnClickListener { anchor ->
            PopupMenuHelper(this).show(
                anchor,
                listOf(
                    PopupMenuItem(
                        title = getString(R.string.demo_add_friend),
                        onClick = { page.onAddFriendClick?.invoke() },
                        iconResId = io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_user_add
                    ),
                    PopupMenuItem(
                        title = getString(R.string.demo_add_group),
                        onClick = { page.onAddGroupClick?.invoke() },
                        iconResId = io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_chat_add
                    )
                )
            )
        }
        page.setHeaderRightAction(addButton)
        page.setup(
            onContactClick = { contactInfo ->
                ChatActivity.start(this, "c2c_${contactInfo.userID}")
            },
            onGroupClick = { contactInfo ->
                ChatActivity.start(this, "group_${contactInfo.userID}")
            }
        )
        page.onAddFriendClick = {
            ContactFlowLauncher.showAddFriendPage(this)
        }
        page.onAddGroupClick = {
            ContactFlowLauncher.showAddGroupPage(this)
        }
        return page
    }

    private fun createMePage(): View {
        val page = SettingsPageView(this)
        page.setHeaderTitle(getString(R.string.demo_page_me_title))
        return page
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}

class TabPagerAdapter(
    private val pages: List<View>
) : RecyclerView.Adapter<TabPagerAdapter.PageViewHolder>() {

    class PageViewHolder(val pageView: View) : RecyclerView.ViewHolder(pageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = pages[viewType]
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
    }

    override fun getItemCount(): Int = pages.size

    override fun getItemViewType(position: Int): Int = position
}
