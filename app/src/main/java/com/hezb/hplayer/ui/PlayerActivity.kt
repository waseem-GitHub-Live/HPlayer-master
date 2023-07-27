package com.hezb.hplayer.ui

import android.content.ComponentName
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
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
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.hezb.clingupnp.HttpServerService
import com.hezb.clingupnp.HttpServerService.Companion.getLocalIpByWifi
import com.hezb.clingupnp.server.NanoHttpServer


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
    private var httpServerService: HttpServerService? = null
    private var videoId: String? = null
    private var isHttpServerServiceBound = false
    private var mCastContext: CastContext? = null
    private  var mNanoHttpServer: NanoHttpServer? = null
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
        bindHttpServerService()
        initAllMember()
    }
    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteSelected(router: MediaRouter?, route: MediaRouter.RouteInfo?) {
            super.onRouteSelected(router, route)
            castDevice = CastDevice.getFromBundle(route?.extras)
            startCasting()
        }

        override fun onRouteUnselected(router: MediaRouter?, route: MediaRouter.RouteInfo?) {
            super.onRouteUnselected(router, route)
            castDevice = null
            isCasting = false
        }
    }
    private fun bindHttpServerService() {
        val serviceIntent = Intent(this, HttpServerService::class.java)
        bindService(serviceIntent, httpServerServiceConnection, Context.BIND_AUTO_CREATE)
    }

    private val httpServerServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as HttpServerService.LocalBinder
            httpServerService = binder.getService()
            isHttpServerServiceBound = true // Service is now bound and initialized
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            httpServerService = null
            isHttpServerServiceBound = false // Service is disconnected
        }
    }
    override fun onStart() {
        super.onStart()
        bindHttpServerService() // Bind the HttpServerService
        val intent = intent
        onNewIntent(intent)

        // Register the mediaRouterCallback
        val mediaRouter = MediaRouter.getInstance(this)
        mediaRouteSelector?.let { mediaRouter.addCallback(it, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY) }
    }
    override fun onStop() {
        super.onStop()


        val mediaRouter = MediaRouter.getInstance(this)
        mediaRouter.removeCallback(mediaRouterCallback)
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
        intentToJoin()
        if (isCasting) {
            startCasting()
        } else {
            mViewBinding?.playerControllerView?.play()
        }
        castContext?.addCastStateListener(castStateListener)
    }

    private fun intentToJoin() {
        val intent = intent
        val intentToJoinUri = Uri.parse("https://castvideos.com/cast/join")
        Log.i(TAG, "URI passed: $intentToJoinUri")
        if (intent.data != null && intent.data == intentToJoinUri) {
            mCastContext?.sessionManager?.startSession(intent)
            Log.i(TAG, "Uri Joined: $intentToJoinUri")
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            initData(it)
            mViewBinding.playerControllerView.mTitle.text = videoTitle
            playUri?.let {
                if (videoId != null) {
                    val mediaUrl = HttpServerService.getVideoUrl1(this, videoId)
                    if (mediaUrl != null) {
                        playMedia(mediaUrl, videoTitle)
                    } else {
                        Toast.makeText(this, "Media URL new not available", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    playUri?.let {
                        playMedia(it.toString(), videoTitle)
                    }
                }
            }
        }
    }
    private fun playMedia(videoId: String?, title: String?) {
        videoId?.let {
            // Assuming the videoId is the local URI of the video file
            val mediaUri = Uri.parse(videoId)
            val mediaModel = MediaModel(mediaUri)

            // Check if the device is casting
            if (castDevice != null) {
                // Device is casting
                val mediaInfo = createMediaInfoForCasting(videoId, title)
                val mediaLoadOptions = MediaLoadOptions.Builder()
                    .setAutoplay(true)
                    .build()
                castSession?.remoteMediaClient?.load(mediaInfo, mediaLoadOptions)?.setResultCallback { mediaLoadResult ->
                    if (mediaLoadResult.status.isSuccess) {
                        Log.d("PlayerActivity", "Casting started")
                        // Release the local ExoPlayer when casting starts
                        releaseLocalPlayer()
                    } else {
                        // Failed to load media for casting, fallback to local playback
                        playVideoLocally(mediaModel)
                    }
                }
            } else {
                // No casting, play the video locally
                playVideoLocally(mediaModel)
            }
        }
    }

    private fun playVideoLocally(mediaModel: MediaModel) {
        mediaPlayer = ExoPlayer()
        mediaPlayer?.setMediaSource(this, mediaModel)
        appPlayerControllerView.bindMediaPlayer(mediaPlayer as ExoPlayer)
        mViewBinding.playerControllerView.play()
    }

    private fun releaseLocalPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onPause() {
        super.onPause()
        mViewBinding?.playerControllerView?.pause()
        castContext?.removeCastStateListener(castStateListener)
    }

    override fun onDestroy() {
        sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
        if (isHttpServerServiceBound) {
            unbindService(httpServerServiceConnection)
            isHttpServerServiceBound = false
        }
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
                castDevice = null
                isCasting = false
            }
        }
    private fun startCasting() {
        if (!isCasting && castDevice != null) {
            val remoteMediaClient = castSession?.remoteMediaClient
            val mediaUrl = HttpServerService.getVideoUrl1(this, videoId)
            if (mediaUrl != null) {
                val mediaInfo = createMediaInfoForCasting(mediaUrl, videoTitle)
                val mediaLoadOptions = MediaLoadOptions.Builder()
                    .setAutoplay(true)
                    .build()
                remoteMediaClient?.load(mediaInfo, mediaLoadOptions)?.setResultCallback { mediaLoadResult ->
                    if (mediaLoadResult.status.isSuccess) {
                        Log.e("Casting", "start ${mediaLoadResult.status.isSuccess}")
                        Toast.makeText(this, "Casting started", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Media URL start not available", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun createMediaInfoForCasting(mediaUrl: String?, title: String?): MediaInfo {
        requireNotNull(mediaUrl) { "mediaUrl must not be null" }
        requireNotNull(title) { "title must not be null" }

        val mediaContentType = "video/mp4"

        return MediaInfo.Builder(mediaUrl)
            .setContentType(mediaContentType)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(MediaMetadata.KEY_TITLE, title)
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