package com.lambda.client.mixin.extension

import com.lambda.mixin.accessor.player.AccessorPlayerControllerMP
import net.minecraft.client.multiplayer.PlayerControllerMP

var PlayerControllerMP.blockHitDelay: Int
    get() = (this as AccessorPlayerControllerMP).blockHitDelay
    set(value) {
        (this as AccessorPlayerControllerMP).blockHitDelay = value
    }

val PlayerControllerMP.currentPlayerItem: Int get() = (this as AccessorPlayerControllerMP).currentPlayerItem

fun PlayerControllerMP.syncCurrentPlayItem() = (this as AccessorPlayerControllerMP).synchronizeCurrentPlayItem()