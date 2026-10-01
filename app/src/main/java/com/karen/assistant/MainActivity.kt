package com.karen.assistant

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Surface
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.OutputStream
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView
    private lateinit var tts: TextToSpeech
    private var requestedCapture = ACTION_SCREENSHOT
    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { updateStatus() }
    private val speechRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val phrase = result.data?.getStringArrayListExtra(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        if (phrase == null) say("Я не расслышала") else CommandEngine(this, ::say).run(phrase)
    }
    private val projectionRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) { say("Действие отменено"); return@registerForActivityResult }
        projection = (getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(result.resultCode, result.data!!)
        if (requestedCapture == ACTION_RECORD) startRecording() else takeScreenshot()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.statusText)
        tts = TextToSpeech(this, this)
        findViewById<Button>(R.id.overlayButton).setOnClickListener { enableOverlay() }
        findViewById<Button>(R.id.voiceButton).setOnClickListener { startVoice() }
        requestBasics()
        when (intent.action) { ACTION_SCREENSHOT, ACTION_RECORD -> requestCapture(intent.action!!) }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); if (intent.action == ACTION_SCREENSHOT || intent.action == ACTION_RECORD) requestCapture(intent.action!!) }

    private fun requestBasics() {
        val missing = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO)
            if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isNotEmpty()) permissionRequest.launch(missing.toTypedArray())
        updateStatus()
    }
    private fun updateStatus() { status.text = if (Settings.canDrawOverlays(this)) "Панель готова. Нажмите «Включить панель»." else "Разрешите показ поверх других приложений — Android откроет нужный экран." }
    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))); return }
        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
        status.text = "Карен активна поверх приложений. Перетащите красную кнопку в удобное место."
    }
    private fun startVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestBasics(); return }
        speechRequest.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU").putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT, "Слушаю"))
    }
    private fun requestCapture(action: String) {
        requestedCapture = action
        projectionRequest.launch((getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent())
    }
    private fun takeScreenshot() {
        val metrics = resources.displayMetrics
        val reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, android.graphics.PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection!!.createVirtualDisplay("Karen screenshot", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null)
        reader.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            val width = image.width
            val height = image.height
            val plane = image.planes[0]
            val bitmap = Bitmap.createBitmap(width + (plane.rowStride - plane.pixelStride * width) / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(plane.buffer); image.close()
            saveScreenshot(Bitmap.createBitmap(bitmap, 0, 0, width, height)); bitmap.recycle()
            source.close(); stopProjection()
        }, Handler(mainLooper))
    }
    private fun saveScreenshot(bitmap: Bitmap) {
        val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "Karen_${System.currentTimeMillis()}.png"); put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Karen") }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let { contentResolver.openOutputStream(it)?.use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) } }
        say("Скриншот сохранён в галерее")
    }
    private fun startRecording() {
        val metrics = resources.displayMetrics
        val values = ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, "Karen_${System.currentTimeMillis()}.mp4"); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4"); put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Karen") }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return
        val descriptor = contentResolver.openFileDescriptor(uri, "w") ?: return
        recorder = MediaRecorder().apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); setOutputFile(descriptor.fileDescriptor)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264); setVideoSize(metrics.widthPixels, metrics.heightPixels); setVideoFrameRate(30); setVideoEncodingBitRate(6_000_000); prepare()
        }
        virtualDisplay = projection!!.createVirtualDisplay("Karen recording", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, recorder!!.surface, null, null)
        recorder!!.start()
        status.text = "Идёт запись экрана. Вернитесь в Карен и нажмите кнопку «Сказать Карен» для остановки."
        findViewById<Button>(R.id.voiceButton).text = "ОСТАНОВИТЬ ЗАПИСЬ"
        findViewById<Button>(R.id.voiceButton).setOnClickListener { stopRecording() }
        say("Запись экрана началась")
    }
    private fun stopRecording() {
        try { recorder?.stop() } catch (_: RuntimeException) { Toast.makeText(this, "Запись слишком короткая", Toast.LENGTH_SHORT).show() }
        recorder?.release(); recorder = null; stopProjection(); findViewById<Button>(R.id.voiceButton).text = "СКАЗАТЬ КАРЕН"; findViewById<Button>(R.id.voiceButton).setOnClickListener { startVoice() }; status.text = "Запись сохранена в Галерее / Movies / Karen"; say("Запись сохранена")
    }
    private fun stopProjection() { virtualDisplay?.release(); virtualDisplay = null; projection?.stop(); projection = null }
    private fun say(text: String) { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "karen") }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts.language = Locale("ru", "RU") }
    override fun onDestroy() { if (recorder != null) stopRecording(); tts.shutdown(); super.onDestroy() }
    companion object { const val ACTION_SCREENSHOT = "com.karen.assistant.SCREENSHOT"; const val ACTION_RECORD = "com.karen.assistant.RECORD" }
}
