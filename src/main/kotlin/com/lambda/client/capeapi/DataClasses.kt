package com.lambda.client.capeapi

import com.google.gson.annotations.SerializedName
import java.util.*

data class CapeUser(
    val id: Long,
    val capes: ArrayList<Cape>,
    @SerializedName("is_premium")
    var isPremium: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        return this === other
            || other is CapeUser
            && other.id == this.id
            && other.capes == capes
    }

    override fun hashCode(): Int {
        return 31 * id.hashCode() + capes.hashCode()
    }
}

data class Cape(
    @SerializedName("cape_uuid")
    val capeUUID: String = UUID.randomUUID().toString().substring(0, 5),
    @SerializedName("player_uuid")
    var playerUUID: UUID? = null,
    val type: CapeType,
    var color: CapeColor = type.color
) {
    override fun equals(other: Any?): Boolean {
        return this === other
            || other is Cape
            && other.capeUUID == capeUUID
            && other.type == type
    }

    override fun hashCode(): Int {
        return 31 * capeUUID.hashCode() + type.hashCode()
    }
}

data class CapeColor(
    val primary: String,
    val border: String
) {
    override fun toString(): String {
        return "#$primary, #$border"
    }
}

data class PlayerProfile(
    @SerializedName("uuid", alternate = ["UUID"])
    val uuid: UUID,
    @SerializedName("name", alternate = ["Name"])
    val name: String
) {
    override fun equals(other: Any?): Boolean {
        return this === other
            || other is PlayerProfile
            && other.uuid == uuid
    }

    override fun hashCode(): Int {
        return uuid.hashCode()
    }
}

enum class CapeType(val realName: String, val imageKey: String, val color: CapeColor) {
    CONTRIBUTOR("Contributor", "github", CapeColor("272727", "363636"))
}

