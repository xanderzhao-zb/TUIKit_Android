package io.trtc.tuikit.chat.uikit.components.aiassistant.call.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.uikit.components.aiassistant.call.AICallState
import kotlin.math.min

/**
 * 通话页中央的光球。颜色取自 App 主题主色，深浅用相邻的 hover / active 两档，
 * 换主题不用再改这里。
 *
 * LISTENING：主题色发光球，呼吸式缩放
 * SPEAKING ：同色软边球，半径跟平滑后的播放音量脉动，字缝里带一层浅呼吸
 * THINKING ：慢速旋转的扫描渐变
 * IDLE     ：聆听态的静帧——颜色和光晕都一样，只是半径钉死、不呼吸。
 *            进页那一下如果画成暗色实心圆，会像一颗还没点亮的空球。
 */
class VoiceOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 主线程写，采集线程和绘制线程读，故 volatile */
    @Volatile
    private var state = AICallState.IDLE

    /** 呼吸/旋转动画的进度，0f..1f。只在主线程读写。 */
    private var phase = 0f

    /**
     * 播放音量目标，0f..1f。采集线程写、主线程在 vsync 上读，故 volatile。
     *
     * TTS 分片往往是 20~60ms 一包，RMS 会台阶式跳变。真正画到屏幕上的是 [displayedLevel]，
     * 按快起慢收跟过去，避免光球一顿一顿。
     */
    @Volatile
    private var targetLevel = 0f

    /** 已经画在屏幕上的音量。只在主线程、动画回调里更新。 */
    private var displayedLevel = 0f

    private var animator: ValueAnimator? = null

    /** 主题色由浅到深：hover / default / active，进页时读一次即可。 */
    private var colorLight = 0
    private var colorCore = 0
    private var colorDark = 0
    private var colorFade = 0

    init {
        applyThemeColors()
        applyLayerType(AICallState.IDLE)
    }

    private fun applyThemeColors() {
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        colorLight = colors.buttonColorPrimaryHover
        colorCore = colors.buttonColorPrimaryDefault
        colorDark = colors.buttonColorPrimaryActive
        colorFade = colorDark and 0x00FFFFFF
    }

    fun setState(newState: AICallState) {
        if (state == newState) {
            return
        }
        state = newState
        if (newState != AICallState.SPEAKING) {
            targetLevel = 0f
            displayedLevel = 0f
        }
        applyLayerType(newState)
        restartAnimator()
        invalidate()
    }

    /**
     * 音量来自采集线程。这里只记下目标值，不 invalidate：
     * 播放态每 20ms 脏一次，再叠软件层栅格化，帧还没画完就又脏了，看起来就是卡。
     * SPEAKING 的绘制由下面的 ValueAnimator 跟 vsync 走。
     */
    fun setLevel(newLevel: Float) {
        targetLevel = newLevel.coerceIn(0f, 1f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartAnimator()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }

    private fun restartAnimator() {
        animator?.cancel()
        if (!isAttachedToWindow || state == AICallState.IDLE) {
            animator = null
            return
        }
        val duration = when (state) {
            AICallState.LISTENING -> 2600L
            AICallState.THINKING -> 1400L
            else -> 2000L
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                if (state == AICallState.SPEAKING) {
                    tickDisplayedLevel()
                }
                invalidate()
            }
            start()
        }
    }

    /**
     * 快起慢收：音量上来用约 40ms 跟上口型，落下用约 150ms 收，词与词之间不会抖成锯齿。
     * 系数是相对 60fps 一帧的近似，掉帧时只是包络稍慢，不会过冲。
     */
    private fun tickDisplayedLevel() {
        val target = targetLevel
        val alpha = if (target > displayedLevel) LEVEL_ATTACK else LEVEL_RELEASE
        displayedLevel += (target - displayedLevel) * alpha
        if (kotlin.math.abs(displayedLevel - target) < 0.002f) {
            displayedLevel = target
        }
    }

    /**
     * BlurMaskFilter 只能走软件层；播放态每帧重画，软件层会把光球画成一顿一顿。
     * SPEAKING 不用模糊，走 GPU 普通绘制。IDLE 要跟聆听态同色同光晕，所以也走软件层。
     */
    private fun applyLayerType(forState: AICallState) {
        val type = if (forState == AICallState.SPEAKING) LAYER_TYPE_NONE else LAYER_TYPE_SOFTWARE
        if (layerType != type) {
            setLayerType(type, null)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = min(width, height) / 2f
        if (maxRadius <= 0f) {
            return
        }
        // 发光会向外弥散，球体半径必须留出这圈余量，否则光晕会被 View 边界切成方形
        val baseRadius = maxRadius * (1f - GLOW_RATIO)

        when (state) {
            AICallState.LISTENING -> drawListening(canvas, cx, cy, maxRadius, baseRadius)
            AICallState.SPEAKING -> drawSpeaking(canvas, cx, cy, baseRadius)
            AICallState.THINKING -> drawThinking(canvas, cx, cy, maxRadius, baseRadius)
            AICallState.IDLE -> drawIdle(canvas, cx, cy, maxRadius, baseRadius)
        }
    }

    /** 呼吸：半径在休息点附近来回。用 BlurMaskFilter 做出边缘发光的弥散感。 */
    private fun drawListening(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        maxRadius: Float,
        baseRadius: Float
    ) {
        val breath = REST_SCALE + 0.07f * kotlin.math.sin(phase * 2 * Math.PI).toFloat()
        drawGlowingOrb(canvas, cx, cy, maxRadius, baseRadius * breath)
    }

    /**
     * 播放：半径跟平滑后的音量走，并叠一层很浅的呼吸，避免字缝里完全停住。
     * 外圈用透明色收边，比 BlurMaskFilter 便宜，缩放时也不会一圈一圈跳。
     */
    private fun drawSpeaking(canvas: Canvas, cx: Float, cy: Float, baseRadius: Float) {
        val breath = kotlin.math.sin(phase * 2 * Math.PI).toFloat()
        val radius = baseRadius * (0.84f + 0.14f * displayedLevel + 0.025f * breath)
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(colorLight, colorCore, colorDark, colorFade),
            floatArrayOf(0f, 0.48f, 0.78f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, paint)
    }

    /** 思考：扫描渐变绕圆心慢转。 */
    private fun drawThinking(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        maxRadius: Float,
        baseRadius: Float
    ) {
        val radius = baseRadius * 0.84f
        paint.reset()
        paint.isAntiAlias = true
        paint.maskFilter = BlurMaskFilter(maxRadius * 0.12f, BlurMaskFilter.Blur.NORMAL)
        paint.shader = SweepGradient(
            cx, cy,
            intArrayOf(colorCore, colorDark, colorLight, colorCore),
            floatArrayOf(0f, 0.34f, 0.67f, 1f)
        )
        canvas.save()
        canvas.rotate(phase * 360f, cx, cy)
        canvas.drawCircle(cx, cy, radius, paint)
        canvas.restore()
    }

    private fun drawIdle(canvas: Canvas, cx: Float, cy: Float, maxRadius: Float, baseRadius: Float) {
        drawGlowingOrb(canvas, cx, cy, maxRadius, baseRadius * REST_SCALE)
    }

    private fun drawGlowingOrb(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        maxRadius: Float,
        radius: Float
    ) {
        paint.reset()
        paint.isAntiAlias = true
        paint.maskFilter = BlurMaskFilter(maxRadius * GLOW_RATIO, BlurMaskFilter.Blur.NORMAL)
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(colorLight, colorCore, colorDark),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, paint)
    }

    companion object {
        /** 光晕向外弥散占用的半径比例 */
        private const val GLOW_RATIO = 0.22f
        /** 聆听呼吸的中点，IDLE 钉在这里，进页时和开始听是同一颗球 */
        private const val REST_SCALE = 0.85f
        /** 约 40ms 跟上突然变大的音量 */
        private const val LEVEL_ATTACK = 0.38f
        /** 约 150ms 收回，词缝里不会抖 */
        private const val LEVEL_RELEASE = 0.10f
    }
}
