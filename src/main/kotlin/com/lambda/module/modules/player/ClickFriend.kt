/*
 * Copyright 2025 Lambda
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

import com.lambda.config.settings.complex.Bind
import com.lambda.config.settings.complex.KeybindSetting.Companion.onPress
import com.lambda.context.SafeContext
import com.lambda.friend.FriendManager
import com.lambda.friend.FriendManager.befriend
import com.lambda.friend.FriendManager.isFriend
import com.lambda.friend.FriendManager.unfriend
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication.info
import com.lambda.util.InputUtils.isSatisfied
import com.lambda.util.world.raycast.RayCastUtils.entityResult
import net.minecraft.client.network.OtherClientPlayerEntity
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT

object ClickFriend : Module(
    name = "ClickFriend",
    description = "Add or remove friends with a single click",
    tag = ModuleTag.PLAYER,
) {
    private val friendBind: Bind by setting("Friend Bind", Bind(0, 0, GLFW.GLFW_MOUSE_BUTTON_MIDDLE), "Bind to press to befriend a player")
        .onPress { if (!unfriendBind.isSatisfied()) if (checkSetFriend(true)) it.cancel() }

    private val unfriendBind: Bind by setting("Unfriend Bind", Bind(0, GLFW_MOD_SHIFT, GLFW.GLFW_MOUSE_BUTTON_MIDDLE), "Bind to press to unfriend a player")
        .onPress { if (!friendBind.isSatisfied()) if (checkSetFriend(false)) it.cancel() }

	init {
		setModulePriority(100)
	}

    private fun SafeContext.checkSetFriend(friend: Boolean): Boolean {
        val target = mc.crosshairTarget?.entityResult?.entity as? OtherClientPlayerEntity
            ?: return false

        if (friend && !target.isFriend && target.befriend()) info(FriendManager.befriendedText(target.name))
        else if (!friend && target.isFriend && target.unfriend()) info(FriendManager.unfriendedText(target.name))
        return true
    }
}
