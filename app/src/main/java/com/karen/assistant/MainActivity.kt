package com.karen.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView
    private lateinit var tts: TextToSpeech
    private lateinit var voice: VoiceInput
    private var captureAction = ACTION_SCREENSHOT
    private var pendingVoice = false
    private var overlayEnabled = false
    private var lastQuestion = ""
    private val modelPreparationCancelled = java.util.concurrent.atomic.AtomicBoolean(false)
    private val silenceReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { if (::tts.isInitialized) tts.stop(); AudioAnswers.stop(); ClonedVoice.stop() }
    }
    private val videoVoiceImport: androidx.activity.result.ActivityResultLauncher<Array<String>> = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) cloneDialog().import(uri)
    }
    private val galleryVideoImport: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest> = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cloneDialog().import(uri)
    }
    private val audioImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = EditText(this).apply { hint = "Название голосового ответа"; setTextColor(0xFF501522.toInt()) }
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Добавить аудиоответ").setView(name)
                .setPositiveButton("Импортировать") { _, _ ->
                    val label = name.text.toString().trim().ifBlank { "Мой голос" }
                    status.text = "Импортирую аудио…"
                    Thread({
                        try {
                            AudioAnswers(this).import(uri, label)
                            runOnUiThread { status.text = "Аудиоответ добавлен. Выберите его в «Мои команды» → «Мой аудиоответ»." }
                        } catch (_: Exception) { runOnUiThread { status.text = "Не удалось импортировать аудио. Максимум 15 МБ." } }
                    }, "karen-audio-import").start()
                }.setNegativeButton("Отмена", null).show()
        }
    }
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (pendingVoice && micGranted()) { pendingVoice = false; startVoice() }
        else if (pendingVoice) { pendingVoice = false; status.text = "Разрешите микрофон, чтобы говорить с Карен" }
    }
    private val capture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK && it.data != null) {
            ContextCompat.startForegroundService(this, Intent(this, CaptureService::class.java)
                .setAction(captureAction).putExtra("code", it.resultCode).putExtra("data", it.data))
            status.text = if (captureAction == ACTION_RECORD) "Запись началась. Остановить можно здесь или в уведомлении" else "Сохраняю скриншот…"
        } else status.text = "Захват экрана отменён"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        status = findViewById(R.id.statusText)
        prepareBundledModels()
        tts = TextToSpeech(this, this)
        ContextCompat.registerReceiver(this, silenceReceiver, android.content.IntentFilter(VoiceInput.CLAIM_MIC), ContextCompat.RECEIVER_NOT_EXPORTED)
        voice = VoiceInput(this, { active, message ->
            status.text = message
            findViewById<Button>(R.id.voiceButton).text = if (active) "■  ВЫКЛЮЧИТЬ МИКРОФОН" else "●  ГОВОРИТЬ С КАРЕН"
        }, ::execute)
        findViewById<Button>(R.id.voiceButton).setOnClickListener { startVoice() }
        findViewById<Button>(R.id.overlayButton).setOnClickListener { toggleOverlay() }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.sendButton).setOnClickListener {
            val input = findViewById<EditText>(R.id.commandInput)
            if (input.text.isNotBlank()) { execute(input.text.toString()); input.text.clear() }
        }
        findViewById<Button>(R.id.appsButton).setOnClickListener { chooseApp() }
        val assistantSettings = AssistantSettings(this, tts)
        findViewById<Button>(R.id.customButton).setOnClickListener { voice.cancel(); assistantSettings.commands() }
        findViewById<Button>(R.id.ownerVoiceButton).setOnClickListener {
            voice.cancel(); tts.stop(); AudioAnswers.stop()
            if (!micGranted()) { requestMic(); status.text = "Разрешите микрофон и нажмите «Только мой голос» ещё раз"; return@setOnClickListener }
            OwnerVoiceDialog(this).show()
        }
        findViewById<Button>(R.id.importVoiceButton).setOnClickListener { voice.cancel(); tts.stop(); ClonedVoice.stop(); cloneDialog().show() }
        findViewById<Button>(R.id.audioAnswerButton).setOnClickListener { voice.cancel(); tts.stop(); ClonedVoice.stop(); audioImport.launch(arrayOf("audio/*")) }
        findViewById<Button>(R.id.voiceStyleButton).setOnClickListener { voice.cancel(); assistantSettings.voices() }
        findViewById<Button>(R.id.scanButton).setOnClickListener { voice.cancel(); assistantSettings.allApps() }
        findViewById<Button>(R.id.playButton).setOnClickListener {
            val input = EditText(this).apply { hint = "Название приложения"; setTextColor(0xFF501522.toInt()) }
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Найти в Google Play").setView(input)
                .setPositiveButton("Найти") { _, _ -> execute("скачай " + input.text.toString()) }.setNegativeButton("Назад", null).show()
        }
        findViewById<Button>(R.id.searchAnswerButton).setOnClickListener {
            if (lastQuestion.isBlank()) { status.text = "Сначала произнесите вопрос или введите его"; return@setOnClickListener }
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=" + android.net.Uri.encode(lastQuestion))))
        }
        findViewById<Button>(R.id.homeButton).setOnClickListener { execute("сверни приложение") }
        findViewById<Button>(R.id.timeButton).setOnClickListener { execute("сколько времени") }
        findViewById<Button>(R.id.shotButton).setOnClickListener { requestCapture(ACTION_SCREENSHOT) }
        findViewById<Button>(R.id.recordButton).setOnClickListener { requestCapture(ACTION_RECORD) }
        findViewById<Button>(R.id.stopRecordButton).setOnClickListener { startService(Intent(this, CaptureService::class.java).setAction(CaptureService.STOP)); status.text = "Останавливаю запись" }
        val audio = getSystemService(android.media.AudioManager::class.java)
        configureSlider(R.id.volumeSlider, audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) * 100 / audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)) { execute("громкость $it") }
        configureSlider(R.id.brightnessSlider, Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) * 100 / 255) { execute("яркость $it") }
        handleIntent(intent)
    }
    private fun configureSlider(id: Int, initial: Int, apply: (Int) -> Unit) {
        findViewById<SeekBar>(id).apply {
            progress = initial
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) {}
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar) { apply(bar.progress) }
            })
        }
    }
    private fun chooseApp() {
        val apps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Открыть приложение").setItems(apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()) { _, index ->
            val app = apps[index]
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(app.activityInfo.packageName, app.activityInfo.name))
        }.setNegativeButton("Назад", null).show()
    }
    private fun micGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun requestMic() {
        val items = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= 33) items.add(Manifest.permission.POST_NOTIFICATIONS)
        permissions.launch(items.toTypedArray())
    }
    private fun startVoice() {
        if (!micGranted()) { pendingVoice = true; requestMic(); return }
        tts.stop()
        voice.toggle()
    }
    private fun toggleOverlay() {
        if (overlayEnabled) { stopService(Intent(this, OverlayService::class.java)); overlayEnabled = false; findViewById<Button>(R.id.overlayButton).text = "ВКЛЮЧИТЬ ПЛАВАЮЩУЮ КНОПКУ"; return }
        if (!micGranted()) { requestMic(); status.text = "Разрешите микрофон и нажмите кнопку ещё раз"; return }
        if (!Settings.canDrawOverlays(this)) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))); status.text = "Включите показ поверх приложений, вернитесь и нажмите кнопку"; return }
        voice.cancel()
        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
        overlayEnabled = true
        findViewById<Button>(R.id.overlayButton).text = "ВЫКЛЮЧИТЬ ПЛАВАЮЩУЮ КНОПКУ"
        status.text = "Кнопка активна: касание — микрофон, удержание — открыть Карен"
    }
    private fun execute(text: String) {
        voice.cancel()
        ClonedVoice.stop(); AudioAnswers.stop()
        lastQuestion = text
        status.text = "Вы: $text"
        val customAction = CustomCommands(this).resolve(text)
        if (customAction?.startsWith("аудио:") == true) {
            tts.stop()
            AudioAnswers(this).play(customAction.substringAfter(':')) { status.text = "Не удалось воспроизвести аудиоответ"; }
            status.text = "Вы: $text\nКарен: аудиоответ"
            return
        }
        CommandEngine(this) { message -> status.text = "Вы: $text\nКарен: $message"; say(message) }.run(text)
    }
    private fun requestCapture(action: String) {
        voice.cancel(); captureAction = action
        capture.launch((getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent())
    }
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                voice.cancel(); tts.stop(); ClonedVoice.stop()
                val uri = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                    else @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri)
                if (uri?.scheme == "content") cloneDialog().import(uri)
                else status.text = "Передайте само видео из Telegram через «Поделиться», не ссылку на сообщение"
            }
            ACTION_SCREENSHOT, ACTION_RECORD -> requestCapture(intent.action!!)
            ACTION_COMMAND -> intent.getStringExtra("command")?.let { execute(it) }
        }
        intent.action = null
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }
    private fun cloneDialog(): CloneVoiceDialog = CloneVoiceDialog(this, { if (!isDestroyed) status.text = it },
        { videoVoiceImport.launch(arrayOf("video/*", "audio/*")) },
        { galleryVideoImport.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) })
    private fun prepareBundledModels() {
        val info = findViewById<TextView>(R.id.modelStatusText)
        if (CloneModels.ready(this)) { info.text = "Модель голоса встроена и готова. Интернет не нужен"; return }
        info.text = "Подготавливаю встроенную модель… 0%"
        Thread({
            try {
                CloneModels.prepare(applicationContext, modelPreparationCancelled) { percent -> runOnUiThread {
                    if (!isDestroyed) info.text = "Подготавливаю встроенную модель… $percent%"
                } }
                runOnUiThread { if (!isDestroyed) info.text = "Модель голоса встроена и готова. Интернет не нужен" }
            } catch (e: Exception) { runOnUiThread { if (!isDestroyed) info.text = "Подготовка модели: ${e.message}. Повторить можно в «Голос из видео»" } }
        }, "karen-bundled-models").start()
    }
    private fun say(text: String) {
        val cloned = ClonedVoice(applicationContext)
        if (cloned.enabled) { tts.stop(); cloned.speak(text) { if (!isDestroyed) status.text = "$text\n$it" } }
        else { ClonedVoice.stop(); tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "karen") }
    }
    override fun onInit(result: Int) { if (result == TextToSpeech.SUCCESS) VoiceStyle.apply(this, tts) }
    override fun onPause() { voice.cancel(); super.onPause() }
    override fun onDestroy() { modelPreparationCancelled.set(true); voice.destroy(); unregisterReceiver(silenceReceiver); ClonedVoice.stop(); tts.shutdown(); super.onDestroy() }
    companion object {
        const val ACTION_SCREENSHOT = "com.karen.assistant.SCREENSHOT"
        const val ACTION_RECORD = "com.karen.assistant.RECORD"
        const val ACTION_COMMAND = "com.karen.assistant.COMMAND"
    }
}
