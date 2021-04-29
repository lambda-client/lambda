package com.lambda.client

import com.lambda.client.event.ForgeEventProcessor
import com.lambda.client.gui.mc.KamiGuiUpdateNotification
import com.lambda.client.util.ConfigUtils
import com.lambda.client.util.threads.BackgroundScope
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File

@Mod(
    modid = com.lambda.client.LambdaMod.Companion.ID,
    name = com.lambda.client.LambdaMod.Companion.NAME,
    version = com.lambda.client.LambdaMod.Companion.VERSION
)
class LambdaMod {

    companion object {
        const val NAME = "Lambda"
        const val ID = "lambda"
        const val DIRECTORY = "lambda/"

        const val VERSION = "2.04.xx-dev" // Used for debugging. R.MM.DD-hash format.
        const val VERSION_SIMPLE = "2.04.xx-dev" // Shown to the user. R.MM.DD[-beta] format.
        const val VERSION_MAJOR = "2.04.01" // Used for update checking. RR.MM.01 format.

        const val APP_ID = "835368493150502923"

        const val DOWNLOADS_API = "https://kamiblue.org/api/v1/downloads.json" // needs to be changed when domain is registered
        const val CAPES_JSON = "https://raw.githubusercontent.com/lambda-client/cape-api/capes/capes.json"
        const val GITHUB_LINK = "https://github.com/lambda-client/"
        const val WEBSITE_LINK = "https://lambda-client.com"

        const val LAMBDA = "λ"

        val LOG: Logger = LogManager.getLogger(com.lambda.client.LambdaMod.Companion.NAME)

        var ready: Boolean = false; private set
    }

    @Suppress("UNUSED_PARAMETER")
    @Mod.EventHandler
    fun preInit(event: FMLPreInitializationEvent) {
        val directory = File(com.lambda.client.LambdaMod.Companion.DIRECTORY)
        if (!directory.exists()) directory.mkdir()

        KamiGuiUpdateNotification.updateCheck()
        com.lambda.client.LoaderWrapper.preLoadAll()
    }

    @Suppress("UNUSED_PARAMETER")
    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        com.lambda.client.LambdaMod.Companion.LOG.info("Initializing ${com.lambda.client.LambdaMod.Companion.NAME} ${com.lambda.client.LambdaMod.Companion.VERSION}")

        com.lambda.client.LoaderWrapper.loadAll()

        MinecraftForge.EVENT_BUS.register(ForgeEventProcessor)

        ConfigUtils.moveAllLegacyConfigs()
        ConfigUtils.loadAll()

        BackgroundScope.start()

        com.lambda.client.LambdaMod.Companion.LOG.info("${com.lambda.client.LambdaMod.Companion.NAME} initialized!")
    }

    @Suppress("UNUSED_PARAMETER")
    @Mod.EventHandler
    fun postInit(event: FMLPostInitializationEvent) {
        com.lambda.client.LambdaMod.Companion.ready = true
    }
}