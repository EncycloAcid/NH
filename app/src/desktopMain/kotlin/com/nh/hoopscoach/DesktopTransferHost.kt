package com.nh.hoopscoach

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/**
 * The PC's end of a phone transfer: a tiny HTTP server on [SaveTransfer.PORT], up only while the
 * transfer screen is open. GET /save?code=… hands out the current career; POST takes one in. Anything
 * without the right code is turned away. Uses only java.base, so the packaged app needs nothing extra.
 */
class DesktopTransferHost : TransferHost {
    @Volatile private var server: ServerSocket? = null
    override val running get() = server?.isClosed == false

    override fun start(code: String, outgoing: () -> ByteArray?, incoming: (ByteArray) -> Unit): List<String> {
        stop()
        val s = ServerSocket(SaveTransfer.PORT)
        server = s
        Thread({
            while (!s.isClosed) {
                val socket = runCatching { s.accept() }.getOrNull() ?: break
                Thread({ runCatching { socket.use { handle(it, code, outgoing, incoming) } } }, "transfer-client").apply { isDaemon = true }.start()
            }
        }, "transfer-host").apply { isDaemon = true }.start()
        return addresses().map { "$it:${SaveTransfer.PORT}" }
    }

    override fun stop() {
        runCatching { server?.close() }
        server = null
    }

    /** This PC's addresses on the local network (Wi-Fi or Ethernet), private ones first. */
    private fun addresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .sortedByDescending { it.isSiteLocalAddress }
            .map { it.hostAddress }
            .distinct()
    }.getOrDefault(emptyList()).ifEmpty { listOf("this PC's IP address") }

    private fun readLine(input: InputStream): String {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0 || b == '\n'.code) break
            if (b != '\r'.code) out.write(b)
            if (out.size() > 8192) break
        }
        return out.toString(Charsets.ISO_8859_1.name())
    }

    private fun handle(socket: Socket, code: String, outgoing: () -> ByteArray?, incoming: (ByteArray) -> Unit) {
        socket.soTimeout = 60_000
        val input = BufferedInputStream(socket.getInputStream())
        val out = socket.getOutputStream()
        fun reply(status: Int, text: String, body: ByteArray = text.toByteArray(), type: String = "text/plain") {
            out.write("HTTP/1.1 $status $text\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            out.write(body)
            out.flush()
        }
        val request = readLine(input).split(' ')
        if (request.size < 2) return reply(400, "Bad Request")
        var length = 0
        while (true) {
            val h = readLine(input)
            if (h.isEmpty()) break
            if (h.startsWith("Content-Length:", ignoreCase = true)) length = h.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val (method, target) = request[0] to request[1]
        if (target.substringBefore('?') != "/save") return reply(404, "Not Found")
        val given = target.substringAfter("code=", "").substringBefore('&')
        if (given != code) return reply(403, "Forbidden")
        when (method) {
            "GET" -> {
                val bytes = outgoing() ?: return reply(404, "Not Found")
                reply(200, "OK", bytes, "application/octet-stream")
            }
            "POST" -> {
                if (length <= 0 || length > SaveTransfer.MAX_BYTES) return reply(413, "Payload Too Large")
                val body = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = input.read(body, read, length - read)
                    if (n < 0) break
                    read += n
                }
                if (read < length) return reply(400, "Bad Request")
                incoming(body)
                reply(200, "OK")
            }
            else -> reply(405, "Method Not Allowed")
        }
    }
}
