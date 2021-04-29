package com.lambda.client.mixin.extension

import net.minecraft.client.gui.*
import net.minecraft.client.gui.inventory.GuiEditSign
import net.minecraft.tileentity.TileEntitySign
import net.minecraft.util.text.ITextComponent
import net.minecraft.world.BossInfo
import java.util.*

val GuiBossOverlay.mapBossInfos: Map<UUID, BossInfoClient>? get() = (this as com.lambda.client.mixin.client.accessor.gui.AccessorGuiBossOverlay).mapBossInfos
fun GuiBossOverlay.render(x: Int, y: Int, info: BossInfo) = (this as com.lambda.client.mixin.client.accessor.gui.AccessorGuiBossOverlay).invokeRender(x, y, info)

var GuiChat.historyBuffer: String
    get() = (this as com.lambda.client.mixin.client.accessor.gui.AccessorGuiChat).historyBuffer
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.gui.AccessorGuiChat).historyBuffer = value
    }
var GuiChat.sentHistoryCursor: Int
    get() = (this as com.lambda.client.mixin.client.accessor.gui.AccessorGuiChat).sentHistoryCursor
    set(value) {
        (this as com.lambda.client.mixin.client.accessor.gui.AccessorGuiChat).sentHistoryCursor = value
    }

val GuiDisconnected.parentScreen: GuiScreen get() = (this as com.lambda.client.mixin.client.accessor.gui.AccessorGuiDisconnected).parentScreen
val GuiDisconnected.reason: String get() = (this as com.lambda.client.mixin.client.accessor.gui.AccessorGuiDisconnected).reason
val GuiDisconnected.message: ITextComponent get() = (this as com.lambda.client.mixin.client.accessor.gui.AccessorGuiDisconnected).message

val GuiEditSign.tileSign: TileEntitySign get() = (this as com.lambda.client.mixin.client.accessor.gui.AccessorGuiEditSign).tileSign
val GuiEditSign.editLine: Int get() = (this as com.lambda.client.mixin.client.accessor.gui.AccessorGuiEditSign).editLine