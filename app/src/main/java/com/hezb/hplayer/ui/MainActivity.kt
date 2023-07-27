package com.hezb.hplayer.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.cast.framework.CastContext
import com.hezb.clingupnp.HttpServerService
import com.hezb.clingupnp.UpnpDMSService
import com.hezb.clingupnp.model.MediaInfo
import com.hezb.clingupnp.util.ContentResolverUtil
import com.hezb.hplayer.base.BaseActivity
import com.hezb.hplayer.databinding.ActivityMainBinding
import com.hezb.hplayer.ui.adapter.VideoListAdapter
import com.hezb.hplayer.util.ToastUtil
import kotlinx.coroutines.*

/**
 * Project Name: HPlayer
 * File Name:    MainActivity
 *
 * Description: 应用主页.
 *
 * @author  hezhubo
 * @date    2022年03月02日 22:13
 */
class MainActivity() : BaseActivity() {

    override lateinit var castContext: CastContext
    override lateinit var videoListAdapter: VideoListAdapter

    private val mViewBinding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private var deferred: Deferred<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(mViewBinding.root)
        castContext = CastContext.getSharedInstance(this)

        val videoList = mutableListOf<MediaInfo>()

        videoListAdapter = VideoListAdapter(videoList)

        mViewBinding.rvVideoList.layoutManager = LinearLayoutManager(this)
        mViewBinding.rvVideoList.adapter = videoListAdapter
        initAllMember()

        videoListAdapter.onItemClickListener = object : VideoListAdapter.OnItemClickListener {
            override fun onItemClick(video: MediaInfo) {
                val intent = Intent(this@MainActivity, HttpServerService::class.java)
                startService(intent)
                startHttpServerService()
            }
        }
    }


    private fun initAllMember() {
        mViewBinding.dlnaRemoteDevices.setOnClickListener {
            startActivity(Intent(this, ContentBrowseActivity::class.java))
        }
        mViewBinding.ivDmsService.setOnClickListener {
            startService(Intent(this, UpnpDMSService::class.java))
            ToastUtil.show(this, "DMS service started")
        }

        if (checkPermission()) {
            loadLocalVideo()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        deferred?.let {
            if (!it.isCancelled) {
                it.cancel()
            }
        }
    }

    private fun checkPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val result = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            if (result != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 999)
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 999) {
            if (permissions.isNotEmpty() && permissions.size == grantResults.size) {
                for (i in permissions.indices) {
                    if (permissions[i] == Manifest.permission.READ_EXTERNAL_STORAGE) {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            loadLocalVideo()
                            return
                        }
                    }
                }
                Toast.makeText(this, "Authorization failed！", Toast.LENGTH_SHORT).show()
            }
        }
    }


    @OptIn(DelicateCoroutinesApi::class)
    private fun loadLocalVideo() {
        GlobalScope.launch(Dispatchers.Main) {
            val deferred = GlobalScope.async(Dispatchers.IO) {
                return@async ContentResolverUtil.queryVideos(this@MainActivity)
            }.also {
                deferred = it
            }
            val videoList = deferred.await()
            if (videoList.isEmpty()) {
                // 无视频

            } else {
                mViewBinding.rvVideoList.layoutManager = LinearLayoutManager(this@MainActivity)
                val videoListAdapter = VideoListAdapter(videoList)
                videoListAdapter.onItemClickListener =
                    object : VideoListAdapter.OnItemClickListener {
                        override fun onItemClick(video: MediaInfo) {
                            PlayerActivity.startPlayerActivity(
                                this@MainActivity,
                                video.path,
                                video.displayName
                            )
                        }
                    }
                mViewBinding.rvVideoList.adapter = videoListAdapter
            }
            deferred.cancel()
        }
    }

    /**
     * Start the http server service for sharing local files
     * Here, in order to facilitate the server to start at the same time when the application starts, it can be turned on when you need to cast local files
     */
    private fun startHttpServerService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, HttpServerService::class.java))
        } else {
            startService(Intent(this, HttpServerService::class.java))
        }
    }

}