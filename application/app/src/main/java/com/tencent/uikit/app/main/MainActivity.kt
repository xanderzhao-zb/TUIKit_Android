package com.tencent.uikit.app.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.tencent.qcloud.tuicore.TUILogin
import com.tencent.uikit.app.R
import com.tencent.uikit.app.common.widget.RecycleFragmentNavigator
import com.tencent.uikit.app.login.LoginActivity
import com.tencent.uikit.app.mine.UserManager


class MainActivity : BaseActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private var mNavController: NavController? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_activity_trtc_main)


        mNavController = Navigation.findNavController(this, R.id.nav_host_fragment)
        val navHostFragment: NavHostFragment? =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment?
        val recycleFragmentNavigator: RecycleFragmentNavigator = RecycleFragmentNavigator(
            this,
            supportFragmentManager, navHostFragment?.getId() ?: 0
        )
        mNavController?.navigatorProvider?.addNavigator(recycleFragmentNavigator)
        mNavController?.setGraph(R.navigation.app_nav_main_graph)
    }

    override fun onResume() {
        super.onResume()
        getUserInfo()
    }

    private fun getUserInfo() {
        if (!TUILogin.isUserLogined()) {
            val intent = Intent(this@MainActivity, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        UserManager.getInstance().getSelfUserInfo(object : V2TIMValueCallback<V2TIMUserFullInfo> {
            override fun onError(errorCode: Int, errorMsg: String?) {
                Log.e(TAG, "getUserInfo failed, code:$errorCode msg: $errorMsg")
            }

            override fun onSuccess(timUserFullInfo: V2TIMUserFullInfo?) {
                if (timUserFullInfo == null) {
                    Log.e(TAG, "getUserInfo result is empty")
                    return
                }
            }
        })
    }
}