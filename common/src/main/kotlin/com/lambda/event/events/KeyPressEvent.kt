package com.lambda.event.events

import com.lambda.event.EventFlow
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable

/**
 * A class representing a [KeyPressEvent] in the event system ([EventFlow]).
 *
 * A [KeyPressEvent] is a type of event that is triggered when a key is pressed.
 * It implements [ICancellable] interface, which means the event can be cancelled.
 *
 * @property key The key code of the key that was pressed.
 */
class KeyPressEvent(val key: Int) : ICancellable by Cancellable()