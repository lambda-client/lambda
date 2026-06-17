/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.gui

/**
 * Implemented by screens the click GUI can open over that hold resources (e.g. textures)
 * which must survive being temporarily replaced by [LambdaScreen].
 *
 * Opening the GUI calls [net.minecraft.client.MinecraftClient.setScreen], which fires
 * [net.minecraft.client.gui.screen.Screen.removed] on the screen being overlaid — screens
 * that free resources there would lose them even though the GUI restores the screen on close.
 * [onOverlaidByGui] is invoked just before that happens so the screen can retain its resources.
 */
interface OverlayBackgroundScreen {
    fun onOverlaidByGui()
}
