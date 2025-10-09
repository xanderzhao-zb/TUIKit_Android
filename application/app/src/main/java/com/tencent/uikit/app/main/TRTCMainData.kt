package com.tencent.uikit.app.main

import com.tencent.uikit.app.R
import com.tencent.uikit.app.main.call.GroupCallActivity
import com.tencent.uikit.app.main.live.LiveActivity

class TRTCMainData {
    val itemDataList = ArrayList<MainItemData?>()

    init {
        itemDataList.add(
            MainItemData(
                MainTypeEnum.TYPE_ITEM_CALL, R.drawable.app_ic_main_call, R.string.app_main_item_call,
                R.string.app_main_item_call_sub, GroupCallActivity::class.java, MainItemData.Category.KIT
            )
        )
        itemDataList.add(
            MainItemData(
                MainTypeEnum.TYPE_ITEM_LIVE, R.drawable.app_ic_main_live, R.string.app_main_item_live,
                R.string.app_main_item_live_sub, LiveActivity::class.java, MainItemData.Category.KIT
            )
        )
    }
}