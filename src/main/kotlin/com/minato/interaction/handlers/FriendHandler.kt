
package com.minato.interaction.handlers

import com.minato.Minato
import com.minato.command.CommandRegistry
import com.minato.command.commands.FriendCommand
import com.minato.config.Config
import com.minato.config.categories.FriendCategory
import com.minato.core.Loadable
import com.minato.network.mojang.getProfile
import com.minato.util.text.ClickEvents
import com.minato.util.text.buildText
import com.minato.util.text.clickEvent
import com.minato.util.text.literal
import com.minato.util.text.styled
import com.minato.util.text.text
import com.mojang.authlib.GameProfile
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text
import java.awt.Color
import java.util.*

object FriendHandler : Config(
    "friends",
	FriendCategory
), Loadable {
    val friends by setting("friends", emptySet<UUID>(), serialize = true)

    private val cachedProfiles = mutableMapOf<UUID, GameProfile>()

    fun befriend(profile: GameProfile): Boolean {
        cachedProfiles[profile.id] = profile
        return befriend(profile.id)
    }

    fun befriend(uuid: UUID) = if (!isFriend(uuid)) friends.add(uuid) else false

    fun unfriend(profile: GameProfile): Boolean {
        cachedProfiles.remove(profile.id)
        return unfriend(profile.id)
    }

    fun unfriend(uuid: UUID): Boolean {
        cachedProfiles.remove(uuid)
        return friends.remove(uuid)
    }

    fun gameProfile(name: String): GameProfile? {
        return onlineProfile(name)
            ?.also { cachedProfiles[it.id] = it }
            ?: cachedProfiles.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun gameProfile(uuid: UUID): GameProfile? {
        return onlineProfile(uuid)
            ?.also { cachedProfiles[it.id] = it }
            ?: cachedProfiles[uuid]
    }

    suspend fun latestGameProfile(name: String): GameProfile? {
        return gameProfile(name)
            ?: getProfile(name)
                .getOrNull()
                ?.also { cachedProfiles[it.id] = it }
    }

    suspend fun latestGameProfile(uuid: UUID): GameProfile? {
        return gameProfile(uuid)
            ?: getProfile(uuid)
                .getOrNull()
                ?.also { cachedProfiles[it.id] = it }
    }

    fun isFriend(profile: GameProfile) = isFriend(profile.id)

    fun isFriend(name: String): Boolean {
        val online = onlineProfile(name) ?: return false
        return isFriend(online.id)
    }

    fun isFriend(uuid: UUID) = friends.contains(uuid)

    fun clear() {
        cachedProfiles.clear()
        friends.clear()
    }

    fun friendDisplayName(uuid: UUID): String = gameProfile(uuid)?.name ?: uuid.toString()

    val PlayerEntity.isFriend: Boolean
        get() = isFriend(gameProfile)

    fun PlayerEntity.befriend() = befriend(gameProfile)
    fun PlayerEntity.unfriend() = unfriend(gameProfile)

    override fun load() = "Loaded ${friends.size} friends"

    fun befriendedText(name: String): Text = befriendedText(Text.of(name))
    fun befriendedText(name: Text) = buildText {
	    literal(Color.GREEN, "Added ")
	    text(name)
	    literal(" to your friend list ")
	    clickEvent(ClickEvents.suggestCommand("${CommandRegistry.prefix}${FriendCommand.name} remove ${name.string}")) {
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
	    clickEvent(ClickEvents.suggestCommand("${CommandRegistry.prefix}${FriendCommand.name} add ${name.string}")) {
		    styled(underlined = true, color = Color.LIGHT_GRAY) {
			    literal("[Undo]")
		    }
	    }
    }

    private fun onlineProfile(name: String): GameProfile? {
        val playerList = Minato.mc.networkHandler?.playerList ?: return null
        return playerList.firstOrNull { it.profile.name.equals(name, ignoreCase = true) }?.profile
    }

    private fun onlineProfile(uuid: UUID): GameProfile? {
        val playerList = Minato.mc.networkHandler?.playerList ?: return null
        return playerList.firstOrNull { it.profile.id == uuid }?.profile
    }
}