package me.zeroeightsix.kami.event.events

class RenderShaderEvent(val phase: Phase) {
    enum class Phase {
        PRE, POST
    }
}