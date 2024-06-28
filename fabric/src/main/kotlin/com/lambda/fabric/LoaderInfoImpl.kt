package com.lambda.fabric

import net.fabricmc.loader.api.FabricLoader

object LoaderInfoImpl {
    @JvmStatic
    fun getVersion(): String =
        FabricLoader.getInstance()
            .getModContainer("lambda").orElseThrow()
            .metadata.version.friendlyString

    @JvmStatic
    fun isDevelopment(): Boolean =
        FabricLoader.getInstance().isDevelopmentEnvironment
}
