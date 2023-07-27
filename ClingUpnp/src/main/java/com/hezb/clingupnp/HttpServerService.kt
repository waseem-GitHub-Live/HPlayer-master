package com.hezb.clingupnp

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import com.hezb.clingupnp.server.NanoHttpServer
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.security.KeyStore
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * Project Name: HPlayer
 * File Name:    HttpServerService
 *
 * Description: http服务器本地服务.
 *
 * @author  hezhubo
 * @date    2022年03月05日 18:24
 */
class HttpServerService : Service() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "http_server"
        const val SERVER_ADDRESS = "localhost"
        const val SERVER_PORT = 8086
        const val HTTPS_SERVER_PORT = 443
        fun getLocalIpByWifi(context: Context?): String? {
            context?.let {
                (it.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.let { wifiManager ->
                    wifiManager.connectionInfo?.ipAddress?.let { ipAddress ->
                        return String.format(
                            "%d.%d.%d.%d",
                            ipAddress and 0xff,
                            ipAddress shr 8 and 0xff,
                            ipAddress shr 16 and 0xff,
                            ipAddress shr 24 and 0xff
                        )
                    }
                }
            }
            return null
        }

        fun getVideoUrl1(context: Context, videoId: String?): String {
            return getVideoUrl(getLocalIpByWifi(context), videoId)
        }
        fun getVideoUrl(videoId: String?, videoId1: String?): String {
            val serverAddress = SERVER_ADDRESS
            val serverPort = HTTPS_SERVER_PORT
            return "https://$serverAddress:$serverPort/video?id=$videoId"
        }

        fun getAudioUrl(ip: String?, audioId: String?): String {
            return "https://${ip}:${HTTPS_SERVER_PORT}${NanoHttpServer.SESSION_URI_AUDIO}?id=${audioId}"
        }

        fun getImageUrl(ip: String?, imageId: String?): String {
            return "https://${ip}:${HTTPS_SERVER_PORT}${NanoHttpServer.SESSION_URI_IMAGE}?id=${imageId}"
        }
    }
    inner class LocalBinder : Binder() {
        fun getService(): HttpServerService = this@HttpServerService
    }

    private val binder = LocalBinder()

    private var mNanoHttpServer: NanoHttpServer? = null

    override fun onBind(intent: Intent?): IBinder? {
        return binder // Obtain the media file sharing url by binding the service
    }

    override fun onCreate() {
        super.onCreate()
        showForeground()

        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }


        val privateKeyBytes = resources.openRawResource(R.raw.terafort_com).readBytes()
        val pemParser = PEMParser(InputStreamReader(ByteArrayInputStream(privateKeyBytes)))
        val pemObject = pemParser.readObject()

        if (pemObject is PEMKeyPair) {
            val keyConverter = JcaPEMKeyConverter().setProvider("BC")
            val keyPair = keyConverter.getKeyPair(pemObject)

            // Load the certificate chain from terafort_com.p7b
            val certificateBytes = resources.openRawResource(R.raw.terafort_com2).readBytes()
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val certificateInputStream = ByteArrayInputStream(certificateBytes)
            val certificateChain: Collection<Certificate> =
                certificateFactory.generateCertificates(certificateInputStream)

            // Create a KeyStore and set the private key and certificate chain
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null)
            keyStore.setKeyEntry("key", keyPair.private, null, certificateChain.toTypedArray())

            // Initialize the SSLContext with the KeyManagerFactory
            val keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keyManagerFactory.init(keyStore, null)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(keyManagerFactory.keyManagers, null, null)

            // Update the NanoHttpServer to use the HTTPS_SERVER_PORT and SERVER_ADDRESS

            mNanoHttpServer = NanoHttpServer(this, HTTPS_SERVER_PORT, sslContext, "localhost").also {
                it.start()
            }
        } else {
            // Show a Toast if loading the private key fails
            Toast.makeText(applicationContext, "not working", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showForeground() {
        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        // 点击回到应用的intent
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.setPackage(null)
            ?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle("HTTP SERVER")
            .setContentText("投屏服务")
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .notification
        startForeground(6666, notification)
    }
    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)

        mNanoHttpServer?.let {
            it.stop()
            it.release()
        }
    }
}