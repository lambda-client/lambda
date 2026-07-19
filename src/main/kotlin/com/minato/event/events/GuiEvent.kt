
package com.minato.event.events

import com.minato.event.Event
import com.minato.event.callback.Cancellable
import com.minato.event.callback.ICancellable
import net.minecraft.block.entity.SignBlockEntity
import net.minecraft.client.gui.screen.Screen

sealed class GuiEvent {
    /**
     * Triggered when a new ImGui frame is created and the client
     * is allowed to submit any command from this point until [EndImguiFrame].
     */
    data object NewImguiFrame : Event

    /**
     * Triggered when the previous ImGui frame is ended and the client
     * is able to perform OpenGL calls.
     *
     * By default, the game's framebuffer is bound.
     */
    data object EndImguiFrame : Event

    /**
     * Triggered when the sign editor GUI is opened. Can be canceled.
     */
    data class SignEditorOpen(
        var sign: SignBlockEntity,
        var front: Boolean
    ) : ICancellable by Cancellable()

    data class ScreenOpen(val screen: Screen?) : ICancellable by Cancellable()
}
