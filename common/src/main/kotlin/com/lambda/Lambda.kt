package com.lambda

import com.lambda.event.EventFlow
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.Listener.Companion.unsubscribe
import com.lambda.event.listener.SafeListener.Companion.concurrentListener
import com.lambda.event.listener.UnsafeListener.Companion.unsafeConcurrentListener
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.lang.Thread.sleep

object Lambda {
    private const val MOD_NAME = "Lambda"
    const val MOD_ID = "lambda"
    const val SYMBOL = "λ"
    private val VERSION: String = LoaderInfo.getVersion()

    val LOG: Logger = LogManager.getLogger()
    val mc: MinecraftClient = MinecraftClient.getInstance()

    init {
        listener<PacketEvent.Send.Pre> {
            if (it.packet is LookAndOnGround) {
                it.cancel()
                LOG.info("SAFE: Canceled: ${it.packet::class.simpleName}")
            }
        }

        runConcurrent {
            sleep(60000)
            LOG.info("Unsubscribing")
            unsubscribe<PacketEvent.Send.Pre>()
        }
    }

    fun initialize() {
        LOG.info("Initializing $MOD_NAME $VERSION")
    }
}
