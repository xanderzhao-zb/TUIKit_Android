package com.tencent.uikit.app.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.tencent.qcloud.tuicore.TUILogin
import com.tencent.qcloud.tuicore.interfaces.TUICallback
import com.tencent.qcloud.tuicore.util.SPUtils
import com.tencent.qcloud.tuicore.util.ToastUtil
import com.tencent.qcloud.tuikit.debug.GenerateTestUserSig
import com.tencent.qcloud.tuikit.debug.GenerateTestUserSig.TENCENT_EFFECT_LICENSE_URL
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit.Companion.createInstance
import com.tencent.uikit.app.R
import com.tencent.uikit.app.main.BaseActivity
import com.tencent.uikit.app.main.MainActivity
import com.tencent.uikit.app.mine.UserManager

class LoginActivity : BaseActivity() {
    companion object {
        private const val TAG = "LoginActivity"
    }

    private var isTestEnv = false
    private lateinit var editUserId: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isTaskRoot
            && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
            && intent.action != null
            && intent.action.equals(Intent.ACTION_MAIN)) {
            finish();
            return;
        }
        setContentView(R.layout.app_activity_login)
        initView()
    }

    private fun initView() {
        editUserId = findViewById(R.id.et_userId)
        editUserId.setText(SPUtils.getInstance("livekit").getString("userId"))
        findViewById<View>(R.id.btn_login).setOnClickListener {
            val userId = editUserId.text.toString().trim()
            SPUtils.getInstance("app_uikit").put("userId", userId)
            login(userId)
        }
        findViewById<ImageView>(R.id.iv_login_logo).setOnLongClickListener {
            isTestEnv = !isTestEnv
            switchTestEnv(isTestEnv)
            true
        }
    }

    private fun login(userId: String) {
        if (userId.isEmpty()) {
            ToastUtil.toastShortMessage(getString(R.string.app_user_id_is_empty))
            return
        }
        val userSig = GenerateTestUserSig.genTestUserSig(userId)
        TUILogin.login(this, GenerateTestUserSig.SDKAPPID, userId, userSig, object : TUICallback() {
            override fun onSuccess() {
                Log.i(TAG, "login onSuccess")
                val instance = createInstance(application)
                instance.enableFloatWindow(true)
                instance.enableVirtualBackground(true)
                instance.enableIncomingBanner(true)
                getUserInfo()
            }

            override fun onError(errorCode: Int, errorMessage: String?) {
                ToastUtil.toastShortMessage(
                    getString(R.string.app_toast_login_fail, errorCode, errorMessage)
                )
                Log.e(TAG, "login fail errorCode: $errorCode errorMessage:$errorMessage")
            }
        })
    }

    private fun getUserInfo() {
        UserManager.getInstance().getSelfUserInfo(object : V2TIMValueCallback<V2TIMUserFullInfo> {
            override fun onError(errorCode: Int, errorMsg: String?) {
                Log.e(TAG, "getUserInfo failed, code:$errorCode msg: $errorMsg")
            }

            override fun onSuccess(timUserFullInfo: V2TIMUserFullInfo?) {
                if (timUserFullInfo == null) {
                    Log.e(TAG, "getUserInfo result is empty")
                    return
                }
                val userName = timUserFullInfo.nickName
                val userAvatar = timUserFullInfo.faceUrl
                if (userName.isNullOrEmpty() || userAvatar.isNullOrEmpty()) {
                    val intent = Intent(this@LoginActivity, RegisterActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        })
    }

    private fun switchTestEnv(enableTestEnv: Boolean) {
        V2TIMManager.getInstance().callExperimentalAPI(
            "setTestEnvironment", enableTestEnv, object : V2TIMValueCallback<Any?> {
                override fun onSuccess(o: Any?) {
                    Log.i(TAG, "V2TIMManager setNetEnv success. enableTestEnv：$enableTestEnv")
                }

                override fun onError(code: Int, desc: String?) {
                    Log.i(TAG, "V2TIMManager setNetEnv fail. enableTestEnv：$enableTestEnv")
                }
            })
        if (enableTestEnv) {
            ToastUtil.toastShortMessage("Test Env")
        } else {
            ToastUtil.toastShortMessage("Online Env")
        }
    }
}