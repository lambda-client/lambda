
package com.minato.event.events

import com.minato.event.Event
import net.minecraft.client.gui.DrawContext

/**
 * Event fired during HUD rendering with access to Minecraft's DrawContext.
 * Use this for rendering items, textures, and other GUI elements that need
 * to integrate with Minecraft's deferred GUI rendering system.
 */
class HudRenderEvent(val context: DrawContext) : Event
