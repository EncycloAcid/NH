package com.nh.hoopscoach

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The PC side of a phone-to-PC transfer: listens on the home network while the transfer screen is open,
 * hands out the current career and takes one in, both only with the code it shows.
 */
interface TransferHost {
    /** Starts listening; returns the addresses (ip:port) a phone on the same network can reach. */
    fun start(code: String, outgoing: () -> ByteArray?, incoming: (ByteArray) -> Unit): List<String>
    fun stop()
    val running: Boolean
}

/**
 * Moving a career between a phone and a PC over the home network. The PC runs a [TransferHost] (set by
 * the desktop app); the phone sends to it or fetches from it with the address and code the PC shows.
 */
object SaveTransfer {
    const val PORT = 47800
    const val MAX_BYTES = 64 * 1024 * 1024

    /** Set on PC at startup; null on a phone. */
    var host: TransferHost? = null

    fun newCode(): String = (1000 + java.util.Random().nextInt(9000)).toString()

    private fun url(address: String, code: String): URL {
        val a = address.trim().removePrefix("http://").removeSuffix("/")
        val withPort = if (':' in a) a else "$a:$PORT"
        return URL("http://$withPort/save?code=${code.trim()}")
    }

    private fun failure(conn: HttpURLConnection, code: Int): Exception = Exception(
        when (code) {
            403 -> "Wrong code. Check the code on the PC."
            404 -> "The PC has no career open to send."
            413 -> "That save is too big to send."
            else -> "The PC answered with an error ($code)."
        },
    )

    /** Phone to PC: sends [bytes]. */
    fun send(address: String, code: String, bytes: ByteArray): Result<Unit> = runCatching {
        val conn = (url(address, code).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6000
            readTimeout = 60000
            doOutput = true
            setFixedLengthStreamingMode(bytes.size)
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        try {
            conn.outputStream.use { it.write(bytes) }
            val status = conn.responseCode
            if (status != 200) throw failure(conn, status)
        } finally {
            conn.disconnect()
        }
    }.recoverCatching { e -> throw friendly(e) }

    /** PC to phone: fetches the career open on the PC. */
    fun fetch(address: String, code: String): Result<ByteArray> = runCatching {
        val conn = (url(address, code).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 60000
        }
        try {
            val status = conn.responseCode
            if (status != 200) throw failure(conn, status)
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) throw Exception("That save is too big to receive.")
                }
            }
            out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }.recoverCatching { e -> throw friendly(e) }

    private fun friendly(e: Throwable): Throwable = when (e) {
        is java.net.ConnectException, is java.net.SocketTimeoutException, is java.net.NoRouteToHostException ->
            Exception("Couldn't reach the PC. Check the address, that both are on the same Wi-Fi, and that the PC's transfer screen is open.")
        is java.net.UnknownHostException, is java.net.MalformedURLException -> Exception("That address doesn't look right. Type it exactly as the PC shows it.")
        else -> e
    }
}
