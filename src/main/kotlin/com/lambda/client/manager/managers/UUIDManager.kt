package com.lambda.client.manager.managers

import com.lambda.client.LambdaMod
import com.lambda.client.capeapi.AbstractUUIDManager
import com.lambda.client.capeapi.PlayerProfile
import com.lambda.client.capeapi.UUIDUtils
import com.lambda.client.manager.Manager
import com.lambda.client.util.FolderUtils
import com.lambda.client.util.Wrapper

object UUIDManager : AbstractUUIDManager(FolderUtils.lambdaFolder + "uuid_cache.json", LambdaMod.LOG, maxCacheSize = 1000), Manager {

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
