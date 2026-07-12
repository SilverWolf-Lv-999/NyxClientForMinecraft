package io.github.seraphina.nyx.client.module.visual.hud.component;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.seraphina.nyx.client.module.visual.Map;
import io.github.seraphina.nyx.client.ui.UIComponent;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;

public class MapComponent implements UIComponent<Map> {
    private static final String ID = "map";
    private static final int RADIUS_BLOCKS = 64;
    private static final int DIAMETER_BLOCKS = RADIUS_BLOCKS * 2 + 1;
    private static final int REFRESH_INTERVAL_MS = 350;
    private static final int CHUNK_CACHE_SIZE = 9;
    private static final float PADDING = 8.0F;
    private static final float MAP_SIZE = 129.0F;
    private static final float WIDTH = MAP_SIZE + PADDING * 2.0F;
    private static final float HEIGHT = WIDTH;
    private static final float RADIUS = 7.0F;
    private static final int BACKGROUND = 0xD00C0D11;
    private static final int BORDER = 0x2EFFFFFF;
    private static final int SHADOW = 0x90000000;
    private static final int MISSING_COLOR = 0x00000000;
    private static final int WATER_COLOR = 0xFF2E73B8;
    private static final int LAVA_COLOR = 0xFFFF6A2A;
    private static final int PLAYER_COLOR = 0xFFFFFFFF;
    private static final int PLAYER_SHADOW = 0xAA000000;

    private DynamicTexture texture;
    private long nextRefreshMs;
    private int lastCenterX = Integer.MIN_VALUE;
    private int lastCenterZ = Integer.MIN_VALUE;
    private ResourceKey<Level> lastDimension;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isVisible() {
        return Map.INSTANCE.isEnabled();
    }

    @Override
    public float getDefaultX() {
        if (mc.getWindow() == null) {
            return 8.0F;
        }
        return Math.max(8.0F, mc.getWindow().getGuiScaledWidth() - WIDTH - 8.0F);
    }

    @Override
    public float getDefaultY() {
        return 36.0F;
    }

    @Override
    public void render(GuiGraphics graphics, float partialTicks, float scale) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }

        updateTextureIfNeeded(level, player);

        Render2DUtility.drawDropShadow(0.0F, 0.0F, WIDTH, HEIGHT, RADIUS, 0.0F, 2.0F, 12.0F, SHADOW);
        Render2DUtility.drawRoundedRect(0.0F, 0.0F, WIDTH, HEIGHT, RADIUS, BACKGROUND);
        if (texture != null) {
            Render2DUtility.drawRoundedTexture(texture.getTextureView(), PADDING, PADDING, MAP_SIZE, MAP_SIZE, 4.0F);
        }
        renderPlayerMarker(player);
        Render2DUtility.drawOutlineRoundedRect(0.0F, 0.0F, WIDTH, HEIGHT, RADIUS, 1.0F, BORDER);
    }

    @Override
    public AABB getBoundingBox() {
        return new AABB(0.0D, 0.0D, 0.0D, WIDTH, HEIGHT, 1.0D);
    }

    private void updateTextureIfNeeded(ClientLevel level, LocalPlayer player) {
        long now = System.currentTimeMillis();
        int centerX = player.getBlockX();
        int centerZ = player.getBlockZ();
        ResourceKey<Level> dimension = level.dimension();

        if (texture != null
            && centerX == lastCenterX
            && centerZ == lastCenterZ
            && dimension.equals(lastDimension)
            && now < nextRefreshMs) {
            return;
        }

        uploadTexture(sampleMap(level, centerX, centerZ));
        lastCenterX = centerX;
        lastCenterZ = centerZ;
        lastDimension = dimension;
        nextRefreshMs = now + REFRESH_INTERVAL_MS;
    }

    private NativeImage sampleMap(ClientLevel level, int centerX, int centerZ) {
        NativeImage image = new NativeImage(DIAMETER_BLOCKS, DIAMETER_BLOCKS, true);
        ClientChunkCache chunkSource = level.getChunkSource();
        int centerChunkX = SectionPos.blockToSectionCoord(centerX);
        int centerChunkZ = SectionPos.blockToSectionCoord(centerZ);
        LevelChunk[] chunks = new LevelChunk[CHUNK_CACHE_SIZE * CHUNK_CACHE_SIZE];
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int minY = level.dimensionType().minY();
        int maxY = minY + level.dimensionType().height() - 1;

        for (int imageZ = 0; imageZ < DIAMETER_BLOCKS; imageZ++) {
            int dz = imageZ - RADIUS_BLOCKS;
            int worldZ = centerZ + dz;
            for (int imageX = 0; imageX < DIAMETER_BLOCKS; imageX++) {
                int dx = imageX - RADIUS_BLOCKS;
                if (dx * dx + dz * dz > RADIUS_BLOCKS * RADIUS_BLOCKS) {
                    image.setPixel(imageX, imageZ, MISSING_COLOR);
                    continue;
                }

                int worldX = centerX + dx;
                LevelChunk chunk = chunkAt(chunkSource, chunks, centerChunkX, centerChunkZ, worldX, worldZ);
                if (chunk == null) {
                    image.setPixel(imageX, imageZ, MISSING_COLOR);
                    continue;
                }

                image.setPixel(imageX, imageZ, sampleColor(level, chunk, mutablePos, worldX, worldZ, minY, maxY));
            }
        }

        return image;
    }

    private LevelChunk chunkAt(ClientChunkCache chunkSource, LevelChunk[] chunks, int centerChunkX, int centerChunkZ,
                               int worldX, int worldZ) {
        int chunkX = SectionPos.blockToSectionCoord(worldX);
        int chunkZ = SectionPos.blockToSectionCoord(worldZ);
        int cacheX = chunkX - centerChunkX + CHUNK_CACHE_SIZE / 2;
        int cacheZ = chunkZ - centerChunkZ + CHUNK_CACHE_SIZE / 2;

        if (cacheX < 0 || cacheX >= CHUNK_CACHE_SIZE || cacheZ < 0 || cacheZ >= CHUNK_CACHE_SIZE) {
            return chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        }

        int index = cacheZ * CHUNK_CACHE_SIZE + cacheX;
        LevelChunk chunk = chunks[index];
        if (chunk == null) {
            chunk = chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            chunks[index] = chunk;
        }
        return chunk;
    }

    private int sampleColor(ClientLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos,
                            int worldX, int worldZ, int minY, int maxY) {
        int height = Mth.clamp(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX & 15, worldZ & 15) - 1, minY, maxY);
        pos.set(worldX, height, worldZ);
        BlockState state = chunk.getBlockState(pos);

        while (height > minY && (state.isAir() || state.getMapColor(level, pos) == MapColor.NONE)) {
            height--;
            pos.setY(height);
            state = chunk.getBlockState(pos);
        }

        int color = baseColor(level, pos, state);
        return shadeByHeight(color, height, minY, maxY);
    }

    private int baseColor(ClientLevel level, BlockPos pos, BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            if (state.is(Blocks.LAVA)) {
                return LAVA_COLOR;
            }
            return WATER_COLOR;
        }

        MapColor mapColor = state.getMapColor(level, pos);
        if (mapColor == MapColor.NONE) {
            return 0xFF1A1C22;
        }
        return mapColor.calculateARGBColor(MapColor.Brightness.NORMAL);
    }

    private int shadeByHeight(int color, int height, int minY, int maxY) {
        float normalized = (height - minY) / (float)Math.max(1, maxY - minY);
        float factor = 0.78F + normalized * 0.34F;
        int alpha = color & 0xFF000000;
        int red = Mth.clamp(Math.round(((color >> 16) & 0xFF) * factor), 0, 255);
        int green = Mth.clamp(Math.round(((color >> 8) & 0xFF) * factor), 0, 255);
        int blue = Mth.clamp(Math.round((color & 0xFF) * factor), 0, 255);
        return alpha | red << 16 | green << 8 | blue;
    }

    private void uploadTexture(NativeImage image) {
        if (texture == null) {
            texture = new DynamicTexture(() -> "nyx-map", image);
            return;
        }

        texture.setPixels(image);
        texture.upload();
    }

    private void renderPlayerMarker(LocalPlayer player) {
        float centerX = PADDING + MAP_SIZE * 0.5F;
        float centerY = PADDING + MAP_SIZE * 0.5F;
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        float directionX = -Mth.sin(yaw);
        float directionY = Mth.cos(yaw);
        float tipX = centerX + directionX * 7.0F;
        float tipY = centerY + directionY * 7.0F;

        Render2DUtility.drawLine(centerX + 1.0F, centerY + 1.0F, tipX + 1.0F, tipY + 1.0F, 3.0F, PLAYER_SHADOW);
        Render2DUtility.drawCircle(centerX + 1.0F, centerY + 1.0F, 3.0F, PLAYER_SHADOW);
        Render2DUtility.drawLine(centerX, centerY, tipX, tipY, 2.0F, PLAYER_COLOR);
        Render2DUtility.drawCircle(centerX, centerY, 2.5F, PLAYER_COLOR);
    }
}
