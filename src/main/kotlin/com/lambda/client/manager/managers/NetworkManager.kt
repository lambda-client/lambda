package com.lambda.client.manager.managers

import com.lambda.client.manager.Manager
import com.lambda.client.util.TickTimer
import com.lambda.client.util.threads.defaultScope
import com.lambda.event.listener.listener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.net.InetSocketAddress
import java.net.Socket

object NetworkManager : Manager {

    var isOffline = false; private set

    private val lastUpdateTimer = TickTimer()

    init {
        listener<TickEvent.ClientTickEvent> {
            if (lastUpdateTimer.tick(1500L)) {
                defaultScope.launch(Dispatchers.IO) {
                    isOffline = try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress("1.1.1.1", 80), 100)
                            false
                        }
                    } catch (e: Exception) {
                        true // Either timeout or unreachable or failed DNS lookup.
                    }
                }
            }
        }
    }
}