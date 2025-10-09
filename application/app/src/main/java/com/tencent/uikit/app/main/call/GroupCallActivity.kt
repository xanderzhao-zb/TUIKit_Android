package com.tencent.uikit.app.main.call

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.RelativeLayout
import com.google.gson.Gson
import com.tencent.cloud.tuikit.engine.call.TUICallDefine
import com.tencent.cloud.tuikit.engine.call.TUICallDefine.CallParams
import com.tencent.cloud.tuikit.engine.call.TUICallDefine.OfflinePushInfo
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine.RoomId
import com.tencent.qcloud.tuicore.util.ToastUtil
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import com.tencent.uikit.app.R
import com.tencent.uikit.app.main.BaseActivity
import java.util.Arrays

class GroupCallActivity : BaseActivity() {
    private var editGroupId: EditText? = null
    private var editUserList: EditText? = null
    private var mediaType = TUICallDefine.MediaType.Audio
    private var isOptionalParamViewExpand = false

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_activity_group_call)
        initView()
    }

    private fun initView() {
        editUserList = findViewById(R.id.et_user_id_list)
        editGroupId = findViewById(R.id.et_group_id)

        findViewById<View>(R.id.iv_back).setOnClickListener { onBackPressed() }
        findViewById<View>(R.id.btn_call).setOnClickListener { startGroupCall() }
        findViewById<View>(R.id.ll_setting).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<RadioGroup>(R.id.rg_media_type).setOnCheckedChangeListener { group: RadioGroup?, checkedId: Int ->
            mediaType = if (checkedId == R.id.rb_video) {
                TUICallDefine.MediaType.Video
            } else {
                TUICallDefine.MediaType.Audio
            }
        }
        val layoutChatGroupId: RelativeLayout = findViewById(R.id.rl_chat_group_id)
        findViewById<View>(R.id.ll_option).setOnClickListener {
            layoutChatGroupId.visibility = if (!isOptionalParamViewExpand) View.VISIBLE else View.GONE
            isOptionalParamViewExpand = !isOptionalParamViewExpand
        }
    }

    private fun startGroupCall() {
        val userList = editUserList!!.getText().toString()

        if (TextUtils.isEmpty(userList)) {
            ToastUtil.toastShortMessage(getString(R.string.app_please_input_user_id_list))
            return
        }
        var userIdList: MutableList<String?>? = null
        if (userList.contains(",")) {
            userIdList =
                Arrays.asList<String?>(*userList.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        } else if (userList.contains("，")) {
            userIdList =
                Arrays.asList<String?>(*userList.split("，".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        } else {
            userIdList = Arrays.asList<String?>(userList)
        }
        var callParams = createCallParams()
        if (callParams == null) {
            callParams = CallParams()
        }
        callParams.chatGroupId = editGroupId!!.getText().toString()
        TUICallKit.createInstance(this).calls(userIdList, mediaType, callParams, null)
    }

    private fun createCallParams(): CallParams? {
        try {
            if (SettingsConfig.callTimeOut !== 30 || !TextUtils.isEmpty(SettingsConfig.userData) || !TextUtils.isEmpty(
                    SettingsConfig.offlineParams
                ) || SettingsConfig.intRoomId !== 0 || !TextUtils.isEmpty(SettingsConfig.strRoomId)
            ) {
                val callParams = CallParams()
                callParams.timeout = SettingsConfig.callTimeOut
                callParams.userData = SettingsConfig.userData
                if (!TextUtils.isEmpty(SettingsConfig.offlineParams)) {
                    callParams.offlinePushInfo =
                        Gson().fromJson(SettingsConfig.offlineParams, OfflinePushInfo::class.java)
                }
                if (SettingsConfig.intRoomId !== 0 || !TextUtils.isEmpty(SettingsConfig.strRoomId)) {
                    val roomId = RoomId()
                    roomId.intRoomId = SettingsConfig.intRoomId
                    roomId.strRoomId = SettingsConfig.strRoomId
                    callParams.roomId = roomId
                }
                return callParams
            }
        } catch (e: Exception) {
        }
        return null
    }
}