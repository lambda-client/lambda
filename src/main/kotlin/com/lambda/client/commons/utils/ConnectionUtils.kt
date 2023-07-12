package com.lambda.client.commons.utils

import com.lambda.client.module.modules.client.Plugins
import java.net.HttpURLConnection
import java.net.URL

object ConnectionUtils {

    fun requestRawJsonFrom(url: String, catch: (Exception) -> Unit = { it.printStackTrace() }): String? {
        return runConnection(url, { connection ->
            connection.setRequestProperty("User-Agent", "LambdaClient")
            connection.setRequestProperty("Connection", "close")
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            if (Plugins.token.isNotBlank()) connection.setRequestProperty("Authorization", "token ${Plugins.token}")
            connection.requestMethod = "GET"
            connection.inputStream.readBytes().toString(Charsets.UTF_8)
        }, catch)
    }

    fun <T> runConnection(url: String, block: (HttpURLConnection) -> T?, catch: (Exception) -> Unit = { it.printStackTrace() }): T? {
        (URL(url).openConnection() as HttpURLConnection).run {
            return try {
                doOutput = true
                doInput = true
                block(this)
            } catch (e: Exception) {
                catch(e)
                null
            } finally {
                disconnect()
            }
        }
    }
}