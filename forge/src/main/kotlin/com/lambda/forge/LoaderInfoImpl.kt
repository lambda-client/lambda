package com.lambda.forge

import net.minecraftforge.fml.loading.FMLLoader

object LoaderInfoImpl {
    @JvmStatic
    fun getVersion(): String =
        FMLLoader.getLoadingModList().getModFileById("lambda").versionString()
}