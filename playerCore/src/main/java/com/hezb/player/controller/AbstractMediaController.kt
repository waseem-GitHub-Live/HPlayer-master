package com.hezb.player.controller

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.Surface
import android.widget.FrameLayout
import com.hezb.player.core.AbstractMediaPlayer
import com.hezb.player.core.IMediaPlayer
import com.hezb.player.render.IRenderView

abstract class AbstractMediaController @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), IMediaPlayer.PlayerCallback
{


    protected var mMediaPlayer: AbstractMediaPlayer? = null


    protected var mRenderView: IRenderView? = null
        private set


    protected var updatePlayerInfoDelay = 200


    protected val MSG_UPDATE_PLAYER_INFO = 1

    protected val mHandler: Handler =
        object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (msg.what == MSG_UPDATE_PLAYER_INFO) {
                    mMediaPlayer?.let {
                        autoUpdateInfo(it)

                        val pos = it.getCurrentPosition()
                        val delayMillis = updatePlayerInfoDelay - pos % updatePlayerInfoDelay
                        if (hasMessages(MSG_UPDATE_PLAYER_INFO)) {
                            removeMessages(MSG_UPDATE_PLAYER_INFO)
                        }
                        val message = obtainMessage(MSG_UPDATE_PLAYER_INFO)
                        sendMessageDelayed(message, delayMillis)
                    }
                } else {
                    onHandleMessage(msg)
                }
            }
        }

    protected open fun autoUpdateInfo(mp: AbstractMediaPlayer) {
        updateCurrentPosition(mp.getCurrentPosition())
    }

    fun handlerUpdatePlayerInfo() {
        stopUpdatePlayerInfo()
        mHandler.sendEmptyMessage(MSG_UPDATE_PLAYER_INFO)
    }
    fun stopUpdatePlayerInfo() {
        if (mHandler.hasMessages(MSG_UPDATE_PLAYER_INFO)) {
            mHandler.removeMessages(MSG_UPDATE_PLAYER_INFO)
        }
    }
    protected open fun onHandleMessage(msg: Message) {}

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mHandler.removeCallbacksAndMessages(null)
    }
    protected fun initRendView(renderView: IRenderView?) {
        if (renderView != null) {
            initRendView(renderView, object : IRenderView.IRenderCallback {
                override fun onSurfaceCreated(surface: Surface?, width: Int, height: Int) {
                    keepScreenOn = true
                    mMediaPlayer?.let {
                        it.setSurface(surface)
                        val videoWidth = it.getVideoWidth()
                        val videoHeight = it.getVideoHeight()
                        if (videoWidth != 0 && videoHeight != 0) {
                            renderView.setVideoSize(videoWidth, videoHeight)
                        }
                    }
                }

                override fun onSurfaceChanged(surface: Surface?, width: Int, height: Int) {
                }

                override fun onSurfaceDestroyed(surface: Surface?) {
                    keepScreenOn = false
                    mMediaPlayer?.releaseSurface(surface)
                }
            })
        }
    }
    protected fun initRendView(
        renderView: IRenderView?,
        renderCallback: IRenderView.IRenderCallback?
    ) {
        if (renderView != null) {
            mRenderView = renderView
            if (renderCallback != null) {
                renderView.setRenderCallback(renderCallback)
            }
        }
    }
    open fun unBindMediaPlayer() {
        mMediaPlayer?.let {
            it.removePlayerCallback()
            it.setSurface(null)
            mMediaPlayer = null
        }
        mHandler.removeCallbacksAndMessages(null)
    }
    open fun bindMediaPlayer(mediaPlayer: AbstractMediaPlayer) {
        mMediaPlayer = mediaPlayer
        mediaPlayer.setPlayerCallback(this)
        mRenderView?.getSurface()?.let {
            mediaPlayer.setSurface(it)
        } ?: mediaPlayer.releaseSurface(null)
    }

    fun hadBindMediaPlayer(): Boolean {
        return mMediaPlayer != null
    }

    protected open fun updatePlayerState(isPlaying: Boolean) {}

    protected open fun updateCurrentPosition(position: Long) {}

    protected open fun updateDuration(duration: Long) {}

    open fun play() {
        mMediaPlayer?.let {
            if (!it.isInPlaybackState()) {
                it.prepareAsync()
            }
            it.start()
        }
        updatePlayerState(true)
        handlerUpdatePlayerInfo()
    }

    open fun pause() {
        mMediaPlayer?.pause()
        updatePlayerState(false)
        stopUpdatePlayerInfo()
    }

    open fun seekTo(pos: Long) {
        mMediaPlayer?.seekTo(pos)
    }

    open fun stop() {
        mMediaPlayer?.stop()
        updatePlayerState(false)
    }

    open fun release() {
        mMediaPlayer?.release()
        updatePlayerState(false)
    }

    override fun onPreparing(mp: IMediaPlayer) {}

    override fun onPrepared(mp: IMediaPlayer) {
        updateDuration(mp.getDuration())
    }

    override fun onCompletion(mp: IMediaPlayer) {
        stopUpdatePlayerInfo()

        updatePlayerState(false)
        updateCurrentPosition(mp.getDuration())
    }

    override fun onBufferingUpdate(mp: IMediaPlayer) {}

    override fun onSeekComplete(mp: IMediaPlayer) {}

    override fun onVideoSizeChanged(mp: IMediaPlayer, width: Int, height: Int) {
        if (width != 0 && height != 0) {
            mRenderView?.setVideoSize(width, height)
        }
    }

    override fun onError(mp: IMediaPlayer, what: Int, extra: Int): Boolean {
        updatePlayerState(false)
        return false
    }

    override fun onInfo(mp: IMediaPlayer, what: Int, extra: Int): Boolean {
        when (what) {
            3 -> { // MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START 开始渲染视频
            }
            10001 -> { // IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED 视频方向变化
                mRenderView?.setVideoRotation(extra)
            }
        }
        return false
    }

}