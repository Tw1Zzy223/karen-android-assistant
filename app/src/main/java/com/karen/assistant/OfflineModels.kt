package com.karen.assistant

import android.content.Context
import org.vosk.Model
import org.vosk.SpeakerModel
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream

object OfflineModels {
    private var speech: Model? = null
    private var speaker: SpeakerModel? = null
    fun installed(context: Context) = File(context.filesDir, "vosk/.ready").exists()
    @Synchronized fun prepare(context: Context, progress: (String) -> Unit) {
        val root = File(context.filesDir, "vosk")
        if (!installed(context)) {
            root.mkdirs()
            val names = listOf("vosk-model-small-ru-0.22", "vosk-model-spk-0.4")
            for ((index, name) in names.withIndex()) {
                progress("Загрузка модели ${index + 1}/2: примерно 58 МБ всего")
                val connection = URL("https://alphacephei.com/vosk/models/$name.zip").openConnection()
                connection.connectTimeout = 15000; connection.readTimeout = 20000
                val archive = File(root, "$name.zip")
                try {
                    connection.getInputStream().use { input -> archive.outputStream().use { input.copyTo(it) } }
                    progress("Распаковка модели ${index + 1}/2…")
                    ZipInputStream(archive.inputStream()).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val target = File(root, entry.name)
                            check(target.canonicalPath.startsWith(root.canonicalPath + File.separator))
                            if (entry.isDirectory) target.mkdirs()
                            else { target.parentFile?.mkdirs(); target.outputStream().use { zip.copyTo(it) } }
                            zip.closeEntry(); entry = zip.nextEntry
                        }
                    }
                } finally { archive.delete() }
            }
            check(File(root, "vosk-model-small-ru-0.22/am/final.mdl").exists())
            check(File(root, "vosk-model-spk-0.4/final.ext.raw").exists())
            File(root, ".ready").writeText("1")
        }
        progress("Загрузка русского распознавания и проверки голоса…")
        if (speech == null) speech = Model(File(root, "vosk-model-small-ru-0.22").absolutePath)
        if (speaker == null) speaker = SpeakerModel(File(root, "vosk-model-spk-0.4").absolutePath)
    }
    @Synchronized fun recognizer(context: Context): org.vosk.Recognizer {
        check(installed(context)) { "Models not installed" }
        prepare(context) {}
        return org.vosk.Recognizer(speech!!, 16000f, speaker!!)
    }
}
