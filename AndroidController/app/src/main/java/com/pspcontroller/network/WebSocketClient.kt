package com.pspcontroller.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** Thin seam over the socket so [ConnectionManager] can be tested with a fake. */
interface Transport {
    interface Listener {
        fun onOpen()
        fun onMessage(text: String)
        /** Called once when the socket closed or failed, with a short human-readable reason. */
        fun onClosed(reason: String)
    }

    fun open(url: String, listener: Listener)
    fun send(text: String): Boolean
    fun close(graceful: Boolean)
}

class OkHttpTransport(private val client: OkHttpClient = defaultClient) : Transport {
    @Volatile private var socket: WebSocket? = null

    override fun open(url: String, listener: Transport.Listener) {
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen()
            override fun onMessage(webSocket: WebSocket, text: String) = listener.onMessage(text)
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                listener.onClosed("closed by receiver ($code)")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onClosed("closed ($code)")
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                listener.onClosed(t.message ?: t.javaClass.simpleName)
        })
    }

    override fun send(text: String): Boolean = socket?.send(text) ?: false

    override fun close(graceful: Boolean) {
        val s = socket ?: return
        socket = null
        if (graceful) s.close(1000, "bye") else s.cancel()
    }

    companion object {
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // liveness is handled by our own heartbeat
            .retryOnConnectionFailure(false)
            .build()
    }
}
