
package com.minato.module.modules.render

import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import java.awt.Color

object ExtraTab : Module(
	name = "ExtraTab",
	description = "Adds more tabs to the main menu",
	tag = ModuleTag.RENDER,
) {
	@JvmStatic val tabEntries by setting("Tab Entries", 80L, 1L..500L, 1L)
	@JvmStatic val rows by setting("Rows", 20, 1..100, 1)
	@JvmStatic val friendsOnly by setting("Friends Only", false)
	@JvmStatic val sortFriendsFirst by setting("Sort Friends First", true)
	@JvmStatic val highlightFriends by setting("Highlight Friends", true)
	@JvmStatic val friendColor by setting("Friend Color", Color(120, 120, 255, 255), "The color friends will be highlighted") { highlightFriends }
}
