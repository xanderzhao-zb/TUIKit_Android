package io.trtc.tuikit.chat.uikit.components.messageinput.asr

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 实时识别专用的采集通路：麦克风 PCM16 / 16kHz / 单声道，按 20ms 切帧后用系统
 * [SystemOpusEncoder] 编成裸 Opus packet，直接喂给 sendASRAudioData。
 *
 * 输入栏与 Demo 通话页共用这一路。默认 [Config.Recognition]（`VOICE_RECOGNITION` → `MIC`，
 * 不挂通话 3A）；通话页用 [Config.VoiceCommunication]。
 *
 * 这条通路与「录语音文件」的 [io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorder]
 * 是**并行**跑的两路采集：前者要的是可上行的实时流，后者要的是可发送的 m4a 文件，
 * 两者的编码格式与生命周期都不一样，没法共用一路。同一个应用内并发采集在 Android 10
 * 及以上可用，但并非所有机型都给；拿不到音频时 [start] 返回 false，调用方回落到
 * 「录完整段再上传识别」的老路径，不影响语音消息本身。
 *
 * 不用第三方 Opus 库，避免给 UIKit 的接入方凭空加一个依赖；代价是
 * `audio/opus` 编码器要 Android 10 起且机型注册了才有，[isSupported] 负责探测。
 *
 * [onPacket] / [onPcmFrame] 都在采集线程上回调。
 */
class RealtimeAsrAudioCapture(
    private val onPacket: (ByteArray) -> Unit,
    private val onPcmFrame: ((ShortArray) -> Unit)? = null,
    private val config: Config = Config.Recognition,
) {

    class Config(
        val audioSources: IntArray,
        val attachVoiceEffects: Boolean = false,
        val minBufferFrames: Int = 8,
        val threadName: String = "message-input-asr-capture",
    ) {
        companion object {
            /** 输入栏：识别源，不挂通话音效。 */
            val Recognition = Config(
                audioSources = intArrayOf(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.MIC,
                ),
            )

            /** 通话：`VOICE_COMMUNICATION` + 系统 AEC / NS / AGC。 */
            val VoiceCommunication = Config(
                audioSources = intArrayOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION),
                attachVoiceEffects = true,
                minBufferFrames = 10,
                threadName = "ai-audio-capture",
            )
        }
    }

    private val running = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var encoder: SystemOpusEncoder? = null
    private var workerThread: Thread? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null
    private var activeSource: Int? = null

    @Volatile
    private var pendingEncoderReset = false

    val isRunning: Boolean
        get() = running.get()

    fun effectSummary(): String {
        val source = when (activeSource) {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
            MediaRecorder.AudioSource.MIC -> "MIC"
            else -> "-"
        }
        return "aec=${if (config.attachVoiceEffects) "ON" else "off"} source=$source " +
            "aecAvailable=${AcousticEchoCanceler.isAvailable()} " +
            "aecCreated=${echoCanceler != null} " +
            "aecFx=${echoCanceler?.enabled == true} " +
            "ns=${noiseSuppressor?.enabled == true} " +
            "agc=${gainControl?.enabled == true}"
    }

    /**
     * 调用前必须已获得 RECORD_AUDIO 权限。
     *
     * @return 启动失败返回 false，调用方据此关掉实时识别，不要重试
     */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running.get()) {
            return true
        }
        if (!isSupported()) {
            return false
        }
        val minBufferSize = AudioRecord.getMinBufferSize(
            RealtimeAsrSpec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) {
            Log.w(TAG, "getMinBufferSize failed: $minBufferSize")
            return false
        }
        val bufferSize = maxOf(minBufferSize, RealtimeAsrSpec.FRAME_BYTES * config.minBufferFrames)
        if (!openRecorder(bufferSize) || !openEncoder()) {
            releaseAll()
            return false
        }
        try {
            audioRecord?.startRecording()
        } catch (exception: Exception) {
            Log.w(TAG, "startRecording failed", exception)
            releaseAll()
            return false
        }
        running.set(true)
        workerThread = Thread(::captureLoop, config.threadName).also {
            it.isDaemon = true
            it.start()
        }
        Log.i(TAG, "capture started: ${effectSummary()}")
        return true
    }

    /** 停止采集并释放资源。可重复调用。 */
    fun stop() {
        if (!running.compareAndSet(true, false) && audioRecord == null && encoder == null) {
            return
        }
        running.set(false)
        // 不要在采集线程还在 read()/encode 时从这边 stop() AudioRecord：
        // 输入栏录音和实时识别两路并行，主线程抢停会把麦克风弄僵，下一次长按就没声。
        // join 之后再在 releaseAll 里 stop/release。
        val worker = workerThread
        workerThread = null
        if (worker != null && worker != Thread.currentThread()) {
            runCatching { worker.join(JOIN_TIMEOUT_MS) }
            if (worker.isAlive) {
                Log.w(TAG, "capture thread still alive after ${JOIN_TIMEOUT_MS}ms, releasing anyway")
            }
        }
        releaseAll()
    }

    /**
     * 主线程请求丢掉 Opus 帧间状态；下一帧在采集线程上生效。
     * 麦克风常开、识别会话按轮切换时，每轮开始前调一次。
     */
    fun requestEncoderReset() {
        pendingEncoderReset = true
    }

    @SuppressLint("MissingPermission")
    private fun openRecorder(bufferSize: Int): Boolean {
        for (source in config.audioSources) {
            val record = try {
                AudioRecord(
                    source,
                    RealtimeAsrSpec.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
            } catch (exception: Exception) {
                Log.w(TAG, "create AudioRecord failed, source=$source", exception)
                null
            }
            if (record == null) {
                continue
            }
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                if (config.attachVoiceEffects) {
                    attachEffects(record.audioSessionId)
                }
                audioRecord = record
                activeSource = source
                return true
            }
            record.release()
        }
        Log.w(TAG, "no usable audio source for realtime asr")
        return false
    }

    private fun openEncoder(): Boolean {
        val opusEncoder = SystemOpusEncoder.create()
        if (opusEncoder == null) {
            return false
        }
        encoder = opusEncoder
        return true
    }

    private fun attachEffects(sessionId: Int) {
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = runCatching {
                AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }.onFailure { Log.w(TAG, "AEC create failed", it) }.getOrNull()
            if (echoCanceler == null) {
                Log.w(TAG, "AcousticEchoCanceler.create returned null, session=$sessionId")
            }
        } else {
            Log.w(TAG, "AcousticEchoCanceler unavailable on this device")
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = runCatching {
                NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }.onFailure { Log.w(TAG, "NS create failed", it) }.getOrNull()
        }
        if (AutomaticGainControl.isAvailable()) {
            gainControl = runCatching {
                AutomaticGainControl.create(sessionId)?.apply { enabled = true }
            }.onFailure { Log.w(TAG, "AGC create failed", it) }.getOrNull()
        }
    }

    private fun captureLoop() {
        val record = audioRecord ?: return
        val readBuffer = ShortArray(RealtimeAsrSpec.FRAME_SAMPLES * READ_BUFFER_FRAMES)
        var pending = ShortArray(RealtimeAsrSpec.FRAME_SAMPLES * (READ_BUFFER_FRAMES + 1))
        var pendingLength = 0
        try {
            while (running.get()) {
                val read = record.read(readBuffer, 0, readBuffer.size)
                if (read <= 0) {
                    continue
                }
                if (pendingLength + read > pending.size) {
                    pending = pending.copyOf((pendingLength + read) * 2)
                }
                System.arraycopy(readBuffer, 0, pending, pendingLength, read)
                pendingLength += read

                var offset = 0
                while (pendingLength - offset >= RealtimeAsrSpec.FRAME_SAMPLES) {
                    if (pendingEncoderReset) {
                        pendingEncoderReset = false
                        encoder?.reset()
                    }
                    onPcmFrame?.invoke(
                        pending.copyOfRange(offset, offset + RealtimeAsrSpec.FRAME_SAMPLES),
                    )
                    encoder?.encode(pending, offset, onPacket)
                    offset += RealtimeAsrSpec.FRAME_SAMPLES
                }
                if (offset > 0) {
                    val remain = pendingLength - offset
                    if (remain > 0) {
                        System.arraycopy(pending, offset, pending, 0, remain)
                    }
                    pendingLength = remain
                }
            }
        } catch (exception: Exception) {
            running.set(false)
            Log.w(TAG, "capture loop crashed", exception)
        }
    }

    private fun releaseAll() {
        runCatching { echoCanceler?.release() }
        runCatching { noiseSuppressor?.release() }
        runCatching { gainControl?.release() }
        echoCanceler = null
        noiseSuppressor = null
        gainControl = null
        activeSource = null
        pendingEncoderReset = false
        audioRecord?.let { record ->
            runCatching {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            }
            runCatching { record.release() }
        }
        audioRecord = null
        encoder?.release()
        encoder = null
    }

    companion object {
        private const val TAG = "RealtimeAsrCapture"
        private const val READ_BUFFER_FRAMES = 4
        private const val JOIN_TIMEOUT_MS = 300L

        /** 系统是否注册了 `audio/opus` 编码器。探测见 [SystemOpusEncoder.isSupported]。 */
        fun isSupported(): Boolean = SystemOpusEncoder.isSupported()
    }
}
