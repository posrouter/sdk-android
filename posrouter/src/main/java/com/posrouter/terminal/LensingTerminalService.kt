package com.posrouter.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.posrouter.LensingContextHolder
import com.posrouter.R
import com.posrouter.core.lensing.LensingProtocolEngine
import com.posrouter.core.lensing.LensingState

/**
 * Keeps Lensing / NATS alive on B-side terminal apps ([POSRouterConfig.terminalMode]).
 */
class LensingTerminalService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var configRetryRunnable: Runnable? = null
    private var configRetryAttempts = 0

    override fun onCreate() {
        super.onCreate()
        running = true
        instance = this
        TerminalNotificationRefresher.bind(this)
        Log.i(TAG, "Terminal service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = LensingContextHolder.config
        if (config == null) {
            Log.w(TAG, "Terminal service started before POSRouter.initialize — retrying")
            scheduleConfigRetry()
            return START_STICKY
        }

        cancelConfigRetry()
        configRetryAttempts = 0
        startInForeground()
        if (config.terminalMode) {
            LensingProtocolEngine.start(config, force = true)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        cancelConfigRetry()
        TerminalNotificationRefresher.unbind(this)
        if (instance === this) {
            instance = null
        }
        running = false
        Log.i(TAG, "Terminal service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun refreshForegroundNotification() {
        if (!running) return
        val notification = buildNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startInForeground() {
        createNotificationChannel()
        refreshForegroundNotification()
    }

    private fun buildNotification(): Notification {
        val config = LensingContextHolder.config
        val terminalId = config?.terminalId.orEmpty()
        val state = LensingProtocolEngine.currentState()
        val statusText = when (state) {
            LensingState.CONNECTED -> getString(R.string.posrouter_terminal_notification_connected)
            LensingState.DISCOVERING, LensingState.CONNECTING, LensingState.RECONNECTING ->
                getString(R.string.posrouter_terminal_notification_connecting)
            LensingState.FAILED ->
                getString(R.string.posrouter_terminal_notification_failed)
            LensingState.IDLE ->
                getString(R.string.posrouter_terminal_notification_idle)
        }
        val launchIntent = config?.terminalLaunchActivityClass?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { className ->
                Intent().setClassName(packageName, className).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.posrouter_ic_terminal_notification)
            .setContentTitle(getString(R.string.posrouter_terminal_notification_title))
            .setContentText(
                if (terminalId.isNotEmpty()) "$terminalId · $statusText" else statusText
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply { contentIntent?.let { setContentIntent(it) } }
            .build()
    }

    private fun scheduleConfigRetry() {
        cancelConfigRetry()
        configRetryAttempts++
        if (configRetryAttempts > MAX_CONFIG_RETRY_ATTEMPTS) {
            Log.e(TAG, "POSRouter.initialize never ran — stopping terminal service")
            stopSelf()
            return
        }
        configRetryRunnable = Runnable {
            val cfg = LensingContextHolder.config
            if (cfg == null) {
                scheduleConfigRetry()
                return@Runnable
            }
            configRetryAttempts = 0
            startInForeground()
            if (cfg.terminalMode) {
                LensingProtocolEngine.start(cfg, force = true)
            }
        }
        mainHandler.postDelayed(configRetryRunnable!!, CONFIG_RETRY_DELAY_MS)
    }

    private fun cancelConfigRetry() {
        configRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        configRetryRunnable = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.posrouter_terminal_notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.posrouter_terminal_notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "POSRouter.TerminalSvc"
        private const val CHANNEL_ID = "posrouter_terminal_lensing_v2"
        private const val NOTIFICATION_ID = 7001
        private const val CONFIG_RETRY_DELAY_MS = 2_000L
        private const val MAX_CONFIG_RETRY_ATTEMPTS = 15

        @Volatile
        var running: Boolean = false
            private set

        @Volatile
        private var instance: LensingTerminalService? = null

        /** Prefer launching from the running FGS — better background start privileges. */
        fun launchTerminalActivity(intent: Intent): Boolean {
            val service = instance ?: return false
            return try {
                service.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "FGS startActivity failed", e)
                false
            }
        }

        fun start(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, LensingTerminalService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start terminal service", e)
            }
        }
    }
}
