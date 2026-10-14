package io.trtc.tuikit.chat.uikit.components.aiassistant.audio

/**
 * 用 ASR 文本判打断时的过滤：会话常开后，播放期回来的字既可能是用户开口，
 * 也可能是上一句的尾包，或 TTS 漏进麦被识别成的回声。
 */
object AsrBargeIn {

    fun significantCharCount(text: String): Int =
        text.count { it.isLetterOrDigit() || it in '\u4e00'..'\u9fff' }

    fun normalize(text: String): String = buildString(text.length) {
        for (ch in text) {
            if (ch.isWhitespace() || ch in PUNCTUATION) continue
            append(ch)
        }
    }

    /**
     * 上一轮已经发出去的用户话，后台还在往同一个 session 上补尾包。
     * 相等，或识别结果仍是已发送句子的前缀，当成同一句。
     * 若识别在已发送句子后面又接了一截，那截由 [extraBeyondCommitted] 交给调用方另判。
     */
    fun looksLikeCommitted(asrText: String, committed: String): Boolean {
        val asr = normalize(asrText)
        val sent = normalize(committed)
        if (asr.isEmpty() || sent.isEmpty()) {
            return false
        }
        return asr == sent || sent.startsWith(asr)
    }

    fun extraBeyondCommitted(asrText: String, committed: String): String? {
        val asr = normalize(asrText)
        val sent = normalize(committed)
        if (sent.isEmpty() || !asr.startsWith(sent) || asr.length <= sent.length) {
            return null
        }
        return asr.substring(sent.length)
    }

    /**
     * TTS 漏音被识别出来时，结果通常是正在念的 LLM 文本的一段子串。
     */
    fun looksLikeTtsEcho(asrText: String, llmText: String): Boolean {
        val asr = normalize(asrText)
        val llm = normalize(llmText)
        if (asr.length < 2 || llm.isEmpty()) {
            return false
        }
        return llm.contains(asr)
    }

    fun shouldBargeIn(
        asrText: String,
        committed: String,
        llmText: String,
        minChars: Int
    ): Boolean {
        val extra = extraBeyondCommitted(asrText, committed)
        val candidate = extra ?: asrText
        if (extra == null && looksLikeCommitted(asrText, committed)) {
            return false
        }
        if (significantCharCount(candidate) < minChars) {
            return false
        }
        if (looksLikeTtsEcho(candidate, llmText)) {
            return false
        }
        return true
    }

    private const val PUNCTUATION = "，。、；：？！,“”‘’\"'（）()[]【】…—-,:;?!."
}
