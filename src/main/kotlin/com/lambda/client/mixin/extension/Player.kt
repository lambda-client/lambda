package com.lambda.client.mixin.extension

import net.minecraft.client.multiplayer.PlayerControllerMP

var PlayerControllerMP.blockHitDelay: Int
    get() = (this as com.lambda.client.mixin.client.accessor.player.AccessorPlayerControllerMP).blockHitDelay
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.player.AccessorPlayerControllerMP).blockHitDelay = value
    }

val PlayerControllerMP.currentPlayerItem: Int get() = (this as com.lambda.client.mixin.client.accessor.player.AccessorPlayerControllerMP).currentPlayerItem

fun PlayerControllerMP.syncCurrentPlayItem() = (this as com.lambda.client.mixin.client.accessor.player.AccessorPlayerControllerMP).kb_invokeSyncCurrentPlayItem()