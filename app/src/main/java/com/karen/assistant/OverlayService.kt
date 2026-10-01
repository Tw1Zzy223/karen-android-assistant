package com.karen.assistant

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.ImageButton
import android.widget.Toast
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var manager: WindowManager
    private lateinit var button: ImageButton
    private lateinit var voice: VoiceInput
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("karen", "Карен", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, OverlayService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "karen").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Карен рядом").setContentText("Микрофон включается только по касанию")
            .setContentIntent(open).addAction(android.R.drawable.ic_menu_close_clear_cancel, "Выключить", stop).build()
        if (android.os.Build.VERSION.SDK_INT >= 30) startForeground(11, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(11, notification)
        manager = getSystemService(WindowManager::class.java)
        button = ImageButton(this).apply {
            setImageResource(R.drawable.avatar_spider); setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(0, 0, 0, 0); contentDescription = "Карен: включить или выключить микрофон"
            setOnClickListener { voice.toggle() }
            setOnLongClickListener { voice.cancel(); startActivity(Intent(this@OverlayService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
        }
        voice = VoiceInput(this, { active, message ->
            button.alpha = if (active) 0.6f else 1f
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }, { command ->
            // Execute in a visible Activity so Android's background-launch rules are respected.
            startActivity(Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_COMMAND)
                .putExtra("command", command).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        val params = WindowManager.LayoutParams(64.dp, 64.dp, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT; x = 16.dp; y = 180.dp }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; dragging = false; false }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - downX) + abs(event.rawY - downY) > 12.dp) dragging = true
                    if (dragging) { params.x = startX + (event.rawX - downX).toInt(); params.y = startY + (event.rawY - downY).toInt(); manager.updateViewLayout(button, params) }
                    dragging
                }
                MotionEvent.ACTION_UP -> dragging
                else -> false
            }
        }
        manager.addView(button, params)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { if (intent?.action == "stop") stopSelf(); return START_NOT_STICKY }
    override fun onDestroy() { voice.destroy(); manager.removeView(button); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
