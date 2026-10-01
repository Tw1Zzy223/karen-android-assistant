package com.karen.assistant

import android.content.*
import android.os.*
import java.io.File

class CloneServiceClient(private val context: Context, private val status: (String) -> Unit, private val result: (File) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var endpoint: Messenger? = null
    private var active = true
    private var bound = false
    private val deadline = PhaseDeadline(SpeechScheduler { delay, task ->
        val runnable = Runnable { task() }; main.postDelayed(runnable, delay)
        CancelTask { main.removeCallbacks(runnable) }
    }) { message -> if (active) { stop(); status(message) } }
    private val receiver = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (!active) return@Handler true
        val value = message.data.getString("value").orEmpty()
        when (message.what) {
            CloneSynthesisService.STATUS -> status(value)
            CloneSynthesisService.PHASE -> {
                val limit = message.data.getLong("limit").coerceIn(30000, 120000)
                deadline.phase(limit + 5000, "$value не завершилась вовремя. Движок остановлен")
                status(value)
            }
            CloneSynthesisService.ERROR -> { stop(); status(value) }
            CloneSynthesisService.RESULT -> {
                if (value.matches(Regex("clone-[a-f0-9-]{36}\\.wav"))) {
                    val file = File(context.cacheDir, value)
                    stop(); if (file.isFile) result(file) else status("Движок не сохранил аудио")
                } else { stop(); status("Движок вернул неверный файл") }
            }
        }
        true
    })
    private var text = ""
    private var sample = ""
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (!active || service == null) return
            endpoint = Messenger(service)
            try {
                endpoint!!.send(Message.obtain(null, CloneSynthesisService.START).apply {
                    replyTo = receiver
                    data = Bundle().apply { putString("text", text); putString("sample", sample) }
                })
            } catch (_: RemoteException) { stop(); status("Не удалось запустить локальный движок") }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            if (active) { stop(); status("Движок завершился или Android остановил его из-за памяти. Выберите системный голос или повторите") }
        }
        override fun onNullBinding(name: ComponentName?) { if (active) { stop(); status("Служба голоса недоступна") } }
        override fun onBindingDied(name: ComponentName?) { onServiceDisconnected(name) }
    }
    fun start(text: String, sample: String) {
        this.text = text; this.sample = sample
        deadline.phase(30000, "Служба голоса не запустилась за 30 секунд. Повторите")
        status("Запускаю локальный движок…")
        try {
            bound = context.bindService(Intent(context, CloneSynthesisService::class.java), connection, Context.BIND_AUTO_CREATE)
            if (!bound) { stop(); status("Android не запустил службу голоса") }
        } catch (e: Exception) { stop(); status("Служба голоса: ${e.message}") }
    }
    fun stop() {
        if (!active) return
        active = false; deadline.finish()
        try { endpoint?.send(Message.obtain(null, CloneSynthesisService.CANCEL)) } catch (_: RemoteException) { }
        endpoint = null
        if (bound) { bound = false; try { context.unbindService(connection) } catch (_: IllegalArgumentException) { } }
    }
}
