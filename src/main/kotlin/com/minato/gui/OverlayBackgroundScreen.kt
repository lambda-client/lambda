
package com.minato.gui

/**
 * Implemented by screens the click GUI can open over that hold resources (e.g. textures)
 * which must survive being temporarily replaced by [MinatoScreen].
 *
 * Opening the GUI calls [net.minecraft.client.MinecraftClient.setScreen], which fires
 * [net.minecraft.client.gui.screen.Screen.removed] on the screen being overlaid — screens
 * that free resources there would lose them even though the GUI restores the screen on close.
 * [onOverlaidByGui] is invoked just before that happens so the screen can retain its resources.
 */
interface OverlayBackgroundScreen {
    fun onOverlaidByGui()
}
