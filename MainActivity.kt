package com.syncbeat.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import android.media.MediaPlayer
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var urlInput: EditText
    private lateinit var pickButton: Button
    private lateinit var hostButton: Button
    private lateinit var connectButton: Button
    private lateinit var broadcastButton: Button
    private lateinit var stopButton: Button

    private var selectedUri: Uri? = null
    private var server: AudioServer? = null
    private var player: MediaPlayer? = null
    private var guestPlayer: MediaPlayer? = null
    private var isGuest = false
    private var lastStartAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.keepScreenOn = true
        buildUi()
    }

    private fun buildUi() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }
        val title = TextView(this).apply {
            text = "SyncBeat"
            textSize = 30f
        }
        status = TextView(this).apply { text = "Create a room or join an existing room."; textSize = 16f }
        details = TextView(this).apply { textSize = 14f }
        pickButton = Button(this).apply {
            text = "1. Choose audio file (Admin)"
            setOnClickListener { chooseAudio() }
        }
        hostButton = Button(this).apply {
            text = "2. Start room (Admin)"
            setOnClickListener { startRoom() }
        }
        broadcastButton = Button(this).apply {
            text = "Broadcast Play"
            isEnabled = false
            setOnClickListener { broadcastPlay() }
        }
        urlInput = EditText(this).apply {
            hint = "Admin address, e.g. http://192.168.43.1:8080"
            setSingleLine(true)
        }
        connectButton = Button(this).apply {
            text = "Join room (Guest)"
            setOnClickListener { connectGuest() }
        }
        stopButton = Button(this).apply {
            text = "Stop playback"
            setOnClickListener {
                player?.stopSafely()
                guestPlayer?.stopSafely()
                status.text = "Playback stopped."
            }
        }
        layout.addView(title)
        layout.addView(status)
        layout.addView(details)
        layout.addView(pickButton)
        layout.addView(hostButton)
        layout.addView(broadcastButton)
        layout.addView(TextView(this).apply { text = "Guest phone"; textSize = 18f })
        layout.addView(urlInput)
        layout.addView(connectButton)
        layout.addView(stopButton)
        layout.addView(TextView(this).apply {
            text = "\nKeep this app open during playback. Connect guest phones to the admin phone's hotspot first."
            textSize = 13f
        })
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun chooseAudio() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        startActivityForResult(intent, 10)
    }

    @Deprecated("Deprecated in Android API, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 10 && resultCode == Activity.RESULT_OK) {
            selectedUri = data?.data
            selectedUri?.let {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                status.text = "Selected: ${it.lastPathSegment ?: "audio file"}"
            }
        }
    }

    private fun startRoom() {
        val uri = selectedUri
        if (uri == null) {
            status.text = "Choose an audio file first."
            return
        }
        try {
            server?.stop()
            server = AudioServer(uri).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
            val addresses = localAddresses()
            val addressText = if (addresses.isEmpty()) "Could not detect hotspot IP. Check Wi-Fi hotspot settings." else addresses.joinToString("\n")
            details.text = "Room server: port 8080\nGuest URL (try the hotspot address):\n$addressText"
            status.text = "Room is running. Connect guest phones to this hotspot."
            broadcastButton.isEnabled = true
            isGuest = false
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                setOnPreparedListener { status.text = "Admin audio ready. Guests can join; then tap Broadcast Play." }
                setOnErrorListener { _, what, extra ->
                    status.text = "Admin player error ($what/$extra). Try an MP3 file."
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            status.text = "Could not start room: ${e.message}"
        }
    }

    private fun broadcastPlay() {
        val target = System.currentTimeMillis() + 5000L
        server?.startAt = target
        lastStartAt = target
        val p = player ?: return
        if (!p.isPlaying) {
            try {
                if (p.currentPosition > 0) p.seekTo(0)
                handler.postDelayed({
                    try { p.start() } catch (_: Exception) {}
                }, 5000L)
            } catch (_: Exception) {}
        }
        status.text = "Broadcast scheduled. Phones that have joined will start in about 5 seconds."
    }

    private fun connectGuest() {
        val base = urlInput.text.toString().trim().trimEnd('/')
        if (!base.startsWith("http://")) {
            status.text = "Enter the admin hotspot URL, including http:// and :8080."
            return
        }
        isGuest = true
        guestPlayer?.release()
        status.text = "Connecting and buffering audio…"
        guestPlayer = MediaPlayer().apply {
            setDataSource("$base/audio")
            setOnPreparedListener {
                status.text = "Connected and ready. Waiting for admin to broadcast."
                pollRoomState(base)
            }
            setOnErrorListener { _, what, extra ->
                status.text = "Connection/playback error ($what/$extra). Check the admin IP and hotspot."
                true
            }
            prepareAsync()
        }
    }

    private fun pollRoomState(base: String) {
        if (!isGuest) return
        io.execute {
            var startAt = 0L
            try {
                val conn = URL("$base/state").openConnection() as HttpURLConnection
                conn.connectTimeout = 2500
                conn.readTimeout = 2500
                startAt = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optLong("startAt")
                conn.disconnect()
            } catch (_: Exception) {}
            if (startAt > lastStartAt && startAt > System.currentTimeMillis()) {
                lastStartAt = startAt
                val delay = (startAt - System.currentTimeMillis()).coerceAtLeast(0L)
                handler.postDelayed({
                    try {
                        guestPlayer?.let { if (!it.isPlaying) it.start() }
                        status.text = "Playing in sync with the room."
                    } catch (_: Exception) {}
                }, delay)
            }
            handler.postDelayed({ pollRoomState(base) }, 400L)
        }
    }

    private fun localAddresses(): List<String> {
        val result = mutableListOf<String>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        result.add("http://${addr.hostAddress}:8080")
                    }
                }
            }
        } catch (_: Exception) {}
        return result.distinct()
    }

    private fun MediaPlayer.stopSafely() {
        try { if (isPlaying) stop() } catch (_: Exception) {}
        try { reset() } catch (_: Exception) {}
    }

    override fun onDestroy() {
        isGuest = false
        handler.removeCallbacksAndMessages(null)
        try { server?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        try { guestPlayer?.release() } catch (_: Exception) {}
        io.shutdownNow()
        super.onDestroy()
    }

    private inner class AudioServer(private val audioUri: Uri) : NanoHTTPD(8080) {
        @Volatile var startAt: Long = 0L

        override fun serve(session: IHTTPSession): Response {
            return when (session.uri) {
                "/state" -> newFixedLengthResponse(
                    Response.Status.OK, "application/json",
                    JSONObject().put("startAt", startAt).toString()
                )
                "/audio" -> {
                    try {
                        val stream = contentResolver.openInputStream(audioUri)
                            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Audio file unavailable")
                        val mime = contentResolver.getType(audioUri) ?: "audio/mpeg"
                        newChunkedResponse(Response.Status.OK, mime, stream)
                    } catch (e: Exception) {
                        newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Unable to stream audio")
                    }
                }
                "/health" -> newFixedLengthResponse(Response.Status.OK, "text/plain", "SyncBeat room ready")
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
            }
        }
    }
}
