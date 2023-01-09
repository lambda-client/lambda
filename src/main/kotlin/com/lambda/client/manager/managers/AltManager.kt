package com.lambda.client.manager.managers

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.lambda.client.LambdaMod
import com.lambda.client.manager.Manager
import com.lambda.mixin.accessor.AccessorMinecraft
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.minecraft.util.Session
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

object AltManager : Manager {
    const val BUTTON_ID = 26

    private val cache = File(LambdaMod.DIRECTORY, "accounts.json")
    private val client = HttpClients.createDefault()
    private val type = object : TypeToken<MutableSet<Account>>() {}.type // JVM jank, read TypeToken javadocs
    private val scopes = BasicNameValuePair("scope", "XboxLive.signin offline_access")
    private val clientId = BasicNameValuePair("client_id", "810b4a0d-7663-4e28-8680-24458240dee4") // TBD
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val mtx = Mutex()

    @Volatile
    private var cancel = false

    suspend fun login(cacheUsername: String?, deviceCodeCallback: ((String, String) -> Unit)?, onCompleted: suspend (String) -> Unit) {
        mtx.withLock {
            cancel = false
            val cacheObj = getAccounts()
            cacheObj.firstOrNull { it.name == cacheUsername }?.let {
                try {
                    setSession(getProfile(it.accessToken), it.accessToken)
                    onCompleted(it.name)
                    return@withLock
                } catch (_: IOException) {
                    // Goes to bottom
                }
            }

            actuallyLogin(cacheUsername, deviceCodeCallback, cacheObj, onCompleted)
        }
    }

    fun getAccounts(): MutableSet<Account> {
        cache.parentFile.mkdirs()
        cache.createNewFile()

        return FileReader(cache).use { gson.fromJson(it, type) } ?: mutableSetOf()
    }

    fun cancel() {
        cancel = true
    }

    private fun setSession(profile: Profile, token: String) {
        (mc as AccessorMinecraft).setSession(Session(profile.name, profile.id, token, Session.Type.MOJANG.name))
    }

    private suspend fun actuallyLogin(
        cacheUsername: String?,
        deviceCodeCallback: ((String, String) -> Unit)?,
        cacheObj: MutableSet<Account>,
        onCompleted: suspend (String) -> Unit
    ) {
        try {
            val result = oauthOrRefresh(cacheUsername, deviceCodeCallback) ?: return
            xblAuth(result.accessToken) { xblResp ->
                val body = gson.fromJson(EntityUtils.toString(xblResp.entity), JsonObject::class.java)
                val xblToken = body["Token"].asString
                val userHash = body["DisplayClaims"]
                    .asJsonObject["xui"]
                    .asJsonArray[0]
                    .asJsonObject["uhs"]
                    .asString
                val xstsToken = xstsAuth(xblToken)
                val mcToken = minecraftAuth(xstsToken, userHash) ?: return@xblAuth
                val profile = getProfile(mcToken)
                setSession(profile, mcToken)
                val acct = result.account
                acct.name = profile.name
                acct.accessToken = mcToken
                cacheObj.add(acct)

                FileWriter(cache).use {
                    gson.toJson(cacheObj, it)
                }
                onCompleted(profile.name)
            }
        } catch (e: IOException) {
            LambdaMod.LOG.error("Failed to login", e)
        }
    }

    private suspend fun oauthOrRefresh(cacheUsername: String?, deviceCodeCallback: ((String, String) -> Unit)?): MicrosoftTokenData? {
        if (cache.exists()) {
            getAccounts().firstOrNull { it.name == cacheUsername }?.let { account ->
                val req = HttpPost("https://login.microsoftonline.com/consumers/oauth2/v2.0/token").apply {
                    setHeader("Content-Type", "application/x-www-form-urlencoded")
                    entity = UrlEncodedFormEntity(
                        listOf(
                            clientId,
                            scopes,
                            BasicNameValuePair("refresh_token", account.refreshToken),
                            BasicNameValuePair("grant_type", "refresh_token")
                        )
                    )
                }

                return client.execute(req).use { response ->
                    val body = gson.fromJson(EntityUtils.toString(response.entity), JsonObject::class.java)
                    return@use MicrosoftTokenData(body["refresh_token"].asString.also { account.refreshToken = it }, account)
                }
            }
        }

        return oauth(deviceCodeCallback!!)
    }

    private suspend inline fun oauth(deviceCodeCallback: (String, String) -> Unit): MicrosoftTokenData? {
        val req = HttpPost("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode").apply {
            setHeader("Content-Type", "application/x-www-form-urlencoded")
            entity = UrlEncodedFormEntity(listOf(clientId, scopes))
        }

        return client.execute(req).use { httpResp ->
            val resp = gson.fromJson(EntityUtils.toString(httpResp.entity), JsonObject::class.java)
            val interval = resp["interval"].asInt
            val ourCode = resp["device_code"].asString

            deviceCodeCallback(resp["user_code"].asString, resp["verification_uri"].asString)

            while (!cancel) {
                delay(interval * 1000L)
                val pollReq = HttpPost("https://login.microsoftonline.com/consumers/oauth2/v2.0/token").apply {
                    setHeader("Content-Type", "application/x-www-form-urlencoded")
                    entity = UrlEncodedFormEntity(
                        listOf(
                            clientId,
                            BasicNameValuePair("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                            BasicNameValuePair("device_code", ourCode)
                        )
                    )
                }
                return@use client.execute(pollReq).use nested@ { pollHttpResp ->
                    val pollResp = gson.fromJson(EntityUtils.toString(pollHttpResp.entity), JsonObject::class.java)
                    val error = pollResp["error"]
                    return@nested if (error == null) {
                        MicrosoftTokenData(pollResp["access_token"].asString, Account(pollResp["refresh_token"].asString))
                    } else {
                        val errorStr = error.asString
                        if (errorStr != "authorization_pending") {
                            cancel = false
                            throw IOException("Failed to poll for device code with error: $errorStr")
                        }
                        return@nested null
                    }
                } ?: continue
            }
            cancel = false
            return@use null
        }
    }

    private inline fun xblAuth(token: String, handler: (CloseableHttpResponse) -> Unit) {
        val inner = JsonObject().apply {
            addProperty("AuthMethod", "RPS")
            addProperty("SiteName", "user.auth.xboxlive.com")
            addProperty("RpsTicket", "d=$token")
        }

        val body = JsonObject().apply {
            add("Properties", inner)
            addProperty("RelyingParty", "http://auth.xboxlive.com")
            addProperty("TokenType", "JWT")
        }

        val req = HttpPost("https://user.auth.xboxlive.com/user/authenticate").apply {
            entity = StringEntity(body.toString())
            setHeader("Content-Type", "application/json")
            setHeader("Accept", "application/json")
        }

        client.execute(req).use(handler)
    }

    private fun xstsAuth(token: String): String {
        val tokenArray = JsonArray().apply { add(token) }
        val inner = JsonObject().apply {
            addProperty("SandboxId", "RETAIL")
            add("UserTokens", tokenArray)
        }

        val body = JsonObject().apply {
            add("Properties", inner)
            addProperty("RelyingParty", "rp://api.minecraftservices.com/")
            addProperty("TokenType", "JWT")
        }

        val req = HttpPost("https://xsts.auth.xboxlive.com/xsts/authorize").apply {
            entity = StringEntity(body.toString())
            setHeader("Content-Type", "application/json")
            setHeader("Accept", "application/json")
        }

        return client.execute(req).use {
            val respBody = gson.fromJson(EntityUtils.toString(it.entity), JsonObject::class.java)
            if (it.statusLine.statusCode == 401) {
                throw IOException("XSTS returned 401 with XErr: " + respBody["XErr"].asLong)
            }

            return@use respBody["Token"].asString
        }
    }

    private fun minecraftAuth(token: String, userHash: String): String? {
        val body = JsonObject().apply { addProperty("identityToken", "XBL3.0 x=$userHash;$token") }
        val req = HttpPost("https://api.minecraftservices.com/authentication/login_with_xbox").apply {
            entity = StringEntity(body.toString())
            setHeader("Content-Type", "application/json")
            setHeader("Accept", "application/json")
        }

        return client.execute(req).use {
            if (it.statusLine.statusCode == 200) {
                return@use gson.fromJson(EntityUtils.toString(it.entity), JsonObject::class.java)["access_token"].asString
            }

            return@use null
        }
    }

    private fun getProfile(token: String): Profile {
        val req = HttpGet("https://api.minecraftservices.com/minecraft/profile").apply {
            setHeader("Authorization", "Bearer $token")
        }

        return client.execute(req).use {
            val status = it.statusLine.statusCode
            if (status == 200) {
                return@use gson.fromJson(EntityUtils.toString(it.entity), Profile::class.java)
            }

            LambdaMod.LOG.error("Failed to get alt profile: $status")
            throw IOException()
        }
    }

    class Account(var refreshToken: String = "") {
        lateinit var name: String
        lateinit var accessToken: String

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || javaClass != other.javaClass) {
                return false
            }

            return name == (other as Account).name
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }
    }

    class MicrosoftTokenData(
        val accessToken: String,
        val account: Account
    )

    class Profile {
        lateinit var name: String
        lateinit var id: String
    }
}