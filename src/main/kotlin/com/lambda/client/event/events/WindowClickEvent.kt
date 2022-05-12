package com.lambda.client.event.events

import com.lambda.client.event.Cancellable
import com.lambda.client.event.Event
import com.lambda.client.event.ICancellable
import net.minecraft.inventory.ClickType

class WindowClickEvent(val windowId: Int, val slotId: Int, val mouseButton: Int, val type: ClickType) : Event, ICancellable by Cancellable()