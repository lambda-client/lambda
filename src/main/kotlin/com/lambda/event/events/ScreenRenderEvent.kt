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

package com.lambda.event.events

import com.lambda.event.Event

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
