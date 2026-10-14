package io.trtc.tuikit.chat.demo.ai

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIAssistantAgent
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 拉取 customer-server 的公开客服目录。
 *
 * `GET /api/support/v1/agents` 无需鉴权。文档要求每次打开客服入口都重新拉，
 * 不长期缓存停用状态；[agents] 只作为当前这次展示和标题回退的快照。
 */
object SupportAgentCatalog {

    private const val TAG = "SupportAgentCatalog"
    private const val PLACEHOLDER_HOST = "your-business-server.example.com"
    private const val AGENTS_URL =
        "https://$PLACEHOLDER_HOST/api/support/v1/agents"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var agents: List<AIAssistantAgent> = emptyList()
        private set

    fun displayNameForConversation(conversationID: String): String? {
        return agents.firstOrNull { it.conversationID == conversationID }?.displayName
    }

    fun fetch(onResult: (List<AIAssistantAgent>) -> Unit) {
        if (AGENTS_URL.contains(PLACEHOLDER_HOST)) {
            Log.w(TAG, "Replace the AGENTS_URL placeholder with your business server domain")
            mainHandler.post { onResult(emptyList()) }
            return
        }
        val request = Request.Builder()
            .url(AGENTS_URL)
            .get()
            .header("Cache-Control", "no-store")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "fetch agents failed", e)
                mainHandler.post { onResult(agents) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    agents = parse(body)
                } else {
                    Log.w(TAG, "fetch agents http ${response.code}")
                }
                mainHandler.post { onResult(agents) }
            }
        })
    }

    private fun parse(body: String): List<AIAssistantAgent> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("agents") ?: return emptyList()
        val result = ArrayList<AIAssistantAgent>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val imUserId = item.optString("im_user_id").trim()
            if (imUserId.isEmpty()) {
                continue
            }
            result += AIAssistantAgent(
                id = item.optString("id").trim(),
                name = item.optString("name").trim(),
                welcome = item.optString("welcome").trim(),
                imUserId = imUserId,
                imNickname = item.optString("im_nickname").trim(),
            )
        }
        return result
    }
}
