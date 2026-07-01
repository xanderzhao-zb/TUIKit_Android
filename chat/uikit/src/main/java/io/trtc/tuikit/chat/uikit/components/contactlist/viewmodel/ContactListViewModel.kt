package io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

data class DefaultContactItem(
    val id: String,
    val titleResID: Int = 0,
    val iconResID: Int = 0,
    val badgeCount: StateFlow<Int>,
    val onClick: () -> Unit = {}
)

class ContactListViewModel(
    val contactStore: ContactStore,
    val groupStore: GroupStore
) : ViewModel() {

    private val contactState = contactStore.state
    private val groupStoreState = groupStore.state

    val groupApplicationCount: StateFlow<Int> = groupStoreState.unreadApplicationCount
    val friendApplicationCount: StateFlow<Int> = contactState.friendApplicationUnreadCount

    val friendList: StateFlow<List<ContactInfo>> = contactState.friendList.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        contactStore.loadFriends(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
        contactStore.loadFriendApplications(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
        groupStore.loadApplications(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
    }

    fun getDefaultItems(
        onNavigateToFriendApplications: () -> Unit = {},
        onNavigateToGroupApplications: () -> Unit = {},
        onNavigateToMyGroup: () -> Unit = {},
        onNavigateToBlacklist: () -> Unit = {}
    ): List<DefaultContactItem> {
        return listOf(
            DefaultContactItem(
                id = "new_contacts_applications",
                titleResID = R.string.contact_list_new_contacts,
                iconResID = R.drawable.contact_list_ic_new_contacts,
                badgeCount = friendApplicationCount,
                onClick = onNavigateToFriendApplications
            ),
            DefaultContactItem(
                id = "new_group_applications",
                titleResID = R.string.contact_list_new_group_applications,
                iconResID = R.drawable.contact_list_ic_group_notification,
                badgeCount = groupApplicationCount,
                onClick = onNavigateToGroupApplications
            ),
            DefaultContactItem(
                id = "my_group",
                titleResID = R.string.contact_list_my_group,
                iconResID = R.drawable.contact_list_ic_my_group,
                badgeCount = kotlinx.coroutines.flow.MutableStateFlow(0),
                onClick = onNavigateToMyGroup
            ),
            DefaultContactItem(
                id = "blacklist",
                titleResID = R.string.contact_list_blacklist,
                iconResID = R.drawable.contact_list_ic_blacklist,
                badgeCount = kotlinx.coroutines.flow.MutableStateFlow(0),
                onClick = onNavigateToBlacklist
            )
        )
    }
}

class ContactListViewModelFactory(
    private val contactStore: ContactStore = ContactStore.shared,
    private val groupStore: GroupStore = GroupStore.shared
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactListViewModel::class.java)) {
            return ContactListViewModel(contactStore, groupStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
