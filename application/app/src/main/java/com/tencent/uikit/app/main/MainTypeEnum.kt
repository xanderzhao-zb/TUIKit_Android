package com.tencent.uikit.app.main

enum class MainTypeEnum(val type: Int, val properties: String) {
    TYPE_ITEM_MEETING(100, "conference"),
    TYPE_ITEM_CALL(104, "call"),
    TYPE_ITEM_LIVE(106, "live"),
    TYPE_ITEM_VOICE(107, "voice"),
    TYPE_ITEM_CHAT(109, "chat")
}