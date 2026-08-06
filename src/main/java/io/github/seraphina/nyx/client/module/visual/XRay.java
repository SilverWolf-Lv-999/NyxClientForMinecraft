package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

@ModuleInfo(name = "nyxclient.module.xray.name", description = "nyxclient.module.xray.description", category = Category.VISUAL)
public class XRay extends Module {
    public static final XRay INSTANCE = new XRay();
    private static final Set<Block> XRAY_BLOCKS = Set.of(
            Blocks.DIAMOND_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.GOLD_ORE,
            Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.NETHER_GOLD_ORE,
            Blocks.IRON_ORE,
            Blocks.DEEPSLATE_IRON_ORE,
            Blocks.REDSTONE_ORE,
            Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.EMERALD_ORE,
            Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.COAL_ORE,
            Blocks.DEEPSLATE_COAL_ORE,
            Blocks.ANCIENT_DEBRIS,
            Blocks.NETHER_QUARTZ_ORE,
            Blocks.LAPIS_ORE,
            Blocks.DEEPSLATE_LAPIS_ORE
    );

    @Override
    public void onEnable() {
        refreshTerrain();
    }

    @Override
    public void onDisable() {
        refreshTerrain();
    }

    public boolean isVisible(BlockState state) {
        return state != null && XRAY_BLOCKS.contains(state.getBlock());
    }

    private void refreshTerrain() {
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
    }
}
