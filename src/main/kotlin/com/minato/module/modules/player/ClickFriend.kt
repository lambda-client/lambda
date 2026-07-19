
package com.minato.module.modules.player

import com.minato.config.settings.complex.Bind
import com.minato.config.settings.complex.KeybindSetting.Companion.onPress
import com.minato.context.SafeContext
import com.minato.interaction.handlers.FriendHandler
import com.minato.interaction.handlers.FriendHandler.befriend
import com.minato.interaction.handlers.FriendHandler.isFriend
import com.minato.interaction.handlers.FriendHandler.unfriend
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.CommunicationUtils.info
import com.minato.util.InputUtils.isSatisfied
import com.minato.util.world.raycast.RayCastUtils.entityResult
import net.minecraft.client.network.OtherClientPlayerEntity
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT

@Suppress("unused")
object ClickFriend : Module(
    name = "ClickFriend",
    description = "Add or remove friends with a single click",
    tag = ModuleTag.PLAYER,
    modulePriority = 100
) {
    private val friendBind: Bind by setting("Friend Bind", Bind(0, 0, GLFW.GLFW_MOUSE_BUTTON_MIDDLE), "Bind to press to befriend a player")
        .onPress { if (!unfriendBind.isSatisfied()) if (checkSetFriend(true)) it.cancel() }

    private val unfriendBind: Bind by setting("Unfriend Bind", Bind(0, GLFW_MOD_SHIFT, GLFW.GLFW_MOUSE_BUTTON_MIDDLE), "Bind to press to unfriend a player")
        .onPress { if (!friendBind.isSatisfied()) if (checkSetFriend(false)) it.cancel() }

    private fun SafeContext.checkSetFriend(friend: Boolean): Boolean {
        val target = mc.crosshairTarget?.entityResult?.entity as? OtherClientPlayerEntity
            ?: return false

        if (friend && !target.isFriend && target.befriend()) info(FriendHandler.befriendedText(target.name))
        else if (!friend && target.isFriend && target.unfriend()) info(FriendHandler.unfriendedText(target.name))
        return true
    }
}
