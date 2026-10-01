package com.karen.assistant

import android.app.*
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.widget.Toast

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var recorder: MediaRecorder? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var output: Uri? = null
    private var finished = false
    private val handler = Handler(Looper.getMainLooper())
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (projection != null) return START_NOT_STICKY
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val recording = intent.action == MainActivity.ACTION_RECORD
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("capture", "Захват экрана", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 12, Intent(this, CaptureService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "capture").setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(if (recording) "Карен записывает экран" else "Карен делает снимок")
            .addAction(android.R.drawable.ic_media_pause, "Остановить", stop).build()
        startForeground(12, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        try {
            projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(intent.getIntExtra("code", Activity.RESULT_OK), data)
            projection!!.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, handler)
            if (recording) record() else screenshot()
        } catch (_: Exception) { Toast.makeText(this, "Не удалось начать захват экрана", Toast.LENGTH_LONG).show(); stopSelf() }
        return START_NOT_STICKY
    }
    private fun screenshot() {
        val m = resources.displayMetrics
        reader = ImageReader.newInstance(m.widthPixels, m.heightPixels, PixelFormat.RGBA_8888, 2)
        reader!!.setOnImageAvailableListener({ source ->
            if (finished) return@setOnImageAvailableListener
            val frame = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            finished = true
            try {
                val plane = frame.planes[0]
                val padded = Bitmap.createBitmap(frame.width + (plane.rowStride - plane.pixelStride * frame.width) / plane.pixelStride, frame.height, Bitmap.Config.ARGB_8888)
                padded.copyPixelsFromBuffer(plane.buffer)
                val cropped = Bitmap.createBitmap(padded, 0, 0, frame.width, frame.height)
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "Karen_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Karen")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("No output")
                contentResolver.openOutputStream(uri)?.use { check(cropped.compress(Bitmap.CompressFormat.PNG, 100, it)) } ?: error("No stream")
                cropped.recycle(); if (!padded.isRecycled) padded.recycle()
                Toast.makeText(this, "Снимок сохранён: Pictures/Karen", Toast.LENGTH_LONG).show()
            } catch (_: Exception) { Toast.makeText(this, "Не удалось сохранить снимок", Toast.LENGTH_LONG).show() }
            finally { frame.close(); stopSelf() }
        }, handler)
        display = projection!!.createVirtualDisplay("Karen screenshot", m.widthPixels, m.heightPixels, m.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler)
        handler.postDelayed({ if (!finished) { Toast.makeText(this, "Экран не передал изображение", Toast.LENGTH_LONG).show(); stopSelf() } }, 10000)
    }
    private fun record() {
        val m = resources.displayMetrics
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Karen_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Karen")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        output = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("No output")
        descriptor = contentResolver.openFileDescriptor(output!!, "w") ?: error("No descriptor")
        val deviceRecorder = MediaRecorder()
        deviceRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        deviceRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        deviceRecorder.setOutputFile(descriptor!!.fileDescriptor)
        deviceRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        val width = (m.widthPixels.coerceAtMost(1080) / 2) * 2
        val height = ((m.heightPixels * width / m.widthPixels) / 2) * 2
        deviceRecorder.setVideoSize(width, height)
        deviceRecorder.setVideoFrameRate(30)
        deviceRecorder.setVideoEncodingBitRate(6000000)
        deviceRecorder.prepare()
        recorder = deviceRecorder
        display = projection!!.createVirtualDisplay("Karen recording", width, height, m.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, deviceRecorder.surface, null, handler)
        deviceRecorder.start()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (recorder != null) {
            try {
                recorder!!.stop()
                output?.let { contentResolver.update(it, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) }
                Toast.makeText(this, "Запись сохранена: Movies/Karen", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                output?.let { contentResolver.delete(it, null, null) }
                Toast.makeText(this, "Запись не сохранена: слишком короткая или ошибка захвата", Toast.LENGTH_LONG).show()
            }
        }
        recorder?.release(); descriptor?.close(); display?.release(); reader?.close(); projection?.stop()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { const val STOP = "com.karen.assistant.STOP_CAPTURE" }
}
