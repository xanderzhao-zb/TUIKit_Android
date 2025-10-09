package com.tencent.uikit.app.main.live

import android.content.Intent
import android.os.Bundle
import android.widget.RelativeLayout
import androidx.appcompat.widget.Toolbar
import com.tencent.uikit.app.R
import com.tencent.uikit.app.main.BaseActivity
import com.trtc.uikit.livekit.livestream.VideoLiveListActivity
import com.trtc.uikit.livekit.voiceroom.VoiceRoomListActivity

class LiveActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_activity_live)
        initToolbar()
        initVideoLiveView()
        initVoiceRoomView()
    }

    private fun initToolbar() {
        val toolbar: Toolbar = findViewById(R.id.toolbar_live)
        toolbar.setNavigationIcon(com.trtc.uikit.livekit.R.drawable.livekit_ic_back)
        toolbar.setNavigationOnClickListener { v -> finish() }
    }

    private fun initVideoLiveView() {
        val layoutVideoLive: RelativeLayout = findViewById(R.id.video_live)
        layoutVideoLive.setOnClickListener { v ->
            val intent: Intent = Intent(this@LiveActivity, VideoLiveListActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun initVoiceRoomView() {
        val layoutVoiceRoom: RelativeLayout = findViewById(R.id.voice_room)
        layoutVoiceRoom.setOnClickListener { v ->
            val intent: Intent = Intent(this@LiveActivity, VoiceRoomListActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }
}