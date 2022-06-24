package com.lambda.client.manager.managers

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.lambda.client.LambdaMod
import com.lambda.client.capeapi.PlayerProfile
import com.lambda.client.commons.extension.synchronized
import com.lambda.client.manager.Manager
import com.lambda.client.util.ConfigUtils
import com.lambda.client.util.FolderUtils
import java.io.File
import java.io.FileReader
import java.io.FileWriter

object FriendManager : Manager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file = File(FolderUtils.lambdaFolder + "friends.json")

    private var friendFile = FriendFile()
    val friends = HashMap<String, PlayerProfile>().synchronized()

    val empty get() = friends.isEmpty()
    var enabled = friendFile.enabled
        set(value) {
            field = value
            friendFile.enabled = value
        }

    fun isFriend(name: String) = friendFile.enabled && friends.contains(name.lowercase())

    fun addFriend(name: String) = UUIDManager.getByName(name)?.let {
        friendFile.friends.add(it)
        friends[it.name.lowercase()] = it
        true
    } ?: false

    fun removeFriend(name: String) = friendFile.friends.remove(friends.remove(name.lowercase()))

    fun clearFriend() {
        friends.clear()
        friendFile.friends.clear()
    }

    fun loadFriends(): Boolean {
        ConfigUtils.fixEmptyJson(file)

        return try {
            friendFile = gson.fromJson(FileReader(file), object : TypeToken<FriendFile>() {}.type)
            friends.clear()
            friends.putAll(friendFile.friends.associateBy { it.name.lowercase() })
            LambdaMod.LOG.info("Friend loaded")
            true
        } catch (e: Exception) {
            LambdaMod.LOG.warn("Failed loading friends", e)
            false
        }
    }

    fun saveFriends(): Boolean {
        return try {
            FileWriter(file, false).buffered().use {
                gson.toJson(friendFile, it)
            }
            LambdaMod.LOG.info("Friends saved")
            true
        } catch (e: Exception) {
            LambdaMod.LOG.warn("Failed saving friends", e)
            false
        }
    }

    data class FriendFile(
        @SerializedName("Enabled")
        var enabled: Boolean = true,

        @SerializedName("Friends")
        val friends: MutableSet<PlayerProfile> = LinkedHashSet<PlayerProfile>().synchronized()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FriendFile) return false

            if (enabled != other.enabled) return false
            if (friends != other.friends) return false

            return true
        }

        override fun hashCode(): Int {
            var result = enabled.hashCode()
            result = 31 * result + friends.hashCode()
            return result
        }
    }
}