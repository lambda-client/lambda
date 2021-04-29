package com.lambda.client.manager.managers

import com.lambda.capeapi.AbstractUUIDManager
import com.lambda.capeapi.PlayerProfile
import com.lambda.capeapi.UUIDUtils
import com.lambda.client.manager.Manager
import com.lambda.client.util.Wrapper

object UUIDManager : AbstractUUIDManager(com.lambda.client.LambdaMod.DIRECTORY + "uuid_cache.json", com.lambda.client.LambdaMod.LOG, maxCacheSize = 1000), Manager {

    override fun getOrRequest(nameOrUUID: String): PlayerProfile? {
        return Wrapper.minecraft.connection?.playerInfoMap?.let { playerInfoMap ->
            val infoMap = ArrayList(playerInfoMap)
            val isUUID = UUIDUtils.isUUID(nameOrUUID)
            val withOutDashes = UUIDUtils.removeDashes(nameOrUUID)

            infoMap.find {
                isUUID && UUIDUtils.removeDashes(it.gameProfile.id.toString()).equals(withOutDashes, ignoreCase = true)
                    || !isUUID && it.gameProfile.name.equals(nameOrUUID, ignoreCase = true)
            }?.gameProfile?.let {
                PlayerProfile(it.id, it.name)
            }
        } ?: super.getOrRequest(nameOrUUID)
    }
}
