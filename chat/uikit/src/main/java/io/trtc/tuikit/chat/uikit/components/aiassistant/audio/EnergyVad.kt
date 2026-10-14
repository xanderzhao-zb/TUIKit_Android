package io.trtc.tuikit.chat.uikit.components.aiassistant.audio

import kotlin.math.min
import kotlin.math.sqrt

/**
 * 20ms 帧粒度的能量 VAD。
 *
 * 先用前 [calibrationFrames] 帧建立噪声基线，之后按「相对基线的倍数」判定，
 * 避免安静房间和嘈杂环境共用同一个绝对阈值。
 *
 * 两个门限同时维护：
 * - 低门限（[silenceThresholdFactor]）用于聆听态判静音断句
 * - 高门限（[speechThresholdFactor]）用于播放态判说话打断，倍数更高是为了
 *   吃掉 AEC 未消干净的残留回声，避免 TTS 播放时自己打断自己
 *
 * 两条累积对「跌破门限」的反应**不对称**：静音累积一帧跌破就清零，说话累积只是进入
 * [speechHangoverMs] 的宽限期。真静音是持续的，所以「连续」这个要求对静音成立；人声的逐帧
 * RMS 天然剧烈起伏（元音响、辅音和字间停顿弱），要求连续越线等于要求打断永不发生，
 * 见 [speechHangoverMs]。
 *
 * 基线取校准窗口内 RMS 的**最小值**而不是平均值：用户在通话页刚打开就说话是常态，
 * 平均值会被语音抬高好几倍，导致此后正常说话反而被判成静音。最小值会落在字间停顿
 * 上，接近真实底噪。[maxNoiseFloor] 是兜底，防止连最小值都被污染。
 *
 * 最小值的反向代价是一帧零值就能把基线咬死：`AudioRecord.startRecording()` 之后的头几帧
 * 是硬件预热期，返回全零 PCM，RMS 恰好是 0。所以校准期会**跳过**数字静音帧（RMS 低于
 * [MIN_CALIBRATION_RMS]）——它们不是对环境噪声的有效测量，既不更新基线也不计入
 * [calibrationFrames]。跳过必须配上界（[calibrationFrames] * [CALIBRATION_ATTEMPT_FACTOR]
 * 帧），否则麦克风坏掉时会永远校准不完，[isCalibrated] 一直为假把状态机卡死。
 *
 * 线程契约：
 * - [accept] 只允许在采集线程上调用（单生产者）。
 * - [reset] / [resetCalibration] / [hasSilenceFor] / [hasSpeechFor] / [isCalibrated]
 *   可以跨线程调用。两个 reset 只是置挂起标志，**在采集线程的下一帧才真正生效**，
 *   以此避免与 [accept] 的读改写竞态（主线程清零被采集线程的旧值覆盖会静默丢失）。
 */
class EnergyVad(
    private val frameDurationMs: Int = 20,
    private val calibrationFrames: Int = 15,
    private val silenceThresholdFactor: Double = 2.0,
    private val speechThresholdFactor: Double = 2.5,
    private val minNoiseFloor: Double = 100.0,
    private val maxNoiseFloor: Double = 2000.0,
    /**
     * 说话累积的宽限期：跌破高门限不立刻清零，连续跌破满这么久才清零。宽限期内累积值
     * **保持**不变而不是继续累加，因此「说话 300ms」的含义是「累计 300ms 真实越线、其间空档
     * 都不超过宽限」，而不是「有几帧越线就算 300ms」——后者一两声咳嗽就能打断 AI。
     *
     * 240ms（12 帧）是对着真机读数定的，不是拍脑袋：华为 NCO-AL00 上用户在 AI 念回答时持续
     * 说话，探针每 500ms 打一次窗口峰值 RMS，得到
     * `1323, 590, 385, 629, 1673, 258, 1254, 1243, 1454, 179, 99, 651, 385, 519, ...`
     * （当时说话门限 800；现网是 minFloor×2.5，安静房间约 250）。
     * - 下限：越线窗口与跌破窗口交替出现，说明一次连读里的空档在百毫秒量级（辅音 + 字间
     *   停顿）。要桥接它们，宽限必须明显大于 100~200ms，否则等于没加。
     * - 上限：`1673, 258, 1254` 中间整整一个 500ms 窗口峰值只有 258，这是「真的停下来了」
     *   的最小可观测尺度，宽限取它的一半以内，真停顿仍然会清零。
     * - 原本还有一条「宽限不该长过它要保护的判定窗口」，那时 `BARGE_IN_HOLD_MS` 是 300ms。
     *   后来判定窗口按实测越线帧密度降到了 160ms，这条不再成立，含义要重新说清楚：宽限
     *   （240ms）长过判定窗口，意味着 8 个零碎越线帧最长可以摊在约 1.9 秒里仍然攒满。
     *   播放打断已改走 ASR 文本，这条说话累积目前只用于观测。
     *
     * 取 0 即退化成「一帧跌破立刻清零」的老行为。
     */
    private val speechHangoverMs: Int = 240
) {

    init {
        require(frameDurationMs > 0) { "frameDurationMs must be positive: $frameDurationMs" }
        require(calibrationFrames > 0) { "calibrationFrames must be positive: $calibrationFrames" }
        require(speechHangoverMs >= 0) { "speechHangoverMs must not be negative: $speechHangoverMs" }
        // 两个倍数反了会让死区消失、两条累积同时增长，打断与断句的互斥防线直接失效
        require(silenceThresholdFactor < speechThresholdFactor) {
            "silenceThresholdFactor($silenceThresholdFactor) must be less than " +
                "speechThresholdFactor($speechThresholdFactor)"
        }
        require(minNoiseFloor <= maxNoiseFloor) {
            "minNoiseFloor($minNoiseFloor) must not exceed maxNoiseFloor($maxNoiseFloor)"
        }
    }

    /** 校准窗口内**有效测量**的 running min，一帧有效测量都没有时为正无穷。 */
    private var noiseFloor: Double = Double.POSITIVE_INFINITY

    /** 计入基线的有效测量帧数，不含被跳过的数字静音帧。 */
    @Volatile
    private var calibratedFrames: Int = 0

    /** 校准期收到的总帧数，含被跳过的，用于给校准封上界。 */
    @Volatile
    private var calibrationAttempts: Int = 0

    @Volatile
    private var silenceAccumMs: Int = 0

    @Volatile
    private var speechAccumMs: Int = 0

    /**
     * 当前连续跌破高门限的时长，即宽限期已经走掉多少。
     *
     * 不变量：只在 [speechAccumMs] 大于 0 时才推进，因此它大于 0 意味着「正处在一段说话的
     * 空档里」。越线帧、宽限到期、[reset] 三处都把它清回 0。
     */
    @Volatile
    private var speechGapMs: Int = 0

    @Volatile
    private var pendingReset: Boolean = false

    @Volatile
    private var pendingRecalibrate: Boolean = false

    /** 攒够有效测量，或尝试帧数触到上界（此时用已有测量收尾，可能一条都没有）。 */
    val isCalibrated: Boolean
        get() = calibratedFrames >= calibrationFrames ||
            calibrationAttempts >= calibrationFrames * CALIBRATION_ATTEMPT_FACTOR

    /**
     * 校准窗口实测到的原始基线，**一帧有效测量都没有时为 [Double.NaN]**。
     *
     * 与 [effectiveNoiseFloor] 一起用于日志观测：两者不相等就说明基线被 [minNoiseFloor] /
     * [maxNoiseFloor] 收敛过。触到上限意味着判定退化成固定的绝对门限，正常说话有可能
     * 低于静音门限——[maxNoiseFloor] 的取值要靠真机日志来定，不要凭感觉调。
     *
     * 无测量时返回 NaN 而不是 0.0：0.0 是一个合法的 RMS 取值，用它表示「没测到」会让
     * 日志分不清「麦克风一直是数字静音」和「基线真的很低」——这个字段纯诊断用，
     * 说谎比没有更糟。
     *
     * 仅供日志使用，[isCalibrated] 为真之后才有意义。跨线程读非 volatile 的基线字段，
     * 拿到的可能是稍旧的值，对日志无影响。
     */
    val rawNoiseFloor: Double
        get() = if (noiseFloor.isFinite()) noiseFloor else Double.NaN

    /**
     * 实际参与判定的基线，即 [rawNoiseFloor] 收敛到 [minNoiseFloor]..[maxNoiseFloor] 之后的值。
     *
     * 一帧有效测量都没有时兜到 [minNoiseFloor]：这种情况下麦克风要么坏了要么被系统静音，
     * 按最安静的环境估门限，至少让断句判定还能跑。
     */
    val effectiveNoiseFloor: Double
        get() = if (noiseFloor.isFinite()) noiseFloor.coerceIn(minNoiseFloor, maxNoiseFloor) else minNoiseFloor

    /** 判静音的低门限，RMS 严格低于它才累积静音。仅供日志与调参观测。 */
    val silenceThreshold: Double
        get() = effectiveNoiseFloor * silenceThresholdFactor

    /** 判说话的高门限，RMS 严格高于它才累积说话。仅供日志与调参观测。 */
    val speechThreshold: Double
        get() = effectiveNoiseFloor * speechThresholdFactor

    /** 清累积时长，保留噪声基线。每轮对话切换时调用，下一帧生效。 */
    fun reset() {
        pendingReset = true
    }

    /** 连噪声基线一起清。环境明显变化时调用，下一帧生效。 */
    fun resetCalibration() {
        pendingRecalibrate = true
    }

    /**
     * 送入一帧 PCM。校准期内只累积基线、不做判定。
     *
     * 只在采集线程上调用。
     *
     * @return 该帧的 RMS，调用方可以拿去驱动音量动画
     */
    fun accept(pcm: ShortArray): Double {
        if (pendingRecalibrate) {
            noiseFloor = Double.POSITIVE_INFINITY
            calibratedFrames = 0
            calibrationAttempts = 0
            pendingRecalibrate = false
            pendingReset = true
        }
        if (pendingReset) {
            silenceAccumMs = 0
            speechAccumMs = 0
            // 宽限计时跟着累积一起清，别让上一轮的空档漏到下一轮
            speechGapMs = 0
            pendingReset = false
        }

        val rms = rms(pcm)
        if (!isCalibrated) {
            calibrationAttempts++
            // 数字静音帧（预热期的全零 PCM）不是对环境噪声的有效测量，跳过，
            // 否则 running min 会被 0 咬死，判定永久退化成固定绝对门限。
            if (rms >= MIN_CALIBRATION_RMS) {
                noiseFloor = min(noiseFloor, rms)
                calibratedFrames++
            }
            return rms
        }
        val floor = effectiveNoiseFloor
        // 两条门限之间是死区（闭区间：rms == floor*2 与 rms == floor*4 都落在死区），
        // 留给 AEC 没消干净的残留回声，两边都不触发。`<` / `>` 不是笔误，别改成 `<=` / `>=`。
        silenceAccumMs = if (rms < floor * silenceThresholdFactor) {
            (silenceAccumMs + frameDurationMs).coerceAtMost(MAX_ACCUM_MS)
        } else {
            0
        }
        if (rms > floor * speechThresholdFactor) {
            speechGapMs = 0
            speechAccumMs = (speechAccumMs + frameDurationMs).coerceAtMost(MAX_ACCUM_MS)
        } else if (speechAccumMs > 0) {
            // 跌破高门限先进宽限期：累积值保持不变（既不加也不清），空档连续满
            // speechHangoverMs 才判定这段说话真的结束。人声逐帧起伏，没有这层宽限
            // 打断需要连续 15 帧都越线，实测永远不成立。
            speechGapMs += frameDurationMs
            if (speechGapMs >= speechHangoverMs) {
                speechAccumMs = 0
                speechGapMs = 0
            }
        }
        return rms
    }

    /** 当前说话累积时长，仅供日志与调参观测。 */
    val speechAccumulationMs: Int
        get() = speechAccumMs

    /** 当前静音累积时长，仅供日志与调参观测。 */
    val silenceAccumulationMs: Int
        get() = silenceAccumMs

    fun hasSilenceFor(holdMs: Long): Boolean = isCalibrated && silenceAccumMs >= holdMs

    fun hasSpeechFor(holdMs: Long): Boolean = isCalibrated && speechAccumMs >= holdMs

    private fun rms(pcm: ShortArray): Double {
        if (pcm.isEmpty()) {
            return 0.0
        }
        var sum = 0.0
        for (sample in pcm) {
            val v = sample.toDouble()
            sum += v * v
        }
        return sqrt(sum / pcm.size)
    }

    private companion object {
        /** 累积量封顶，避免长时间运行后 Int 溢出成负数导致判定永久失效。 */
        const val MAX_ACCUM_MS = 60_000

        /** 低于此 RMS 视为数字静音（硬件预热帧），不计入校准。真实环境底噪远高于 1。 */
        const val MIN_CALIBRATION_RMS = 1.0

        /** 校准最多尝试 calibrationFrames 的这个倍数，默认 15*4=60 帧即 1.2 秒。 */
        const val CALIBRATION_ATTEMPT_FACTOR = 4
    }
}
