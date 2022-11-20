package com.lambda.client.capeapi

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.lambda.client.commons.extension.synchronized
import com.lambda.client.commons.utils.ConnectionUtils
import org.apache.logging.log4j.Logger
import java.io.File
import java.io.FileWriter
import java.util.*

abstract class AbstractUUIDManager(
    filePath: String,
    private val logger: Logger,
    private val maxCacheSize: Int = 500
) {

    private val file = File(filePath)

    private val parser = JsonParser()
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val type = TypeToken.getArray(PlayerProfile::class.java).type

    private val nameProfileMap = LinkedHashMap<String, PlayerProfile>().synchronized()
    private val uuidNameMap = LinkedHashMap<UUID, PlayerProfile>().synchronized()

    fun getByString(stringIn: String?) = stringIn?.let { string ->
        UUIDUtils.fixUUID(string)?.let { getByUUID(it) } ?: getByName(string)
    }

    fun getByUUID(uuid: UUID?) = uuid?.let {
        uuidNameMap.getOrPut(uuid) {
            getOrRequest(uuid.toString())?.also { profile ->
                // If UUID already present in nameUuidMap but not in uuidNameMap (user changed name)
                nameProfileMap[profile.name]?.let { uuidNameMap.remove(it.uuid) }
                nameProfileMap[profile.name] = profile
            } ?: return null
        }.also {
            trimMaps()
        }
    }

    fun getByName(name: String?) = name?.let {
        nameProfileMap.getOrPut(name.lowercase()) {
            getOrRequest(name)?.also { profile ->
                // If UUID already present in uuidNameMap but not in nameUuidMap (user changed name)
                uuidNameMap[profile.uuid]?.let { nameProfileMap.remove(it.name) }
                uuidNameMap[profile.uuid] = profile
            } ?: return null
        }.also {
            trimMaps()
        }
    }

    private fun trimMaps() {
        while (nameProfileMap.size > maxCacheSize) {
            nameProfileMap.remove(nameProfileMap.keys.first())?.also {
                uuidNameMap.remove(it.uuid)
            }
        }
    }

    /**
     * Overwrites this if you want to get UUID from other source
     * eg. online player in game client
     */
    protected open fun getOrRequest(nameOrUUID: String): PlayerProfile? {
        return requestProfile(nameOrUUID)
    }

    private fun requestProfile(nameOrUUID: String): PlayerProfile? {
        val isUUID = UUIDUtils.isUUID(nameOrUUID)
        val response = if (isUUID) requestProfileFromUUID(nameOrUUID) else requestProfileFromName(nameOrUUID)

        return if (response.isNullOrBlank()) {
            // If there is no player with the given username or UUID an HTTP status code 204 (No Content) is sent without any HTTP body.
            null
        } else {
            try {
                val jsonElement = parser.parse(response)
                if (isUUID) {
                    val name = jsonElement.asJsonObject["name"].asString
                    PlayerProfile(UUID.fromString(nameOrUUID), name)
                } else {
                    val id = jsonElement.asJsonObject["id"].asString
                    val name = jsonElement.asJsonObject["name"].asString
                    PlayerProfile(UUIDUtils.fixUUID(id)!!, name) // let it throw a NPE if failed to parse the string to UUID
                }
            } catch (e: Exception) {
                logger.error("Failed parsing profile", e)
                null
            }
        }
    }

    private fun requestProfileFromUUID(uuid: String): String? {
        return request("https://sessionserver.mojang.com/session/minecraft/profile/${UUIDUtils.removeDashes(uuid)}")
    }

    private fun requestProfileFromName(name: String): String? {
        return request("https://api.mojang.com/users/profiles/minecraft/$name")
    }

    private fun request(url: String): String? {
        return ConnectionUtils.requestRawJsonFrom(url) {
            logger.error("Failed requesting from Mojang API", it)
        }
    }

    fun load(): Boolean {
        fixEmptyJson(file)
        return try {
            val cacheList = file.bufferedReader().use {
                gson.fromJson<Array<PlayerProfile>>(it, type)
            }
            uuidNameMap.clear()
            nameProfileMap.clear()
            uuidNameMap.putAll(cacheList.associateBy { it.uuid })
            nameProfileMap.putAll(cacheList.associateBy { it.name.lowercase() })
            logger.info("UUID cache loaded")
            true
        } catch (e: Exception) {
            logger.warn("Failed loading UUID cache", e)
            false
        }
    }

    fun save(): Boolean {
        return try {
            val cacheList = uuidNameMap.values.sortedBy { it.name }
            file.bufferedWriter().use {
                gson.toJson(cacheList, it)
            }
            logger.info("UUID cache saved")
            true
        } catch (e: Exception) {
            logger.warn("Failed saving UUID cache", e)
            false
        }
    }

    private fun fixEmptyJson(file: File) {
        if (!file.exists()) file.createNewFile()
        var notEmpty = false
        file.forEachLine { notEmpty = notEmpty || it.trim().isNotBlank() || it == "[]" || it == "{}" }

        if (!notEmpty) {
            val fileWriter = FileWriter(file)
            try {
                fileWriter.write("[]")
            } catch (e: Exception) {
                logger.error("Failed to fix empty json", e)
            } finally {
                fileWriter.close()
            }
        }
    }

}
