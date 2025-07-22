/*
 * Copyright 2024 Lambda
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

package com.lambda.module.modules.player

import com.lambda.event.events.MouseEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.friend.FriendManager
import com.lambda.friend.FriendManager.befriend
import com.lambda.friend.FriendManager.isFriend
import com.lambda.friend.FriendManager.unfriend
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication.info
import com.lambda.util.Mouse
import com.lambda.util.world.raycast.RayCastUtils.entityResult
import net.minecraft.client.network.OtherClientPlayerEntity
import org.lwjgl.glfw.GLFW.GLFW_MOD_ALT
import org.lwjgl.glfw.GLFW.GLFW_MOD_CAPS_LOCK
import org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL
import org.lwjgl.glfw.GLFW.GLFW_MOD_NUM_LOCK
import org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT
import org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER

object ClickFriend : Module(
    name = "Click Friend",
    description = "Add or remove friends with a single click",
    defaultTags = setOf(ModuleTag.PLAYER)
) {
    private val friendButton by setting("Friend Button", Mouse.Button.Middle, description = "Button to press to befriend a player")
    private val friendAction by setting("Action", Mouse.Action.Release, description = "What mouse action should add or remove the player")
    private val comboUnfriend by setting("Combo Unfriend", false, description = "Press a key and right click a player to unfriend")
    private val modUnfriend by setting("Combo Key", MouseMod.Shift, description = "The key to press to activate the unfriend combo") { comboUnfriend }

    init {
        listen<MouseEvent.Click> {
            if (mc.currentScreen != null) return@listen
            if (it.button != friendButton.ordinal || it.action != friendAction.ordinal) return@listen

            val target = mc.crosshairTarget?.entityResult?.entity as? OtherClientPlayerEntity
                ?: return@listen

            if (!it.hasModifier(modUnfriend.modifiers) && comboUnfriend && target.isFriend) return@listen

            when {
                target.isFriend && target.unfriend() -> info(FriendManager.unfriendedText(target.name))
                !target.isFriend && target.befriend() -> info(FriendManager.befriendedText(target.name))
            }
        }
    }

    private enum class MouseMod(val modifiers: Int) {
        Shift(GLFW_MOD_SHIFT),
        Control(GLFW_MOD_CONTROL),
        Alt(GLFW_MOD_ALT),
        Super(GLFW_MOD_SUPER),
        Caps(GLFW_MOD_CAPS_LOCK),
        NumLock(GLFW_MOD_NUM_LOCK);
    }
}
