package com.lambda.client.command.commands

import com.lambda.client.LambdaMod
import com.lambda.client.command.ClientCommand
import com.lambda.client.module.ModuleManager
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.capitalize
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraftforge.common.ForgeVersion
import org.lwjgl.opengl.GL11

object TroubleshootCommand : ClientCommand(
    name = "troubleshoot",
    alias = arrayOf("tsc"),
    description = "Prints troubleshooting information"
) {
    init {
        execute("Print troubleshooting information") {
            MessageSendHelper.sendErrorMessage("&l&cSend a screenshot of all information below this line!")
            MessageSendHelper.sendChatMessage("Enabled Modules:\n" + ModuleManager.modules.filter { it.isEnabled }.joinToString { it.name })
            MessageSendHelper.sendChatMessage("${LambdaMod.NAME} ${LambdaMod.LAMBDA} ${LambdaMod.VERSION}")
            MessageSendHelper.sendChatMessage("Forge ${ForgeVersion.getMajorVersion()}.${ForgeVersion.getMinorVersion()}.${ForgeVersion.getRevisionVersion()}.${ForgeVersion.getBuildVersion()}")
            MessageSendHelper.sendChatMessage("Operating System: ${System.getProperty("os.name").lowercase().capitalize()} ${System.getProperty("os.version")} ")
            MessageSendHelper.sendChatMessage("JVM: ${System.getProperty("java.version")} ${System.getProperty("java.vendor")}")
            MessageSendHelper.sendChatMessage("GPU: ${GlStateManager.glGetString(GL11.GL_VENDOR)}")
            MessageSendHelper.sendChatMessage("CPU: ${System.getProperty("os.arch")} ${OpenGlHelper.getCpu()}")
            MessageSendHelper.sendErrorMessage("&l&cPlease send a screenshot of the full output to the developer or moderator who's helping you!")
        }
    }
}
