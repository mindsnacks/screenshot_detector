
package com.ss.detect

import android.app.Activity
import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import kotlin.math.log

class FlutterScreenshotDetectPlugin: FlutterPlugin, EventChannel.StreamHandler, ActivityAware {
    private var contentResolver: ContentResolver? = null
    private var eventSink: EventChannel.EventSink? = null
    private var screenshotObserver: ContentObserver? = null
    private lateinit var channel: EventChannel
    private var activity: Activity? = null
    private var screenCaptureCallback: Activity.ScreenCaptureCallback? = null

    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        contentResolver = binding.applicationContext.contentResolver
        channel = EventChannel(binding.binaryMessenger, "com.ss.detect/events")
        channel.setStreamHandler(this)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setStreamHandler(null)
        contentResolver = null
        screenshotObserver = null
        unregisterScreenCaptureCallback()
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        registerScreenCaptureCallback()
    }

    override fun onDetachedFromActivity() {
        unregisterScreenCaptureCallback()
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        registerScreenCaptureCallback()
    }

    override fun onDetachedFromActivityForConfigChanges() {
        unregisterScreenCaptureCallback()
        activity = null
    }

    private fun registerScreenCaptureCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.d("INFO","Android 14 detected")
            screenCaptureCallback = Activity.ScreenCaptureCallback {
                eventSink?.success(mapOf(
                    "method" to "screen_capture_callback",
                    "timestamp" to System.currentTimeMillis()
                ))
            }
            Log.d("INFO","SS CAPTURED")
            activity?.registerScreenCaptureCallback(
                activity!!.mainExecutor,
                screenCaptureCallback!!
            )
        }
    }

    private fun unregisterScreenCaptureCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && screenCaptureCallback != null) {
            activity?.unregisterScreenCaptureCallback(screenCaptureCallback!!)
            screenCaptureCallback = null
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events

        // Set up content observer for older Android versions
        screenshotObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri?.let {
                    Log.d("INFO","SS CAPTURED"+it.path)
                    if (isScreenshotPath(it.path)) {
                        eventSink?.success(mapOf(
                            "method" to "content_observer",
                            "timestamp" to System.currentTimeMillis(),
                            "path" to it.path
                        ))
                    }
                }
            }
        }

        contentResolver?.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            screenshotObserver!!
        )
        // Register screen capture callback for Android 14+
        registerScreenCaptureCallback()
    }

    override fun onCancel(arguments: Any?) {
        contentResolver?.unregisterContentObserver(screenshotObserver!!)
        unregisterScreenCaptureCallback()
        eventSink = null
        screenshotObserver = null
    }

    private fun isScreenshotPath(path: String?): Boolean {
        // The URI delivered to ContentObserver.onChange has a path like
        // "/external/images/media/12345" (no scheme/authority), so comparing
        // against EXTERNAL_CONTENT_URI.toString() ("content://media/external/images/media")
        // always returned false. Instead, fire whenever the change occurs on an
        // external images MediaStore row — that captures screenshots reliably
        // and avoids needing READ_MEDIA_IMAGES to query row data on Android 13+.
        return path?.contains("/external/images/media") == true
    }
}