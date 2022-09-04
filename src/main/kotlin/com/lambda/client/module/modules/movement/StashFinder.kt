package com.lambda.client.module.modules.movement

import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.gui.GuiMultiplayer
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.combat.CrystalAura
import com.lambda.client.module.modules.combat.CrystalAura.atValue
import com.lambda.client.module.modules.movement.StashFinder.atValue
import com.lambda.client.util.math.RotationUtils
import com.lambda.client.util.text.MessageSendHelper

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent

import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.GeometryMasks
import com.lambda.client.util.graphics.LambdaTessellator
import com.lambda.client.util.threads.safeListener
import org.lwjgl.opengl.GL11


internal object StashFinder : Module(
    name = "Stashfinder",
    category = Category.MISC,
    description = "circle spinna ",

    ) {
    private val page = setting("Page", Page.GENERAL)

    private val ogX = setting("dontTouchX", 50, -99999999..99999999, 1, page.atValue(Page.DONTTOUCH))
    private val ogZ = setting("dontTouchY", 50, -99999999..99999999, 1, page.atValue(Page.DONTTOUCH))

    private val spacerLength = setting("spiralSpacerVal", 25, 1..5000, 5, page.atValue(Page.GENERAL))
    // private var timesPointer = 1

    private val resumeMode = setting("resumeMode", false,page.atValue(Page.GENERAL))
    private val sideLengthForResume = setting("sideLengthOfSpiralforResume", 50, 1..999999, 1,page.atValue(Page.GENERAL))
    private val timesPointerForResume = setting("timesPointerForResume", 1, 1..50, 1,  page.atValue(Page.DONTTOUCH))
    private val directionCounter = setting("directionCounterDontTouch", 1, 1..999999, 1,  page.atValue(Page.DONTTOUCH))
    private val destinationXonCount = setting("destinationXonCountDontTouch", 0, -9999999..9999999, 1, page.atValue(Page.DONTTOUCH))
    private val destinationZonCount = setting("destinationZonCountDontTouch", 0, -9999999..9999999, 1, page.atValue(Page.DONTTOUCH))
    private val timesPointer = setting("timesPointerDontTouch", 0, 1..999999, 1, page.atValue(Page.DONTTOUCH))
    //private var directionCounter = 1
    private var destinationX = 0.00
    private var destinationZ = 0.00
    var blocksTraveled = 0.00

    //private var destinationXonCount = 0.00
    //private var destinationZonCount = 0.00
    // private var xzResumeCounter = 1
    private enum class Page {
        GENERAL, DONTTOUCH
    }
    //face west for resume top right hand corner of   completed area and use side length of already completed area as side length for resume/ side length for resueme must be biggr then spacer length
    private fun drawVerticalLines(pos: BlockPos, color: ColorHolder) {
        val box = AxisAlignedBB(pos.x.toDouble(), 0.0, pos.z.toDouble(), pos.x + 1.0, 256.0, pos.z + 1.0)
        LambdaTessellator.begin(GL11.GL_LINES)
        LambdaTessellator.drawOutline(box, color, 55, GeometryMasks.Quad.ALL, 1f)
        LambdaTessellator.render()
    }

    init {

        onEnable {
            blocksTraveled = 0.00
            if (resumeMode.value){
                timesPointer.value = (sideLengthForResume.value.toInt()/ spacerLength.value.toInt()).toInt()
            }
            safeListener<TickEvent.ClientTickEvent> {
                if (!resumeMode.value) {
                    ogX.setValue(mc.player.posX)
                    ogZ.setValue(mc.player.posZ)
                }
                if (resumeMode.value){
                    //spacerLength.setValue(sideLengthForResume.value.toDouble())
                    ogX.setValue(mc.player.posX)
                    ogZ.setValue(mc.player.posZ)
                }
            }
        }

        onDisable {
            directionCounter.value = 1
            destinationX = 0.00
            destinationZ = 0.00
            destinationXonCount.value = 0
            destinationZonCount.value = 0
            timesPointer.value = 1
            blocksTraveled = 0.00
            //xzResumeCounter = 1
        }
    }
    init{
        safeListener<TickEvent.ClientTickEvent> {

            /*
            if (it.phase == TickEvent.Phase.START && mc.player.posY < disconnectBelowYlevelval.value && disconnectBelowYlevelTF.value){
                val screen = getScreen() // do this before disconnecting

                // mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
                Companion.mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.BLOCK_GLASS_BREAK, 1.0f, 1.0f))

                connection.networkManager.closeChannel(TextComponentString(""))
                Companion.mc.loadWorld(null as WorldClient?)
                var reasonText = arrayOf<String>("Disconnected because stashfinder Player was below ", disconnectBelowYlevelval.value.toString())
                Companion.mc.displayGuiScreen(KamiGuiDisconnected(reasonText, screen, disable = false, LocalTime.now()))
                return@safeListener
                //mc.displayGuiScreen(KamiGuiDisconnected(reasonText, screen, AutoLog.disableMode == AutoLog.DisableMode.ALWAYS || (AutoLog.disableMode == AutoLog.DisableMode.NOT_PLAYER && reason != AutoLog.Reasons.PLAYER), LocalTime.now()))
            }

             */
            if (!resumeMode.value && it.phase == TickEvent.Phase.START) {
                // MessageSendHelper.sendChatMessage(destinationXonCount.toString() +"xonc")
                // MessageSendHelper.sendChatMessage(destinationZonCount.toString() +"zonc")
                // MessageSendHelper.sendChatMessage(destinationXonCount.toString() +"xonc")
                //MessageSendHelper.sendChatMessage(directionCounter.toString() +"zonc")
                if (directionCounter.value ==1){
                    if (destinationXonCount.value == 0){
                        destinationXonCount.value = mc.player.posX.toInt()
                    }
                    if (destinationZonCount.value == 0){
                        destinationZonCount.value = mc.player.posZ.toInt()
                    }

                    val localVec3d = Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ)
                    val localVec3dZeroZero = Vec3d(destinationXonCount.value.toDouble(), mc.player.posY, destinationZonCount.value.toDouble()+ spacerLength.value.toDouble()* timesPointer.value.toDouble())
                    destinationX = localVec3dZeroZero.x
                    destinationZ = localVec3dZeroZero.z


                    // drawVerticalLines(BlockPos(destinationX.toInt(),55, destinationZ.toInt()),ColorHolder(111, 1, 155))
                    val tval = RotationUtils.getRotationTo(localVec3d,localVec3dZeroZero)
                    if (StashFinderCamera.isEnabled&& mc.player.ticksExisted % 50 == 0){mc.player.rotationYaw = tval.x}
                }
                if (directionCounter.value ==2){
                    val localVec3d = Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ)
                    val localVec3dZeroZero = Vec3d(destinationXonCount.value.toDouble()+ spacerLength.value.toDouble()* timesPointer.value.toDouble(), mc.player.posY, destinationZonCount.value.toDouble())
                    destinationX = localVec3dZeroZero.x
                    destinationZ = localVec3dZeroZero.z
                    // if hold camera
                    val tval = RotationUtils.getRotationTo(localVec3d,localVec3dZeroZero)
                    if (StashFinderCamera.isEnabled&& mc.player.ticksExisted % 50 == 0){mc.player.rotationYaw = tval.x}
                }
                if (directionCounter.value ==3){
                    val localVec3d = Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ)
                    val localVec3dZeroZero = Vec3d(destinationXonCount.value.toDouble(), mc.player.posY, destinationZonCount.value.toDouble()- spacerLength.value.toDouble()* timesPointer.value.toDouble())
                    destinationX = localVec3dZeroZero.x
                    destinationZ =localVec3dZeroZero.z
                    val tval = RotationUtils.getRotationTo(localVec3d,localVec3dZeroZero)
                    if (StashFinderCamera.isEnabled&& mc.player.ticksExisted % 50 == 0){mc.player.rotationYaw = tval.x}
                }
                if (directionCounter.value ==4){
                    val localVec3d = Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ)
                    val localVec3dZeroZero = Vec3d(destinationXonCount.value.toDouble()- spacerLength.value.toDouble()* timesPointer.value.toDouble(), mc.player.posY, destinationZonCount.value.toDouble())
                    destinationX = localVec3dZeroZero.x
                    destinationZ = localVec3dZeroZero.z
                    val tval = RotationUtils.getRotationTo(localVec3d,localVec3dZeroZero)
                    if (StashFinderCamera.isEnabled&& mc.player.ticksExisted % 50 == 0){mc.player.rotationYaw = tval.x}
                }
                if (destinationX-50<mc.player.posX && mc.player.posX< destinationX+50&& destinationZ-50<mc.player.posZ && mc.player.posZ< destinationZ+50){
                    MessageSendHelper.sendChatMessage("inchange if")
                    destinationXonCount.value = mc.player.posX.toInt()
                    destinationZonCount.value = mc.player.posZ.toInt()

                    if (directionCounter.value <5){
                        MessageSendHelper.sendChatMessage("inchange if2"+ directionCounter.value.toString())
                        timesPointer.value += 1
                        directionCounter.value+=1
                        blocksTraveled += (spacerLength.value.toDouble()* timesPointer.value.toDouble())
                        print(blocksTraveled)
                        if (directionCounter.value == 5){
                            directionCounter.value=1
                        }
                    }
                    return@safeListener

                }




            }

            if (resumeMode.value && it.phase == TickEvent.Phase.START){
                if (directionCounter.value ==2){


                    val localVec3d = Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ)
                    val localVec3dZeroZero = Vec3d(destinationXonCount.value.toDouble(), mc.player.posY, destinationZonCount.value.toDouble()+ spacerLength.value.toDouble()* timesPointer.value.toDouble())
                    destinationX = localVec3dZeroZero.x
                    destinationZ = localVec3dZeroZero.z

                    val tval = RotationUtils.getRotationTo(localVec3d,localVec3dZeroZero)

                    if (StashFinderCamera.isEnabled&& mc.player.ticksExisted % 50 == 0){mc.player.rotationYaw = tval.x}
                }
                if (directionCounter.value ==3){

                    val localVec3d = Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ)
                    val localVec3dZeroZero = Vec3d(destinationXonCount.value.toDouble()+ spacerLength.value.toDouble()* timesPointer.value.toDouble(), mc.player.posY, destinationZonCount.value.toDouble())
                    destinationX = localVec3dZeroZero.x
                    destinationZ = localVec3dZeroZero.z
                    // if hold camera
                    val tval = RotationUtils.getRotationTo(localVec3d,localVec3dZeroZero)
                    if (StashFinderCamera.isEnabled&& mc.player.ticksExisted % 50 == 0){mc.player.rotationYaw = tval.x}
                }
                if (directionCounter.value ==4){
                    val localVec3d = Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ)
                    val localVec3dZeroZero = Vec3d(destinationXonCount.value.toDouble(), mc.player.posY, destinationZonCount.value.toDouble()- spacerLength.value.toDouble()* timesPointer.value.toDouble())
                    destinationX = localVec3dZeroZero.x
                    destinationZ =localVec3dZeroZero.z
                    val tval = RotationUtils.getRotationTo(localVec3d,localVec3dZeroZero)
                    if (StashFinderCamera.isEnabled&& mc.player.ticksExisted % 50 == 0){mc.player.rotationYaw = tval.x}
                }
                if (directionCounter.value ==1){
                    if (destinationXonCount.value == 0){
                        destinationXonCount.value = mc.player.posX.toInt()
                    }
                    if (destinationZonCount.value == 0){
                        destinationZonCount.value = mc.player.posZ.toInt()
                    }
                    val localVec3d = Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ)
                    val localVec3dZeroZero = Vec3d(destinationXonCount.value.toDouble()- spacerLength.value.toDouble()* timesPointer.value.toDouble(), mc.player.posY, destinationZonCount.value.toDouble())
                    destinationX = localVec3dZeroZero.x
                    destinationZ = localVec3dZeroZero.z
                    val tval = RotationUtils.getRotationTo(localVec3d,localVec3dZeroZero)
                    if (StashFinderCamera.isEnabled&& mc.player.ticksExisted % 50 == 0){mc.player.rotationYaw = tval.x}
                }
                if (destinationX-50<mc.player.posX && mc.player.posX< destinationX+50&& destinationZ-50<mc.player.posZ && mc.player.posZ< destinationZ+50){
                    MessageSendHelper.sendChatMessage("inchange if")
                    destinationXonCount.value = mc.player.posX.toInt()
                    destinationZonCount.value = mc.player.posZ.toInt()
                    timesPointer.value += 1
                    if (directionCounter.value <5){
                        MessageSendHelper.sendChatMessage("inchange if2"+ directionCounter.toString())
                        directionCounter.value+=1
                        blocksTraveled += (spacerLength.value.toDouble()* timesPointer.value.toDouble())
                        if (directionCounter.value == 5){
                            directionCounter.value=1
                        }
                    }
                    return@safeListener

                }
            }
        }
    }
    private fun getScreen() = if (mc.isIntegratedServerRunning) {
        GuiMainMenu()
    } else {
        GuiMultiplayer(GuiMainMenu())
    }


}

