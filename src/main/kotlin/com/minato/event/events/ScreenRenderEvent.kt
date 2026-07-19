
package com.minato.event.events

import com.minato.event.Event

/**
 * Event fired after Minecraft's GUI has been fully rendered.
 * 
 * This fires after guiRenderer.render() in GameRenderer, ensuring that
 * any screen-space rendering done in response to this event will appear
 * above all of Minecraft's native GUI elements (hotbar, held items, etc.).
 * 
 * Use this event for screen-space rendering that needs to appear on top of
 * Minecraft's HUD. For world-space (3D) rendering, use RenderEvent.Render.
 */
object ScreenRenderEvent : Event
