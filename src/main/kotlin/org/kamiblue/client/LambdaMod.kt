package org.kamiblue.client

import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.kamiblue.client.event.ForgeEventProcessor
import org.kamiblue.client.gui.mc.KamiGuiUpdateNotification
import org.kamiblue.client.util.ConfigUtils
import org.kamiblue.client.util.threads.BackgroundScope
import java.io.File

@Mod(
    modid = LambdaMod.ID,
    name = LambdaMod.NAME,
    version = LambdaMod.VERSION
)
class LambdaMod {

    companion object {
        const val NAME = "Lambda"
        const val ID = "lambda"
        const val DIRECTORY = "lambda/"

        const val VERSION = "2.04.xx-dev" // Used for debugging. R.MM.DD-hash format.
        const val VERSION_SIMPLE = "ht-v9.9-dev" // Shown to the user. R.MM.DD[-beta] format.
        const val VERSION_MAJOR = "2.04.01" // Used for update checking. RR.MM.01 format.

        const val APP_ID = "835368493150502923"

        const val DOWNLOADS_API = "https://kamiblue.org/api/v1/downloads.json" // needs to be changed when domain is registered
        const val CAPES_JSON = "https://raw.githubusercontent.com/lambda-client/cape-api/capes/capes.json"
        const val GITHUB_LINK = "https://github.com/lambda-client/"
        const val WEBSITE_LINK = "https://lambda-client.com"

        const val LAMBDA = "λ"

        val LOG: Logger = LogManager.getLogger(NAME)

        var ready: Boolean = false; private set
    }

    @Suppress("UNUSED_PARAMETER")
    @Mod.EventHandler
    fun preInit(event: FMLPreInitializationEvent) {
        val directory = File(DIRECTORY)
        if (!directory.exists()) directory.mkdir()

        KamiGuiUpdateNotification.updateCheck()
        LoaderWrapper.preLoadAll()
    }

    @Suppress("UNUSED_PARAMETER")
    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        LOG.info("Initializing $NAME $VERSION")

        LoaderWrapper.loadAll()

        MinecraftForge.EVENT_BUS.register(ForgeEventProcessor)

        ConfigUtils.moveAllLegacyConfigs()
        ConfigUtils.loadAll()

        BackgroundScope.start()

        LOG.info("$NAME initialized!")
    }

    @Suppress("UNUSED_PARAMETER")
    @Mod.EventHandler
    fun postInit(event: FMLPostInitializationEvent) {
        ready = true
    }
}