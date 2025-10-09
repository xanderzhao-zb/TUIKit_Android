package com.tencent.uikit.app.main.live

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tencent.uikit.app.R
import com.trtc.uikit.livekit.livestream.VideoLiveListActivity
import com.trtc.uikit.livekit.voiceroom.VoiceRoomListActivity

class LiveAdapter(private val context: Context, private val dataList: MutableList<LiveItemData?>) :
    RecyclerView.Adapter<LiveAdapter.ItemViewHolder?>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view: View = LayoutInflater.from(parent.context).inflate(R.layout.app_adapter_live_item, parent, false)
        return ItemViewHolder(view, context)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = dataList[position]
        holder.bind(item)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemCount(): Int {
        return dataList.size
    }


    class ItemViewHolder(itemView: View, private val context: Context) : RecyclerView.ViewHolder(itemView) {
        private val imageIcon: ImageView = itemView.findViewById(R.id.iv_icon)
        private val textTitle: TextView = itemView.findViewById(R.id.tv_title)
        private val textSubTitle: TextView = itemView.findViewById(R.id.tv_subtitle)

        fun bind(mainItemData: LiveItemData?) {
            if (mainItemData == null) {
                return
            }
            itemView.setOnClickListener { v: View? ->
                when (mainItemData.type) {
                    LiveTypeEnum.TYPE_VOICE_ROOM -> startVoiceRoomActivity()
                    LiveTypeEnum.TYPE_VIDEO_LIVE -> startVideoLiveActivity()
                    else -> startVideoLiveActivity()
                }
            }
            textTitle.setText(mainItemData.title)
            imageIcon.setImageResource(mainItemData.resId)
            textSubTitle.setText(mainItemData.subTitle)
        }

        private fun startVideoLiveActivity() {
            val intent = Intent(context, VideoLiveListActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        private fun startVoiceRoomActivity() {
            val intent = Intent(context, VoiceRoomListActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}