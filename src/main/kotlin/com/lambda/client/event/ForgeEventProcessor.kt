package com.lambda.client.event

import com.lambda.client.command.CommandManager
import com.lambda.client.event.events.*
import com.lambda.client.gui.mc.LambdaGuiChat
import com.lambda.client.manager.managers.CombatManager
import com.lambda.client.module.ModuleManager
import com.lambda.client.util.Wrapper
import com.lambda.client.util.graphics.LambdaTessellator
import com.lambda.client.util.graphics.ProjectionUtils
import com.lambda.client.util.text.MessageDetection
import net.minecraft.item.ItemFood
import net.minecraftforge.client.event.*
import net.minecraftforge.event.ForgeEventFactory
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.event.world.BlockEvent
import net.minecraftforge.event.world.ExplosionEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse


internal object ForgeEventProcessor {
    private val mc = Wrapper.minecraft
    private var prevWidth = mc.displayWidth
    private var prevHeight = mc.displayHeight
    private var prevFoodLevel = -1

    @SubscribeEvent
    fun onTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.START) {
            mc.profiler.startSection("kbTickPre")
        } else {
            mc.profiler.startSection("kbTickPost")
        }

        LambdaEventBus.postProfiler(event)

        if (event.phase == TickEvent.Phase.END && (prevWidth != mc.displayWidth || prevHeight != mc.displayHeight)) {
            prevWidth = mc.displayWidth
            prevHeight = mc.displayHeight
            LambdaEventBus.post(ResolutionUpdateEvent(mc.displayWidth, mc.displayHeight))
        }

        mc.profiler.endSection()
    }

    @SubscribeEvent
    fun onPlayerTick(event: PlayerTickEvent) {
        if (event.player.foodStats.foodLevel != prevFoodLevel) {
            if (prevFoodLevel == -1) {
                prevFoodLevel = event.player.foodStats.foodLevel
            } else {
                prevFoodLevel = event.player.foodStats.foodLevel
                val result = PlayerEvent.OnEatFinish(event.player)
                LambdaEventBus.post(result)
                prevFoodLevel = event.player.foodStats.foodLevel
            }
        }
    }

    @SubscribeEvent
    @Suppress("UNUSED_PARAMETER")
    fun onWorldRender(event: RenderWorldLastEvent) {
        ProjectionUtils.updateMatrix()
        LambdaTessellator.prepareGL()
        LambdaEventBus.post(com.lambda.client.event.events.WorldEvent.RenderTickEvent())
        LambdaTessellator.releaseGL()
    }

    @SubscribeEvent
    fun onRenderPre(event: RenderGameOverlayEvent.Pre) {
        LambdaEventBus.post(event)
    }

    @SubscribeEvent
    fun onRender(event: RenderGameOverlayEvent.Post) {
        LambdaEventBus.post(event)
    }

    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    fun onKeyInput(event: InputEvent.KeyInputEvent) {
        if (!Keyboard.getEventKeyState()) return

        if (!mc.gameSettings.keyBindSneak.isKeyDown) {
            val prefix = CommandManager.prefix
            val typedChar = Keyboard.getEventCharacter().toString()
            if (prefix.length == 1 && typedChar.equals(CommandManager.prefix, true)) {
                mc.displayGuiScreen(LambdaGuiChat(CommandManager.prefix))
            }
        }

        LambdaEventBus.post(event)
        ModuleManager.onBind(Keyboard.getEventKey())
    }

    @SubscribeEvent
    fun onEventMouse(event: InputEvent.MouseInputEvent) {
        LambdaEventBus.post(event)
        if (!Mouse.getEventButtonState()) return
        ModuleManager.onMouseBind(Mouse.getEventButton() + 1)
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onChatSent(event: ClientChatEvent) {
        MessageDetection.Command.BARITONE.removedOrNull(event.message)?.let {
            LambdaEventBus.post(BaritoneCommandEvent(it.toString().substringBefore(' ').lowercase()))
        }

        if (MessageDetection.Command.LAMBDA detect event.message) {
            CommandManager.runCommand(event.message.removePrefix(CommandManager.prefix))
            event.isCanceled = true
        }
    }

    /**
     * Includes events of subclasses like ChunkEvent and GetCollisionBoxesEvent
     */
    @SubscribeEvent
    fun onWorldEvent(event: WorldEvent) {
        LambdaEventBus.post(event)
    }

    /**
     * Also includes NoteBlockEvent
     */
    @SubscribeEvent
    fun onBlockEvent(event: BlockEvent) {
        LambdaEventBus.post(event)
    }

    @SubscribeEvent
    fun onInputUpdate(event: InputUpdateEvent) {
        LambdaEventBus.post(event)
    }

    @SubscribeEvent
    fun onLivingEntityUseItemEventTick(event: LivingEntityUseItemEvent.Tick) {
        LambdaEventBus.post(event)
    }

    @SubscribeEvent
    fun onPlayerInteractEvent(event: PlayerInteractEvent) {
        event.itemStack.let {
            if (it.item is ItemFood) {
                val result = PlayerEvent.OnEatStart(mc.player, it.item as ItemFood)
                LambdaEventBus.post(result)
            }
        }
        LambdaEventBus.post(event)
    }

    @SubscribeEvent
    fun onRenderBlockOverlay(event: RenderBlockOverlayEvent) {
        LambdaEventBus.post(event)
    }

    @SubscribeEvent
    fun onClientChat(event: ClientChatReceivedEvent) {
        LambdaEventBus.post(event)
    }

    @SubscribeEvent
    @Suppress("UNUSED_PARAMETER")
    fun onServerDisconnect(event: FMLNetworkEvent.ServerDisconnectionFromClientEvent) {
        LambdaEventBus.post(ConnectionEvent.Disconnect())
    }

    @SubscribeEvent
    @Suppress("UNUSED_PARAMETER")
    fun onClientDisconnect(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
        LambdaEventBus.post(ConnectionEvent.Disconnect())
    }

    @SubscribeEvent
    @Suppress("UNUSED_PARAMETER")
    fun onClientConnect(event: FMLNetworkEvent.ClientConnectedToServerEvent) {
        LambdaEventBus.post(ConnectionEvent.Connect())
    }

    @SubscribeEvent
    fun onRenderFogColors(event: EntityViewRenderEvent.FogColors) {
        LambdaEventBus.post(event)
    }

    @SubscribeEvent
    fun onEntityJoinWorld(event: EntityJoinWorldEvent) {
        com.lambda.client.event.events.WorldEvent.EntityCreate(event.entity).let {
            LambdaEventBus.post(it)
        }
    }

    @SubscribeEvent
    fun onPlayerJoin(event: net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent) {
        val result = com.lambda.client.event.events.WorldEvent.Join(event.player)
        LambdaEventBus.post(result)
    }

    @SubscribeEvent
    fun onPlayerLeave(event: net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent) {
        val result = com.lambda.client.event.events.WorldEvent.Leave(event.player)
        LambdaEventBus.post(result)
    }

    @SubscribeEvent
    fun onEntityDeath(event: LivingDeathEvent) {
        if (event.entity == CombatManager.target) {
            val result = TargetEvent.Death(event.entity)
            LambdaEventBus.post(result)
        }
        val result = com.lambda.client.event.events.WorldEvent.EntityDestroy(event.entity)
        LambdaEventBus.post(result)
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldEvent.Load) {
        LambdaEventBus.post(com.lambda.client.event.events.WorldEvent.Load(event.world))
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldEvent.Unload) {
        LambdaEventBus.post(com.lambda.client.event.events.WorldEvent.Unload(event.world))
    }
}
