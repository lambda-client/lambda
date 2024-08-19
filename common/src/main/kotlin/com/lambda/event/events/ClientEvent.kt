package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import net.minecraft.client.sound.SoundInstance


abstract class ClientEvent : Event {
    class Shutdown : ClientEvent()
    class Startup : ClientEvent()
    class Timer(var speed: Double) : ClientEvent()
    class Sound(val sound: SoundInstance) : ClientEvent(), ICancellable by Cancellable()
}