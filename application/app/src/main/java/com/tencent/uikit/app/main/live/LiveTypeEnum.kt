package com.tencent.uikit.app.main.live

enum class LiveTypeEnum(val type: Int, mProperties: String) {
    TYPE_VIDEO_LIVE(100, "video_live"),
    TYPE_VOICE_ROOM(101, "voice_room");

    val properties: String? = mProperties
}