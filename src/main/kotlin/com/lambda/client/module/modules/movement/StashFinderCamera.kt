package com.lambda.client.module.modules.movement

import baritone.api.utils.Helper

import net.minecraft.client.gui.inventory.GuiEditSign
import net.minecraft.util.EnumHand
import net.minecraftforge.fml.common.gameevent.TickEvent
import com.lambda.client.mixin.extension.tileSign
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.TaskState
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener

import net.minecraft.util.text.TextComponentString;
//todo

internal object StashFinderCamera : Module(
    name = "StashFinderCamera",
    category = Category.MISC,
    description = "Holds head if stashfinding",

    ) {


    init {


    }
}