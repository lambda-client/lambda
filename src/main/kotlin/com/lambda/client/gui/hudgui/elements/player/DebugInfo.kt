package com.lambda.client.gui.hudgui.elements.player

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.util.MovementUtils.realSpeed
import java.util.*

internal object DebugInfo : LabelHud(
    name = "DebugInfo",
    category = Category.PLAYER,
    description = "Displays debug info"
) {
    override fun SafeClientEvent.updateText() {
        displayText.addLine("Movement", secondaryColor)
        displayText.add("On Ground: ", primaryColor)
        displayText.addLine(player.onGround.toString().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, secondaryColor)
        displayText.add("Velocity: ", primaryColor)
        displayText.addLine("X " + String.format("%.5f", player.motionX) + ", Y " + String.format("%.5f", player.motionY) + ", Z " + String.format("%.5f", player.motionZ), secondaryColor)
        displayText.add("BP/s: ", primaryColor)
        displayText.addLine(String.format("%.3f", (player.realSpeed * 20)), secondaryColor) //I believe this is how you calculate bp/s speed, i might be wrong
        displayText.addLine("")

        displayText.addLine("Player Capabilities", secondaryColor)
        if (player.capabilities != null) {
            displayText.add("Flying: ", primaryColor)
            displayText.addLine(player.capabilities.isFlying.toString().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, secondaryColor)
            displayText.add("In Water: ", primaryColor)
            displayText.addLine(player.isInWater.toString().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, secondaryColor)
            displayText.addLine("")
        }

        displayText.addLine("World Info", secondaryColor)
        displayText.add("Gamemode: ", primaryColor)
        displayText.addLine(playerController.currentGameType.name.lowercase(Locale.getDefault()).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, secondaryColor)
        displayText.add("Difficulty: ", primaryColor)
        displayText.addLine(world.worldInfo.difficulty.name.lowercase(Locale.getDefault()).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, secondaryColor)
        if (player.bedLocation != null) { //TODO: Make this save just like it does with waypoints
            displayText.add("Bed Location: ", primaryColor)
            displayText.addLine("X " + player.bedLocation.x.toString() + ", Y " + player.bedLocation.y.toString()+ ", Z " + player.bedLocation.z.toString(), secondaryColor)
        }
    }
}