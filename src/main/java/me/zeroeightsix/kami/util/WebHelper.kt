package me.zeroeightsix.kami.util

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * @author balusc (StackOverflow ID 157882)
 *
 * https://stackoverflow.com/questions/3584210/preferred-java-way-to-ping-an-http-url-for-availability#3584332
 */
object WebHelper : Runnable {
    var isInternetDown = false

    fun isDown(host: String?, port: Int, timeout: Int): Boolean {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeout)
                return false
            }
        } catch (e: IOException) {
            return true // Either timeout or unreachable or failed DNS lookup.
        }
    }

    override fun run() {
        isInternetDown = isDown("1.1.1.1", 80, 100)
    }
}