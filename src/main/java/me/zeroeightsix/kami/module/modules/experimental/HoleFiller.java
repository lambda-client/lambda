package me.zeroeightsix.kami.module.modules.experimental;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.module.modules.combat.CrystalAura;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.BlockInteractionHelper;
import me.zeroeightsix.kami.util.Friends;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static me.zeroeightsix.kami.module.modules.combat.CrystalAura.getPlayerPos;

/**
 * @author hub
 * @author polymer
 * Created by polymer on 12/03/20
 */
@Module.Info(name = "HoleFiller", category = Module.Category.EXPERIMENTAL, description="Fills holes around the player to make people easier to crystal.")
public class HoleFiller extends Module {
    private Setting<Float> distance = register(Settings.f("Range", 4.0f));
    /*
    private Setting<Boolean> render = register(Settings.b("Render Filled Blocks", false));
    private Setting<Boolean> holeCheck = register(Settings.b("Only Fill in Hole", true));
    */ /* unused */
   
    public List<BlockPos> blockPosList;
    public List<BlockPos> blocksToFill;
    List<Entity> entities = new ArrayList<>();
    
    public boolean isHole;
    
    private final BlockPos[] surroundOffset = {
            new BlockPos(0, -1, 0), // down
            new BlockPos(0, 0, -1), // north
            new BlockPos(1, 0, 0), // east
            new BlockPos(0, 0, 1), // south
            new BlockPos(-1, 0, 0) // west
    };

    /* Vec3d[] holeOffset; */
    
    @Override
    public void onUpdate() {
        /* mc.player can only be null if the world is null, so checking if the mc.player is null *should be sufficient */
       if (mc.player == null && mc.world == null) return;
       /*
       Vec3d[] holeOffset = {
           	mc.player.getPositionVector().add(1, 0, 0),
           	mc.player.getPositionVector().add(-1, 0, 0),
           	mc.player.getPositionVector().add(0, 0, 1),
           	mc.player.getPositionVector().add(0, 0, -1),
           	mc.player.getPositionVector().add(0, -1, 0)
       };
       */ /* this is never used */

    	entities.addAll(mc.world.playerEntities.stream().filter(entityPlayer -> !Friends.isFriend(entityPlayer.getName())).collect(Collectors.toList()));
        int range = (int) Math.ceil(distance.getValue());
    	CrystalAura ca = (CrystalAura) ModuleManager.getModuleByName("CrystalAura");
    	blockPosList = ca.getSphere(getPlayerPos(), range, range, false, true, 0);

    	if (blockPosList == null || blocksToFill == null) return;
    	for (BlockPos p: blockPosList) {
    	    if (p == null) return;

    		isHole = true;
    		// block gotta be air
            if (!mc.world.getBlockState(p).getBlock().equals(Blocks.AIR)) continue;

            // block 1 above gotta be air
            if (!mc.world.getBlockState(p.add(0, 1, 0)).getBlock().equals(Blocks.AIR)) continue;

            // block 2 above gotta be air
            if (!mc.world.getBlockState(p.add(0, 2, 0)).getBlock().equals(Blocks.AIR)) continue;

            for (BlockPos o : surroundOffset) {
                Block block = mc.world.getBlockState(p.add(o)).getBlock();
                if (block != Blocks.BEDROCK && block != Blocks.OBSIDIAN && block != Blocks.ENDER_CHEST && block != Blocks.ANVIL) {
                    isHole = false;
                    break;
                }
            }

            if (isHole) {
            	if (mc.player.getPositionVector().x == p.x && mc.player.getPositionVector().y == p.y && mc.player.getPositionVector().z == p.z) {
            		break;
            	}
            	for (Entity e: entities) {
            		if (e.getPositionVector().x-0.2 == p.x && e.getPositionVector().y == p.y && e.getPositionVector().z-0.2 == p.z) {
                		break;
                	}
            	}
            blocksToFill.add(p);
            }
    	}
    	for (BlockPos p: blocksToFill) {
    		BlockInteractionHelper.placeBlockScaffold(p);
    	}
    }
}