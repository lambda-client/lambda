package com.lambda.quilt

import org.quiltmc.loader.api.QuiltLoader


object LoaderInfoImpl {
    @JvmStatic
    fun getVersion(): String =
        QuiltLoader
            .getModContainer("lambda").orElseThrow()
            .metadata().version().raw()
}
