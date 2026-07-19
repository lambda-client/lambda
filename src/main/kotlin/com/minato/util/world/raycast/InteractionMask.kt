
package com.minato.util.world.raycast

enum class InteractionMask(val block: Boolean, val entity: Boolean) {
    Both(true, true),
    Block(true, false),
    Entity(false, true)
}
