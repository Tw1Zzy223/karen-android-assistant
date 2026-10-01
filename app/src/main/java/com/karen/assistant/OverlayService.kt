package com.karen.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import java.util.Locale

class OverlayService : Service(), TextToSpeech.OnInitListener {
    private lateinit var windowManager: WindowManager
    private lateinit var button: ImageButton
    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        createChannel()
        val notification = android.app.Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Карен активна")
            .setContentText("Нажмите плавающую кнопку, чтобы говорить").build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(11, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(11, notification)
        showOverlay()
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        button = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setBackgroundResource(com.karen.assistant.R.drawable.overlay_bg)
            contentDescription = "Говорить с Карен"
            setOnClickListener { listen() }
        }
        val params = WindowManager.LayoutParams(72.dp, 72.dp,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = 18.dp; y = 180.dp }
        var downX = 0; var downY = 0; var startX = 0; var startY = 0
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX.toInt(); downY = event.rawY.toInt(); startX = params.x; startY = params.y; false }
                MotionEvent.ACTION_MOVE -> { params.x = startX + (downX - event.rawX.toInt()); params.y = startY + (event.rawY.toInt() - downY); windowManager.updateViewLayout(button, params); true }
                else -> false
            }
        }
        windowManager.addView(button, params)
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { say("Распознавание речи недоступно"); return }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: android.os.Bundle) { results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { CommandEngine(this@OverlayService, ::say).run(it) } }
                override fun onError(error: Int) { Toast.makeText(this@OverlayService, "Не удалось расслышать. Нажмите ещё раз.", Toast.LENGTH_SHORT).show() }
                override fun onReadyForSpeech(p: android.os.Bundle?) = say("Слушаю")
                override fun onBeginningOfSpeech() {} ; override fun onRmsChanged(v: Float) {} ; override fun onBufferReceived(b: ByteArray?) {} ; override fun onEndOfSpeech() {} ; override fun onPartialResults(p: android.os.Bundle?) {} ; override fun onEvent(t: Int, p: android.os.Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU").putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
        }
    }
    private fun say(text: String) { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "karen") }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts.language = Locale("ru", "RU") }
    override fun onDestroy() { recognizer?.destroy(); tts.shutdown(); if (::button.isInitialized) windowManager.removeView(button); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun createChannel() { if (Build.VERSION.SDK_INT >= 26) (getSystemService(NotificationManager::class.java)).createNotificationChannel(NotificationChannel(CHANNEL, "Карен", NotificationManager.IMPORTANCE_LOW)) }
    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
    companion object { const val CHANNEL = "karen_overlay" }
}
