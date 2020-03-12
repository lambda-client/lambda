package me.zeroeightsix.kami.module.modules.combat;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
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

@Module.Info(name = "HoleFiller", category = Module.Category.COMBAT, description="Fills holes around the player to make people easier to crystal.")
public class HoleFiller extends Module {
    private Setting<Float> distance = register(Settings.f("Range", 4.0f));
    private Setting<Boolean> render = register(Settings.b("Render Filled Blocks", false));
    private Setting<Boolean> holeCheck = register(Settings.b("Only Fill in Hole", true));
   
    public List<BlockPos> blockPosList;
    public List<BlockPos> blocksToFill;
    List<Entity> entities = new ArrayList<>();
    
    public boolean isHole;
    
    Vec3d[] holeOffset = {
        	mc.player.getPositionVector().add(1, 0, 0),
        	mc.player.getPositionVector().add(-1, 0, 0),
        	mc.player.getPositionVector().add(0, 0, 1),
        	mc.player.getPositionVector().add(0, 0, -1),
        	mc.player.getPositionVector().add(0, -1, 0)
    };
    
    private final BlockPos[] surroundOffset = {
            new BlockPos(0, -1, 0), // down
            new BlockPos(0, 0, -1), // north
            new BlockPos(1, 0, 0), // east
            new BlockPos(0, 0, 1), // south
            new BlockPos(-1, 0, 0) // west
    };
    
    private boolean isInHole() {
    	int holeBlocks = 0;
    	for (Vec3d vecOffset:holeOffset) { /* for placeholder offset for each Vector in the list holeOffset */
    	    BlockPos offset = new BlockPos(vecOffset.x,vecOffset.y, vecOffset.z);
    		if (mc.world.getBlockState(offset).getBlock() == Blocks.OBSIDIAN || mc.world.getBlockState(offset).getBlock() == Blocks.BEDROCK) {
    			holeBlocks++;
    		}
    	}
    	if (holeBlocks == 5) {
    		return true;
    	} else {
    		return false;
    	}
    }
    
    @Override
    public void onUpdate() {
        entities.addAll(mc.world.playerEntities.stream().filter(entityPlayer -> !Friends.isFriend(entityPlayer.getName())).collect(Collectors.toList()));
        int range = (int) Math.ceil(distance.getValue());
    	CrystalAura ca = (CrystalAura) ModuleManager.getModuleByName("CrystalAura");
    	blockPosList = ca.getSphere(getPlayerPos(), range, range, false, true, 0);
    	for (BlockPos p:blockPosList) {
    		isHole = true;
    		// block gotta be air
            if (!mc.world.getBlockState(p).getBlock().equals(Blocks.AIR)) {
                continue;
            }

            // block 1 above gotta be air
            if (!mc.world.getBlockState(p.add(0, 1, 0)).getBlock().equals(Blocks.AIR)) {
                continue;
            }

            // block 2 above gotta be air
            if (!mc.world.getBlockState(p.add(0, 2, 0)).getBlock().equals(Blocks.AIR)) {
                continue;
            }
            for (BlockPos o : surroundOffset) {
                Block block = mc.world.getBlockState(p.add(o)).getBlock();
                if (block != Blocks.BEDROCK && block != Blocks.OBSIDIAN && block != Blocks.ENDER_CHEST && block != Blocks.ANVIL) {
                    isHole = false;
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
    }
}