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

package com.lambda.friend

import com.lambda.config.Configurable
import com.lambda.config.configurations.FriendConfig
import com.lambda.core.Loadable
import com.lambda.util.text.ClickEvents
import com.lambda.util.text.buildText
import com.lambda.util.text.clickEvent
import com.lambda.util.text.literal
import com.lambda.util.text.styled
import com.lambda.util.text.text
import com.mojang.authlib.GameProfile
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.text.Text
import java.awt.Color
import java.util.*

// ToDo:
//  - Allow adding of offline players by name or uuid.
//  - Should store the data until the player was seen.
//      Either no UUID but with name or no name but with uuid or both.
//      -> Should update the record if the player was seen again.
//  - Handle player changing names.
//  - Improve save file structure.
object FriendManager : Configurable(FriendConfig), Loadable {
    override val name = "friends"
    val friends by setting("friends", emptySet(), setOf<GameProfile>())

    fun befriend(profile: GameProfile) = friends.add(profile)
    fun unfriend(profile: GameProfile): Boolean = friends.remove(profile)

    fun gameProfile(name: String) = friends.firstOrNull { it.name == name }
    fun gameProfile(uuid: UUID) = friends.firstOrNull { it.id == uuid }

    fun isFriend(profile: GameProfile) = friends.contains(profile)
    fun isFriend(name: String) = friends.any { it.name == name }
    fun isFriend(uuid: UUID) = friends.any { it.id == uuid }

    fun clear() = friends.clear()

    val OtherClientPlayerEntity.isFriend: Boolean
        get() = isFriend(gameProfile)

    fun OtherClientPlayerEntity.befriend() = befriend(gameProfile)
    fun OtherClientPlayerEntity.unfriend() = unfriend(gameProfile)

    override fun load() = "Loaded ${friends.size} friends"

    fun befriendedText(name: String): Text = befriendedText(Text.of(name))
    fun befriendedText(name: Text) = buildText {
        literal(Color.GREEN, "Added ")
        text(name)
        literal(" to your friend list ")
        clickEvent(ClickEvents.suggestCommand(";friends remove ${name.string}")) {
            styled(underlined = true, color = Color.LIGHT_GRAY) {
                literal("[Undo]")
            }
        }
    }

    fun unfriendedText(name: String): Text = unfriendedText(Text.of(name))
    fun unfriendedText(name: Text) = buildText {
        literal(Color.RED, "Removed ")
        text(name)
        literal(" from your friend list ")
        clickEvent(ClickEvents.suggestCommand(";friends add ${name.string}")) {
            styled(underlined = true, color = Color.LIGHT_GRAY) {
                literal("[Undo]")
            }
        }
    }
}
