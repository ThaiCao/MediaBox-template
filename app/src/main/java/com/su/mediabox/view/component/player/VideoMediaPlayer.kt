package com.su.mediabox.view.component.player

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.*
import android.view.View.OnClickListener
import android.widget.*
import androidx.core.content.ContextCompat.startActivity
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shuyu.gsyvideoplayer.utils.CommonUtil
import com.shuyu.gsyvideoplayer.utils.Debuger
import com.shuyu.gsyvideoplayer.utils.GSYVideoType
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import com.shuyu.gsyvideoplayer.video.base.GSYBaseVideoPlayer
import com.shuyu.gsyvideoplayer.video.base.GSYVideoPlayer
import com.shuyu.gsyvideoplayer.video.base.GSYVideoView
import com.su.mediabox.App
import com.su.mediabox.R
import com.su.mediabox.config.Const
import com.su.mediabox.pluginapi.been.BaseBean
import com.su.mediabox.util.*
import com.su.mediabox.pluginapi.UI.dp
import com.su.mediabox.util.Util.getResColor
import com.su.mediabox.util.Util.getResDrawable
import com.su.mediabox.util.Util.getScreenBrightness
import com.su.mediabox.util.Util.openVideoByExternalPlayer
import com.su.mediabox.view.activity.DlnaActivity
import com.su.mediabox.view.adapter.SkinRvAdapter
import com.su.mediabox.view.component.ZoomView
import com.su.mediabox.view.component.textview.TypefaceTextView
import com.su.mediabox.view.listener.dsl.setOnSeekBarChangeListener
import com.su.skin.SkinManager
import kotlinx.coroutines.*
import java.io.File
import java.io.Serializable
import kotlin.math.abs

//TODO 太乱了，需要后续整理重写
open class VideoMediaPlayer : StandardGSYVideoPlayer {
    companion object {
        val mScaleStrings = listOf(
            "默认比例" to GSYVideoType.SCREEN_TYPE_DEFAULT,
            "16:9" to GSYVideoType.SCREEN_TYPE_16_9,
            "4:3" to GSYVideoType.SCREEN_TYPE_4_3,
            "全屏" to GSYVideoType.SCREEN_TYPE_FULL,
            "拉伸全屏" to GSYVideoType.SCREEN_MATCH_FULL
        )

        const val NO_REVERSE = 0
        const val HORIZONTAL_REVERSE = 1
        const val VERTICAL_REVERSE = 2

        // 夜间屏幕最大Alpha
        const val NIGHT_SCREEN_MAX_ALPHA: Int = 0xAA

        val playPositionMemoryStoreCoroutineScope by lazy(LazyThreadSafetyMode.NONE) {
            CoroutineScope(Dispatchers.Default)
        }

    }

    /**
     * 进度记忆最小时间，默认5秒后的进度才记忆
     */
    var playPositionMemoryTimeLimit = 5000L

    var playPositionMemoryStore: AnimeVideoPlayer.PlayPositionMemoryDataStore? = null
    private var playPositionViewJob: Job? = null

    // 预跳转进度
    private var preSeekPlayPosition: Long? = null

    // 正在双指缩放移动
    private var doublePointerZoomingMoving = false

    var ivDownloadButton: ImageView? = null
        private set

    private var initFirstLoad = true

    //记住切换数据源类型
    private var mScaleIndex = 0

    //4:3  16:9等
    private var tvMoreScale: TextView? = null

    //倍速按钮
    private var tvSpeed: TextView? = null
    private var rvSpeed: RecyclerView? = null

    //速度
    private var mPlaySpeed = 1f

    //投屏按钮
    var ivCling: ImageView? = null
        private set

    //分享按钮
    var ivShare: ImageView? = null
        private set

    //更多按钮
    var ivMore: ImageView? = null
        private set

    val mBottomContainer: ViewGroup? = mBottomContainer

    //下一集按钮
    var ivNextEpisode: ImageView? = null
        private set

    // 进度记忆组
    private var vgPlayPosition: ViewGroup? = null

    // 进度文字
    private var tvPlayPosition: TextView? = null

    // 关闭进度提示ImageView
    private var ivClosePlayPositionTip: ImageView? = null

    // 设置
    protected var vgSettingContainer: ViewGroup? = null
    private var ivSetting: ImageView? = null

    // 镜像RadioGroup
    private var rgReverse: RadioGroup? = null
    private var mReverseValue: Int? = null
    private var mTextureViewTransform: Int =
        NO_REVERSE

    // 底部进度条CheckBox
    private var cbBottomProgress: CheckBox? = null
    private var playBottomProgress: ProgressBar? = null

    //控制进度条
    private var pbBottomProgress: ProgressBar? = null

    // 外部播放器打开
    private var tvOpenByExternalPlayer: TextView? = null

    // 硬解码CheckBox
    private var cbMediaCodec: CheckBox? = null

    // 右侧弹出栏
    var vgRightContainer: ViewGroup? = null
        private set

    // 按住高速播放的tv
    private var tvTouchDownHighSpeed: TextView? = null
    private var mLongPressing: Boolean = false

    // 还原屏幕
    private var tvRestoreScreen: TextView? = null

    // 投屏
    private var tvDlna: TextView? = null

    // 屏幕已经双指放大移动了
    private var mDoublePointerZoomMoved: Boolean = false

    // 屏幕已经双指放大移动了
    private var vgBiggerSurface: ViewGroup? = null

    // 控件没有显示
    private var mUiCleared: Boolean = true

    // 显示系统时间
    private var tcSystemTime: TextClock? = null

    // top阴影
    private var viewTopContainerShadow: View? = null

    // 夜间屏幕View
    private var viewNightScreen: View? = null

    // 夜间屏幕seekbar
    private var sbNightScreen: SeekBar? = null

    // 夜间屏幕SeekBar值
    private var mNightScreenSeekBarProgress: Int = 0

    // 全屏手动滑动下拉状态栏的起始偏移位置
    protected open var mStatusBarOffset: Int = 50.dp

    constructor(context: Context) : super(context)

    constructor(context: Context, fullFlag: Boolean?) : super(context, fullFlag)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun getLayoutId() = R.layout.layout_video_media_play

    @SuppressLint("ClickableViewAccessibility")
    override fun init(context: Context?) {
        super.init(context)

        ivDownloadButton = findViewById(R.id.iv_play_activity_toolbar_download)
        tvMoreScale = findViewById(R.id.tv_more_scale)
        tvSpeed = findViewById(R.id.tv_speed)
        vgRightContainer = findViewById(R.id.layout_right)
        rvSpeed = findViewById(R.id.rv_right)
        ivShare = findViewById(R.id.iv_play_activity_toolbar_share)
        ivNextEpisode = findViewById(R.id.iv_next)
        ivSetting = findViewById(R.id.iv_setting)
        vgSettingContainer = findViewById(R.id.layout_setting)
        rgReverse = findViewById(R.id.rg_reverse)
        cbBottomProgress = findViewById(R.id.cb_bottom_progress)
        pbBottomProgress = findViewById(R.id.progress)
        playBottomProgress = findViewById(R.id.play_bottom_progressbar)
        ivMore = findViewById(R.id.iv_play_activity_toolbar_more)
        tvOpenByExternalPlayer = findViewById(R.id.tv_open_by_external_player)
        tvRestoreScreen = findViewById(R.id.tv_restore_screen)
        tvTouchDownHighSpeed = findViewById(R.id.tv_touch_down_high_speed)
        vgBiggerSurface = findViewById(R.id.bigger_surface)
        tcSystemTime = findViewById(R.id.tc_system_time)
        viewTopContainerShadow = findViewById(R.id.view_top_container_shadow)
        viewNightScreen = findViewById(R.id.view_player_night_screen)
        sbNightScreen = findViewById(R.id.sb_player_night_screen)
        tvDlna = findViewById(R.id.tv_dlna)
        vgPlayPosition = findViewById(R.id.ll_play_position_view)
        tvPlayPosition = findViewById(R.id.tv_play_position_time)
        ivClosePlayPositionTip = findViewById(R.id.iv_close_play_position_tip)

        vgRightContainer?.gone()
        vgSettingContainer?.gone()
        tvTouchDownHighSpeed?.gone()
        vgPlayPosition?.gone()

        vgBiggerSurface?.setOnClickListener(this)
        vgBiggerSurface?.setOnTouchListener(this)

        ivClosePlayPositionTip?.setOnClickListener {
            playPositionViewJob?.cancel()
            vgPlayPosition?.gone(true, 200L)
        }
        vgPlayPosition?.setOnClickListener {
            preSeekPlayPosition?.also { if (it > 0L) seekTo(it) }
            vgPlayPosition?.gone(true, 200L)
        }

        tvRestoreScreen?.setOnClickListener {
            mTextureViewContainer?.run {
                if (this is ZoomView) restore()
                else {
                    translationX = 0f
                    translationY = 0f
                    scaleX = 1f
                    scaleY = 1f
                    rotation = 0f
                }
                mDoublePointerZoomMoved = false
                it.gone()
            }
        }
        tvSpeed?.setOnClickListener {
            //TODO 需要改进
            vgRightContainer?.let {
                val adapter = SpeedAdapter(
                    listOf(
                        SpeedBean("speed", "", "0.5"),
                        SpeedBean("speed", "", "0.75"),
                        SpeedBean("speed", "", "1"),
                        SpeedBean("speed", "", "1.25"),
                        SpeedBean("speed", "", "1.5"),
                        SpeedBean("speed", "", "2")
                    )
                )
                rvSpeed?.layoutManager = LinearLayoutManager(context)
                rvSpeed?.adapter = adapter
            }
            showRightContainer()
        }
        ivSetting?.setOnClickListener { showSettingContainer() }
        mReverseValue = rgReverse?.getChildAt(0)?.id
        rgReverse?.children?.forEach {
            (it as RadioButton).apply {
                setOnCheckedChangeListener { buttonView, isChecked ->
                    if (!isChecked) return@setOnCheckedChangeListener
                    mReverseValue = id
                    when (id) {
                        R.id.rb_no_reverse -> resolveTransform(NO_REVERSE)
                        R.id.rb_horizontal_reverse -> resolveTransform(HORIZONTAL_REVERSE)
                        R.id.rb_vertical_reverse -> resolveTransform(VERTICAL_REVERSE)
                    }
                }
            }
        }

        //全屏时的底部进度条
        context?.sharedPreferences()?.getBoolean(Const.Setting.SHOW_PLAY_BOTTOM_BAR, true)?.also {
            cbBottomProgress?.isChecked = it
            updateBottomProgressBar(it)
        }
        cbBottomProgress?.setOnCheckedChangeListener { buttonView, isChecked ->
            updateBottomProgressBar(isChecked)
        }

        //重置视频比例
        GSYVideoType.setShowType(mScaleStrings[mScaleIndex].second)
        changeTextureViewShowType()
        if (mTextureView != null) mTextureView.requestLayout()

        tvMoreScale?.text = mScaleStrings[mScaleIndex].first

        //切换视频比例
        tvMoreScale?.setOnClickListener(OnClickListener {
            startDismissControlViewTimer()      //重新开始ui消失时间计时
            if (!mHadPlay) {
                return@OnClickListener
            }
            mScaleIndex = (mScaleIndex + 1) % mScaleStrings.size
            resolveTypeUI()
        })

        ivCling?.setOnClickListener {
            mContext.startActivity(
                Intent(mContext, DlnaActivity::class.java)
                    .putExtra("url", mUrl)
                    .putExtra("title", mTitle)
            )
            mOriginUrl
        }

        tvOpenByExternalPlayer?.setOnClickListener {
            if (!openVideoByExternalPlayer(mContext, mUrl))
                mContext.getString(R.string.matched_app_not_found).showToast()
        }

        sbNightScreen?.setOnSeekBarChangeListener {
            onProgressChanged { seekBar, progress, _ ->
                seekBar ?: return@onProgressChanged
                mNightScreenSeekBarProgress = progress
                viewNightScreen?.setBackgroundColor((NIGHT_SCREEN_MAX_ALPHA * progress / seekBar.max) shl 24)
            }
        }

        tvDlna?.setOnClickListener {
            val url = getUrl()
            if (url == null) {
                mContext.getString(R.string.please_wait_video_loaded).showToast()
                return@setOnClickListener
            }
            startActivity(
                mContext, Intent(mContext, DlnaActivity::class.java)
                    .putExtra("url", url)
                    .putExtra("title", getTitle()), null
            )
        }
    }

    fun getUrl(): String? = mUrl

    fun getTitle(): String = mTitle

    private fun showSettingContainer() {
        vgSettingContainer?.let {
            hideAllWidget()
            it.translationX = 150f.dp
            it.visible()
            val animator = ObjectAnimator.ofFloat(
                it, "translationX", 170f.dp, 0f
            )
            animator.duration = 300
            animator.start()
            //取消xx秒后隐藏控制界面
            cancelDismissControlViewTimer()
            if (mReverseValue == null) mReverseValue = rgReverse?.getChildAt(0)?.id
            mReverseValue?.let { id -> findViewById<RadioButton>(id).isChecked = true }

//            mMediaCodecCheckBox?.isChecked = GSYVideoType.isMediaCodec()
//            mMediaCodecCheckBox?.setOnCheckedChangeListener { buttonView, isChecked ->
//                if (isChecked) GSYVideoType.enableMediaCodec()
//                else GSYVideoType.disableMediaCodec()
//                startPlayLogic()
//            }
        }
    }

    fun setTopContainer(top: ViewGroup?) {
        mTopContainer = top
        viewTopContainerShadow = if (top == null) {
            viewTopContainerShadow?.visible()
            null
        } else {
            findViewById(R.id.view_top_container_shadow)
        }
        restartTimerTask()
    }

    private fun showRightContainer() {
        vgRightContainer?.let {
            hideAllWidget()
            it.translationX = 150f.dp
            it.visible()
            val animator = ObjectAnimator.ofFloat(it, "translationX", 170f.dp, 0f)
            animator.duration = 300
            animator.start()
            //取消xx秒后隐藏控制界面
            cancelDismissControlViewTimer()
        }
    }

    override fun hideAllWidget() {
        super.hideAllWidget()
//        setViewShowState(vgRightContainer, INVISIBLE)
//        setViewShowState(vgSettingContainer, INVISIBLE)
        setViewShowState(tvRestoreScreen, View.GONE)
        setViewShowState(viewTopContainerShadow, View.INVISIBLE)
    }

    override fun onClickUiToggle(e: MotionEvent?) {
        vgRightContainer?.let {
            //如果右侧栏显示，则隐藏
            if (it.visibility == View.VISIBLE) {
                it.gone()
                //因为右侧界面显示时，不在xx秒后隐藏界面，所以要恢复xx秒后隐藏控制界面
                startDismissControlViewTimer()
                return
            }
        }
        vgSettingContainer?.let {
            // 如果显示，则隐藏
            if (it.visibility == View.VISIBLE) {
                it.gone()
                // 因为设置界面显示时，不在xx秒后隐藏界面，所以要恢复xx秒后隐藏控制界面
                startDismissControlViewTimer()
                return
            }
        }
        super.onClickUiToggle(e)
        setRestoreScreenTextViewVisibility()
    }

    /**
     * 全屏时将对应处理参数逻辑赋给全屏播放器
     *
     * @param context
     * @param actionBar
     * @param statusBar
     * @return
     */
    override fun startWindowFullscreen(
        context: Context?,
        actionBar: Boolean,
        statusBar: Boolean
    ): GSYBaseVideoPlayer {
        val player = super.startWindowFullscreen(
            context,
            actionBar,
            statusBar
        ) as VideoMediaPlayer
        player.mScaleIndex = mScaleIndex
        player.tvSpeed?.text = tvSpeed?.text
        player.mFullscreenButton.visibility = mFullscreenButton.visibility
        player.mTextureViewTransform = mTextureViewTransform
        player.mReverseValue = mReverseValue
        player.mPlaySpeed = mPlaySpeed
        player.sbNightScreen?.progress = mNightScreenSeekBarProgress
        touchSurfaceUp()
        player.setRestoreScreenTextViewVisibility()
        player.resolveTypeUI()
        return player
    }

    private fun setRestoreScreenTextViewVisibility() {
        if (mUiCleared) {
            tvRestoreScreen?.gone()
        } else {
            if (mDoublePointerZoomMoved) tvRestoreScreen?.visible()
            else tvRestoreScreen?.gone()
        }
    }

    /**
     * 退出全屏时将对应处理参数逻辑返回给非播放器
     *
     * @param oldF
     * @param vp
     * @param gsyVideoPlayer
     */
    override fun resolveNormalVideoShow(
        oldF: View?,
        vp: ViewGroup?,
        gsyVideoPlayer: GSYVideoPlayer?
    ) {
        super.resolveNormalVideoShow(oldF, vp, gsyVideoPlayer)
        if (gsyVideoPlayer != null) {
            val player = gsyVideoPlayer as VideoMediaPlayer
            mScaleIndex = player.mScaleIndex
            mFullscreenButton.visibility = player.mFullscreenButton.visibility
            tvSpeed?.text = player.tvSpeed?.text
            mTextureViewTransform = player.mTextureViewTransform
            mReverseValue = player.mReverseValue
            mPlaySpeed = player.mPlaySpeed
            mNightScreenSeekBarProgress = player.sbNightScreen?.progress ?: 0
            player.touchSurfaceUp()
            setRestoreScreenTextViewVisibility()
            resolveTypeUI()
        }
    }

    fun setShowType(index: Int) {
        if (!mHadPlay || index !in mScaleStrings.indices) {
            return
        }
        mScaleIndex = index
        resolveTypeUI()
    }

    /**
     * 全屏/退出全屏，显示比例
     * 注意，GSYVideoType.setShowType是全局静态生效，除非重启APP。
     */
    @SuppressLint("SetTextI18n")
    private fun resolveTypeUI() {
        if (!mHadPlay) {
            return
        }
        tvMoreScale?.text = mScaleStrings[mScaleIndex].first
        GSYVideoType.setShowType(mScaleStrings[mScaleIndex].second)
        changeTextureViewShowType()
        if (mTextureView != null) mTextureView.requestLayout()
        setSpeed(mPlaySpeed, true)
        tvTouchDownHighSpeed?.gone()
        mLongPressing = false
    }

    override fun setSpeed(speed: Float, soundTouch: Boolean) {
        super.setSpeed(speed, soundTouch)
        onSpeedChanged(speed)
    }

    /**
     * 视频播放速度改变后回调
     */
    protected open fun onSpeedChanged(speed: Float) {

    }

    /**
     * 需要在尺寸发生变化的时候重新处理
     */
    override fun onSurfaceSizeChanged(
        surface: Surface?,
        width: Int,
        height: Int
    ) {
        super.onSurfaceSizeChanged(surface, width, height)
        resolveTransform(mTextureViewTransform)
    }

    override fun onSurfaceAvailable(surface: Surface?) {
        super.onSurfaceAvailable(surface)
//        resolveRotateUI()
        resolveTransform(mTextureViewTransform)
    }

    /**
     * 处理镜像旋转
     */
    private fun resolveTransform(transformSize: Int) {
        if (mTextureView == null) return
        val transform = Matrix()
        when (transformSize) {
            NO_REVERSE -> {  // 正常
                transform.setScale(1f, 1f, mTextureView.width / 2.toFloat(), 0f)
            }
            HORIZONTAL_REVERSE -> {  // 左右镜像
                transform.setScale(-1f, 1f, mTextureView.width / 2.toFloat(), 0f)
            }
            VERTICAL_REVERSE -> {  // 上下镜像
                transform.setScale(1f, -1f, 0f, mTextureView.height / 2.toFloat())
            }
            else -> return
        }
        mTextureViewTransform = transformSize
        mTextureView.setTransform(transform)
        mTextureView.invalidate()
    }

    override fun setUp(
        url: String?,
        cacheWithPlay: Boolean,
        cachePath: File?,
        title: String?
    ): Boolean {
        val result = super.setUp(url, cacheWithPlay, cachePath, title)
        mTitleTextView?.let {
            if (it is TypefaceTextView) {
                it.isFocused = true
            }
        }
        return result
    }

    override fun updateStartImage() {
        if (mStartButton is ImageView) {
            val imageView = mStartButton as ImageView
            when (mCurrentState) {
                GSYVideoView.CURRENT_STATE_PLAYING -> {
                    imageView.setImageDrawable(getResDrawable(R.drawable.ic_pause_white_24))
                }
                GSYVideoView.CURRENT_STATE_ERROR -> {
                    imageView.setImageDrawable(getResDrawable(R.drawable.ic_play_white_24))
                }
                GSYVideoView.CURRENT_STATE_AUTO_COMPLETE -> {
                    imageView.setImageDrawable(getResDrawable(R.drawable.ic_refresh_white_24))
                }
                else -> {
                    imageView.setImageDrawable(getResDrawable(R.drawable.ic_play_white_24))
                }
            }
        } else {
            super.updateStartImage()
        }
    }

    fun updateBottomProgressBar(isChecked: Boolean) {
        playBottomProgress?.isVisible = isChecked
        context?.sharedPreferences()?.editor {
            putBoolean(Const.Setting.SHOW_PLAY_BOTTOM_BAR, isChecked)
        }
        mBottomProgressBar = if (isChecked) playBottomProgress else null
    }

    override fun onBrightnessSlide(percent: Float) {
        val activity = mContext as Activity
        val lpa = activity.window.attributes
        val mBrightnessData = lpa.screenBrightness
        if (mBrightnessData <= 0.00f) {
            getScreenBrightness(activity)?.div(255.0f)?.let {
                lpa.screenBrightness = it
                activity.window.attributes = lpa
            }
        }
        super.onBrightnessSlide(percent)
    }

    override fun onVideoSizeChanged() {
        super.onVideoSizeChanged()
        mVideoAllCallBack.let {
            if (it is MyVideoAllCallBack) it.onVideoSizeChanged()
        }
    }

    //正常
    override fun changeUiToNormal() {
        super.changeUiToNormal()
        viewTopContainerShadow?.visible()
        initFirstLoad = true
        mUiCleared = false
    }

    override fun changeUiToPauseShow() {
        super.changeUiToPauseShow()
        viewTopContainerShadow?.visible()
        mUiCleared = false
    }

    override fun changeUiToClear() {
        super.changeUiToClear()
        viewTopContainerShadow?.invisible()
        mUiCleared = true

        if (vgPlayPosition?.isVisible == true) ivClosePlayPositionTip?.callOnClick()
    }

    //准备中
    override fun changeUiToPreparingShow() {
        super.changeUiToPreparingShow()
        viewTopContainerShadow?.visible()
        mUiCleared = false
    }

    //播放中
    override fun changeUiToPlayingShow() {
        super.changeUiToPlayingShow()
        viewTopContainerShadow?.visible()
//        if (initFirstLoad) {
//            mBottomContainer.gone()
//            mStartButton.gone()
//        }
        initFirstLoad = false
        mUiCleared = false
    }

    //自动播放结束
    override fun changeUiToCompleteShow() {
        super.changeUiToCompleteShow()
        viewTopContainerShadow?.visible()
        mBottomContainer?.gone()
        tvTouchDownHighSpeed?.gone()
        mUiCleared = false

        if (vgPlayPosition?.isVisible == true) ivClosePlayPositionTip?.callOnClick()
    }

    override fun changeUiToError() {
        super.changeUiToError()
        viewTopContainerShadow?.invisible()

        if (vgPlayPosition?.isVisible == true) ivClosePlayPositionTip?.callOnClick()
    }

    override fun changeUiToPrepareingClear() {
        super.changeUiToPrepareingClear()
        viewTopContainerShadow?.invisible()
    }

    override fun changeUiToPlayingBufferingClear() {
        super.changeUiToPlayingBufferingClear()
        viewTopContainerShadow?.invisible()
    }

    override fun changeUiToCompleteClear() {
        super.changeUiToCompleteClear()
        viewTopContainerShadow?.invisible()
    }

    override fun changeUiToPlayingBufferingShow() {
        super.changeUiToPlayingBufferingShow()
        viewTopContainerShadow?.visible()
    }

    override fun onVideoPause() {
        super.onVideoPause()
        mVideoAllCallBack.let {
            if (it is MyVideoAllCallBack) it.onVideoPause()
        }
    }

    override fun onVideoResume(seek: Boolean) {
//        super.onVideoResume(seek)
        mPauseBeforePrepared = false
        if (mCurrentState == GSYVideoView.CURRENT_STATE_PAUSE) {
            try {
                clickStartIcon()
                mVideoAllCallBack.let {
                    if (it is MyVideoAllCallBack) it.onVideoResume()
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    public override fun clickStartIcon() {
        super.clickStartIcon()

        // 下面是处理完点击后的逻辑
        if (mCurrentState == CURRENT_STATE_PLAYING) {
            onVideoResume()
        }
    }

    override fun onClick(v: View) {
        super.onClick(v)

        when (v.id) {
            // bigger_surface代替原有的surface_container执行点击动作
            R.id.bigger_surface -> {
                vgSettingContainer?.gone()
                vgRightContainer?.gone()
                if (mCurrentState == GSYVideoView.CURRENT_STATE_ERROR) {
                    if (mVideoAllCallBack != null) {
                        Debuger.printfLog("onClickStartError")
                        mVideoAllCallBack.onClickStartError(mOriginUrl, mTitle, this)
                    }
                    prepareVideo()
                } else {
                    if (mVideoAllCallBack != null && isCurrentMediaListener) {
                        if (mIfCurrentIsFullscreen) {
                            Debuger.printfLog("onClickBlankFullscreen")
                            mVideoAllCallBack.onClickBlankFullscreen(mOriginUrl, mTitle, this)
                        } else {
                            Debuger.printfLog("onClickBlank")
                            mVideoAllCallBack.onClickBlank(mOriginUrl, mTitle, this)
                        }
                    }
                    startDismissControlViewTimer()
                }
            }
            R.id.thumb -> {
                vgSettingContainer?.gone()
                vgRightContainer?.gone()
            }
            R.id.back -> (context as Activity).finish()
        }
    }

    /**
     * 双击的时候调用此方法
     */
    //TODO 双击应该隐藏其他界面
    override fun touchDoubleUp(e: MotionEvent?) {
        // 处理双击前的逻辑
        val oldUiVisibilityState = mBottomContainer?.visibility ?: VISIBLE

        // 处理双击
        super.touchDoubleUp(e)

        // 下面是处理完双击后的逻辑
        if (mCurrentState == CURRENT_STATE_PLAYING) {       // 若双击后是播放状态
            //双击前Ui是什么可见性状态，则双击后Ui还是什么可见性状态，避免双击后Ui突然显示出来
            if (oldUiVisibilityState == VISIBLE) changeUiToPlayingShow()
            else changeUiToPlayingClear()
//            cancelDismissControlViewTimer()
        } else if (mCurrentState == CURRENT_STATE_PAUSE) {  // 若双击后是暂停状态
            //双击前Ui是什么可见性状态，则双击后Ui还是什么可见性状态，避免双击后Ui突然显示出来
            if (oldUiVisibilityState == VISIBLE) changeUiToPauseShow()
            else changeUiToPauseClear()
//            cancelDismissControlViewTimer()
        }
    }

    override fun touchLongPress(e: MotionEvent?) {
        e ?: return
        if (e.pointerCount == 1) {
            // 长按加速
            if (!mLongPressing && e.action == MotionEvent.ACTION_DOWN && !doublePointerZoomingMoving) {
                mLongPressing = true
                // 此处不能设置mPlaySpeed
                setSpeed(2f, true)
                tvTouchDownHighSpeed?.text =
                    mContext.getString(R.string.touch_down_high_speed, "2")
                tvTouchDownHighSpeed?.visible()
            }
        }
    }

    override fun touchSurfaceMoveFullLogic(absDeltaX: Float, absDeltaY: Float) {
        // 全屏下拉任务栏
        if (absDeltaY > mThreshold && absDeltaY > absDeltaX && mDownY <= mStatusBarOffset) {
            cancelProgressTimer()
            return
        }
        super.touchSurfaceMoveFullLogic(absDeltaX, absDeltaY)
    }

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        // ---长按逻辑开始
        if (event.pointerCount == 1) {
            if (event.action == MotionEvent.ACTION_UP) {
                // 如果刚才在长按，则取消长按
                if (mLongPressing) {
                    mLongPressing = false
                    setSpeed(mPlaySpeed, true)
                    tvTouchDownHighSpeed?.gone()
                    return false
                }
            }
        }
        // ---长按逻辑结束
        // 不是全屏下，不使用双指操作
        if (!mIfCurrentIsFullscreen) return super.onTouch(v, event)
        if (v?.id == R.id.surface_container) {
            if (event.pointerCount > 1 && event.actionMasked == MotionEvent.ACTION_MOVE) {
                // 如果是surface_container并且触摸手指数大于1，则return false拦截
                // 不让super的代码执行，表明正在双指放大移动旋转
                doublePointerZoomingMoving = true
                mDoublePointerZoomMoved = true
                if (!mUiCleared) tvRestoreScreen?.visible()
                // 下面用bigger_surface代替原有的surface_container执行手势动作
                return false
            }
        }
        // 当正在双指操作时，禁止执行super的代码
        if (doublePointerZoomingMoving) {
            tvRestoreScreen?.visible()
            // 如果双指松开，则标志不是在移动
            if (event.action == MotionEvent.ACTION_UP) {
                doublePointerZoomingMoving = false
            }
            return false
        }
        return if (v?.id == R.id.bigger_surface || v?.id == R.id.surface_container) {
            val x = event.x
            val y = event.y
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchSurfaceDown(x, y)
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = x - mDownX
                    val deltaY = y - mDownY
                    val absDeltaX = abs(deltaX)
                    val absDeltaY = abs(deltaY)
                    if (mIfCurrentIsFullscreen && mIsTouchWigetFull
                        || mIsTouchWiget && !mIfCurrentIsFullscreen
                    ) {
                        if (!mChangePosition && !mChangeVolume && !mBrightness) {
                            touchSurfaceMoveFullLogic(absDeltaX, absDeltaY)
                        }
                    }
                    touchSurfaceMove(deltaX, deltaY, y)
                }
                MotionEvent.ACTION_UP -> {
                    startDismissControlViewTimer()
                    touchSurfaceUp()
                    Debuger.printfLog(
                        this.hashCode()
                            .toString() + "------------------------------ surface_container ACTION_UP"
                    )
                    startProgressTimer()
                    //不要和隐藏虚拟按键后，滑出虚拟按键冲突
                    if (mHideKey && mShowVKey) return true
                }
            }
            gestureDetector.onTouchEvent(event)
            return false
        } else {
            super.onTouch(v, event)
        }
    }

    override fun onBackFullscreen() {
        if (!mFullAnimEnd) {
            return
        }
        mIfCurrentIsFullscreen = false
        var delay = 0
        if (mOrientationUtils != null) {
            val orientationUtils = mOrientationUtils
            delay = if (orientationUtils is AnimeOrientationUtils)
                orientationUtils.backToProtVideo2()
            else
                orientationUtils.backToProtVideo()
            mOrientationUtils.isEnable = false
            if (mOrientationUtils != null) {
                mOrientationUtils.releaseListener()
                mOrientationUtils = null
            }
        }

        if (!mShowFullAnimation) {
            delay = 0
        }

        val vp = CommonUtil.scanForActivity(context)
            .findViewById<View>(Window.ID_ANDROID_CONTENT) as ViewGroup
        val oldF = vp.findViewById<View>(fullId)
        if (oldF != null) {
            //此处fix bug#265，推出全屏的时候，虚拟按键问题
            val gsyVideoPlayer = oldF as GSYVideoPlayer
            gsyVideoPlayer.isIfCurrentIsFullscreen = false
        }

        mInnerHandler.postDelayed({ backToNormal() }, delay.toLong())
    }

    /**
     * 准备好视频，开始查找进度
     */
    override fun onPrepared() {
        playPositionViewJob?.cancel()
        playPositionMemoryStore?.apply {
            playPositionMemoryStoreCoroutineScope.launch {
                getPlayPosition(mOriginUrl)?.also {
                    preSeekPlayPosition = it
                    if (it > 0L) {
                        //TODO 自动跳转开关
                        val isAutoSeek = true
                        if (isAutoSeek) {
                            seekOnStart = it
                            context.getString(R.string.play_auto_seek).showToast()
                        } else
                            playPositionViewJob = launch(Dispatchers.Main) {
                                tvPlayPosition?.text = positionFormat(it)
                                vgPlayPosition?.visible()
                                //展示5秒
                                delay(5000)
                                vgPlayPosition?.gone(true, 200L)
                            }
                    }
                }
            }
        }
        super.onPrepared()
    }

    /**
     * 1.退出界面时记忆进度
     */
    override fun onDetachedFromWindow() {
        storePlayPosition()
        super.onDetachedFromWindow()
    }

    /**
     * 2.切换选集时记忆进度
     */
    override fun setUp(
        url: String?,
        cacheWithPlay: Boolean,
        cachePath: File?,
        title: String?,
        changeState: Boolean
    ): Boolean {
        if (url != mOriginUrl) {
            vgPlayPosition?.gone()
            storePlayPosition()
        }

        return super.setUp(url, cacheWithPlay, cachePath, title, changeState)
    }

    /**
     * 1.退出界面时记忆进度
     * 2.切换选集时记忆进度
     *
     * @param position 小于0代表播放完毕（已看完），大于等于0代表正常进度
     *
     * 注意：记忆单位是每个视频而不是一部番剧；一部番剧里面的每集都有记录，并非只记录最后看的那一集
     */
    private fun storePlayPosition(position: Long = gsyVideoManager.currentPosition) {
        when (currentState) {
            CURRENT_STATE_PREPAREING, CURRENT_STATE_PLAYING_BUFFERING_START, CURRENT_STATE_ERROR -> return
        }
        val url = mOriginUrl
        val duration = gsyVideoManager.duration
        // 进度为负（已经播放完） 或 当前进度大于最小限制且小于最大限制（播放完时不记录），则记录
        if (position < 0 || (position > playPositionMemoryTimeLimit && duration > 0
                    && abs(position - duration) > 2000L)
        ) {
            playPositionMemoryStore?.apply {
                playPositionMemoryStoreCoroutineScope.launch {
                    putPlayPosition(url, position)
                }
            }
        }
    }

    override fun onAutoCompletion() {
        super.onAutoCompletion()
        // 播放完毕
        storePlayPosition(-1L)
    }

    fun enableDismissControlViewTimer(start: Boolean) {
        if (start) super.startDismissControlViewTimer()
        else super.cancelDismissControlViewTimer()
    }

    class SpeedBean(
        override var type: String,
        override var actionUrl: String,
        var title: String
    ) : BaseBean, Serializable

    inner class SpeedAdapter(val list: List<SpeedBean>) : SkinRvAdapter() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return AnimeVideoPlayer.RightRecyclerViewViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_player_list_item_1, parent, false)
            ).apply { SkinManager.setSkin(itemView) }
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            super.onBindViewHolder(holder, position)
            val item = list[position]

            when (holder) {
                is AnimeVideoPlayer.RightRecyclerViewViewHolder -> {
                    if (item.type == "speed") {
                        if (item.title.toFloat() == speed) {
                            holder.tvTitle.setTextColor(context.getResColor(R.color.unchanged_main_color_2_skin))
                        }
                        holder.tvTitle.text = item.title
                        holder.itemView.setOnClickListener {
                            if (item.title == "1") {
                                tvSpeed?.text = App.context.getString(R.string.play_speed)
                            } else {
                                tvSpeed?.text = item.title + "X"
                            }
                            mPlaySpeed = item.title.toFloat()
                            setSpeed(mPlaySpeed, true)
                            vgRightContainer?.gone()
                            //因为右侧界面显示时，不在xx秒后隐藏界面，所以要恢复xx秒后隐藏控制界面
                            startDismissControlViewTimer()
                        }
                    }
                }
            }
        }

        override fun getItemCount(): Int = list.size
    }
}