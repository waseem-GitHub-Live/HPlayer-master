package com.hezb.hplayer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.mediarouter.media.MediaRouteSelector
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.hezb.hplayer.databinding.ActivityPlayerBinding
import com.hezb.player.core.MediaModel
import com.hezb.player.exo.ExoPlayer
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.hezb.hplayer.R
import com.hezb.hplayer.ui.widget.AppPlayerControllerView
import com.hezb.player.core.AbstractMediaPlayer
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient




class PlayerActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks,GoogleApiClient.OnConnectionFailedListener {
    companion object {
        private const val KEY_VIDEO_PATH = "video_path"
        private const val KEY_VIDEO_TITLE = "video_title"

        fun startPlayerActivity(
            context: Context,
            videoPath: String?,
            videoTitle: String?

        ) {
            val intent = Intent(context, PlayerActivity::class.java)
            intent.putExtra(KEY_VIDEO_PATH, videoPath)
            intent.putExtra(KEY_VIDEO_TITLE, videoTitle)
            context.startActivity(intent)
        }
    }
    private lateinit var mViewBinding: ActivityPlayerBinding
    private lateinit var appPlayerControllerView: AppPlayerControllerView
    private var castContext: CastContext? = null
    private var castSession: CastSession? = null
    private var mediaRouteSelector: MediaRouteSelector? = null
    private var castDevice: CastDevice? = null
    private var isCasting = true
    private var sessionManager: SessionManager? = null
    private lateinit var googleApiClient: GoogleApiClient
    private var playUri: Uri? = null
    private var videoTitle: String? = null
    private var mediaPlayer: AbstractMediaPlayer? = null
    private lateinit var mediaRouteButton: MediaRouteButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mViewBinding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(mViewBinding.root)

        mediaRouteButton = findViewById(R.id.media_route_button)
        castContext = CastContext.getSharedInstance(this)

        mediaRouteSelector = MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(getString(R.string.app_id)))
            .build()

        mediaRouteButton.routeSelector = mediaRouteSelector as MediaRouteSelector
        appPlayerControllerView = mViewBinding.playerControllerView

        sessionManager = CastContext.getSharedInstance(this).sessionManager
        sessionManager?.addSessionManagerListener(sessionManagerListener, CastSession::class.java)

        initAllMember()
    }

    override fun onConnected(bundle: Bundle?) {
        // Handle successful connection to Google Cast API
        Log.d("PlayerActivity", "Connected to Google Cast API")
    }

    override fun onConnectionSuspended(i: Int) {
        // Handle connection suspension
        Log.d("PlayerActivity", "Connection to Google Cast API suspended")
    }

    override fun onConnectionFailed(result: ConnectionResult) {
        // Handle connection failure
        Log.e("PlayerActivity", "Connection to Google Cast API failed: ${result.errorMessage}")
    }



    override fun onResume() {
        super.onResume()
        val intent = intent
        onNewIntent(intent)

        if (isCasting) {
            startCasting()
        } else {
            mViewBinding?.playerControllerView?.play()
        }
        castContext?.addCastStateListener(castStateListener)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        intent?.let {
            initData(it)

            mViewBinding.playerControllerView.mTitle.text = videoTitle
            playUri?.let {
                mViewBinding.playerControllerView.play()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mViewBinding?.playerControllerView?.pause()
        castContext?.removeCastStateListener(castStateListener)
    }

    override fun onDestroy() {
        sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
        super.onDestroy()
    }

    private fun initAllMember() {
        initData(intent)
        initPlayer()
    }

    private fun initData(intent: Intent) {
        val path = intent.getStringExtra(KEY_VIDEO_PATH)
        playUri = Uri.parse(path)
        videoTitle = intent.getStringExtra(KEY_VIDEO_TITLE)
    }

    private fun initPlayer() {
        mViewBinding.playerControllerView.mBackBtn.setOnClickListener {
            onBackPressed()
        }
        mViewBinding.playerControllerView.mTitle.text = videoTitle
        playUri?.let {
            val mediaModel = MediaModel(it)
            mediaPlayer = ExoPlayer()
            mediaPlayer?.setMediaSource(this, mediaModel)
            appPlayerControllerView.bindMediaPlayer(mediaPlayer as ExoPlayer)

            mViewBinding.playerControllerView.play()
        }
    }
    private val castStateListener =
        CastStateListener { newState ->
            if (newState == CastState.NO_DEVICES_AVAILABLE) {
                // Handle no devices available
                castDevice = null
                isCasting = false
            }
        }
    private fun startCasting() {
        if (!isCasting && castDevice != null) {
            val remoteMediaClient = castSession?.remoteMediaClient
            val mediaInfo = createMediaInfoForCasting()
            val mediaLoadOptions = MediaLoadOptions.Builder()
                .setAutoplay(true)
                .build()
            remoteMediaClient?.load(mediaInfo, mediaLoadOptions)?.setResultCallback { mediaLoadResult ->
                if (mediaLoadResult.status.isSuccess) {
                    Log.e("CastingError", "Failed to cast: ${mediaLoadResult.status.isSuccess}")
                    Toast.makeText(this, "Casting started", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createMediaInfoForCasting(): MediaInfo {
        val mediaUrl = playUri.toString()
        val mediaTitle = videoTitle ?: "Untitled"
        val mediaContentType = "video/mp4"

        return MediaInfo.Builder(mediaUrl)
            .setContentType(mediaContentType)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(MediaMetadata.KEY_TITLE, mediaTitle)
///////////////////////////////////////////
            })
            .build()
    }

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession?) {}

        override fun onSessionStarted(session: CastSession?, sessionId: String?) {
            castSession = session
            isCasting = true
            startCasting()
        }

        override fun onSessionStartFailed(session: CastSession?, error: Int) {}
        override fun onSessionEnding(session: CastSession?) {}

        override fun onSessionResumed(session: CastSession?, wasSuspended: Boolean) {
            castSession = session
            isCasting = true
            startCasting()
        }

        override fun onSessionResumeFailed(session: CastSession?, error: Int) {}
        override fun onSessionSuspended(session: CastSession?, reason: Int) {}
        override fun onSessionResuming(session: CastSession?, sessionId: String?) {}
        override fun onSessionEnded(session: CastSession?, error: Int) {
            castSession = null
            isCasting = false
        }
    }


}