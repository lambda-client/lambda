
package com.minato.event.events

import com.minato.event.Event
import com.minato.event.callback.Cancellable
import com.minato.event.callback.ICancellable

sealed class RenderEvent {
    object PreRenderWorld : Event
    object RenderWorld : Event
    object RenderScreen : Event

    class UpdateTarget : ICancellable by Cancellable()
}
