package io.github.seraphina.nyx.client.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.BlockBreakingProgressEvent;
import io.github.seraphina.nyx.client.events.impl.Render3DEvent;
import io.github.seraphina.nyx.client.manager.FriendManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.client.Friend;
import io.github.seraphina.nyx.client.utility.MathUtility;
import io.github.seraphina.nyx.client.utility.Render3DUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(name = "nyxclient.module.breakesp.name", description = "nyxclient.module.breakesp.description", category = Category.VISUAL)
public class BreakESP extends Module {
    public static final BreakESP INSTANCE = new BreakESP();
    private static final long ENTRY_TIMEOUT_MS = 1000L;

    public final BoolValue progress = ValueBuild.boolSetting("progress", true, this);
    public final BoolValue fill = ValueBuild.boolSetting("fill", true, this);
    public final ColorValue fillColor = ValueBuild.colorSetting(
            "fill color",
            new Color(198, 176, 12, 78),
            () -> fill.getValue(),
            this
    );
    public final BoolValue outline = ValueBuild.boolSetting("outline", true, this);
    public final ColorValue outlineColor = ValueBuild.colorSetting(
            "outline color",
            new Color(198, 176, 12, 255),
            () -> outline.getValue(),
            this
    );
    public final BoolValue friendFill = ValueBuild.boolSetting("friend fill", true, this);
    public final ColorValue friendFillColor = ValueBuild.colorSetting(
            "friend fill color",
            new Color(30, 45, 169, 78),
            () -> friendFill.getValue(),
            this
    );
    public final BoolValue friendOutline = ValueBuild.boolSetting("friend outline", true, this);
    public final ColorValue friendOutlineColor = ValueBuild.colorSetting(
            "friend outline color",
            new Color(30, 45, 169, 255),
            () -> friendOutline.getValue(),
            this
    );
    public final EnumValue<Easing> easing = ValueBuild.enumSetting("easing", Easing.CUBIC_IN_OUT, this);
    public final BoolValue second = ValueBuild.boolSetting("second", true, this);
    public final BoolValue secondFill = ValueBuild.boolSetting("second fill", true, () -> second.getValue(), this);
    public final ColorValue secondFillColor = ValueBuild.colorSetting(
            "second fill color",
            new Color(255, 255, 255, 100),
            () -> second.getValue() && secondFill.getValue(),
            this
    );
    public final BoolValue secondOutline = ValueBuild.boolSetting("second outline", true, () -> second.getValue(), this);
    public final ColorValue secondOutlineColor = ValueBuild.colorSetting(
            "second outline color",
            new Color(255, 255, 255, 255),
            () -> second.getValue() && secondOutline.getValue(),
            this
    );

    private final Map<Integer, BreakEntry> primaryBreaks = new ConcurrentHashMap<>();
    private final Map<Integer, BreakEntry> secondaryBreaks = new ConcurrentHashMap<>();

    @Override
    public void onDisable() {
        primaryBreaks.clear();
        secondaryBreaks.clear();
    }

    @EventTarget
    public void onBlockBreakingProgress(BlockBreakingProgressEvent event) {
        if (mc.level == null || mc.player == null) {
            return;
        }

        Entity entity = mc.level.getEntity(event.getBreakerId());
        if (!(entity instanceof Player) || entity == mc.player) {
            return;
        }

        int breakerId = event.getBreakerId();
        BlockPos pos = event.getPos();
        if (event.getProgress() < 0) {
            removeBreak(primaryBreaks, breakerId, pos);
            removeBreak(secondaryBreaks, breakerId, pos);
            return;
        }

        long now = System.currentTimeMillis();
        BreakEntry current = primaryBreaks.get(breakerId);
        if (current != null && !current.pos.equals(pos)) {
            secondaryBreaks.put(breakerId, current);
        }
        primaryBreaks.put(breakerId, new BreakEntry(breakerId, pos, Math.min(event.getProgress(), 9), now));
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) {
            primaryBreaks.clear();
            secondaryBreaks.clear();
            return;
        }

        long now = System.currentTimeMillis();
        renderEntries(event.getPoseStack(), primaryBreaks, now, false);
        if (second.getValue()) {
            renderEntries(event.getPoseStack(), secondaryBreaks, now, true);
        }
    }

    private void renderEntries(PoseStack poseStack, Map<Integer, BreakEntry> entries, long now, boolean isSecondary) {
        for (Map.Entry<Integer, BreakEntry> mapEntry : entries.entrySet()) {
            BreakEntry entry = mapEntry.getValue();
            if (!isValid(entry, now)) {
                entries.remove(mapEntry.getKey(), entry);
                continue;
            }

            Entity entity = mc.level.getEntity(entry.breakerId);
            if (!(entity instanceof Player player)) {
                entries.remove(mapEntry.getKey(), entry);
                continue;
            }

            renderEntry(poseStack, entry, player, isSecondary);
        }
    }

    private boolean isValid(BreakEntry entry, long now) {
        return entry != null
                && now - entry.updatedAt <= ENTRY_TIMEOUT_MS
                && !mc.level.isEmptyBlock(entry.pos);
    }

    private void renderEntry(PoseStack poseStack, BreakEntry entry, Player player, boolean isSecondary) {
        AABB box = progress.getValue() ? progressBox(entry.progress, entry.pos) : new AABB(entry.pos);
        if (isSecondary) {
            if (secondFill.getValue()) {
                Render3DUtility.renderFilledBoxNoDepth(poseStack, box, secondFillColor.getValue());
            }
            if (secondOutline.getValue()) {
                Render3DUtility.renderOutlineBoxNoDepth(poseStack, box, secondOutlineColor.getValue());
            }
            return;
        }

        boolean friend = Friend.INSTANCE.isEnabled() && FriendManager.isFriend(player);
        if (friend) {
            if (friendFill.getValue()) {
                Render3DUtility.renderFilledBoxNoDepth(poseStack, box, friendFillColor.getValue());
            }
            if (friendOutline.getValue()) {
                Render3DUtility.renderOutlineBoxNoDepth(poseStack, box, friendOutlineColor.getValue());
            }
            return;
        }

        if (fill.getValue()) {
            Render3DUtility.renderFilledBoxNoDepth(poseStack, box, fillColor.getValue());
        }
        if (outline.getValue()) {
            Render3DUtility.renderOutlineBoxNoDepth(poseStack, box, outlineColor.getValue());
        }
    }

    private AABB progressBox(int damage, BlockPos pos) {
        float rawProgress = (damage + 1) / 10.0F;
        double size = switch (easing.getValue()) {
            case LINEAR -> rawProgress;
            case CUBIC_IN_OUT -> MathUtility.easeInOutCubic(rawProgress);
        };
        return AABB.ofSize(pos.getCenter(), size, size, size);
    }

    private static void removeBreak(Map<Integer, BreakEntry> entries, int breakerId, BlockPos pos) {
        entries.computeIfPresent(breakerId, (ignored, entry) -> entry.pos.equals(pos) ? null : entry);
    }

    public enum Easing {
        LINEAR,
        CUBIC_IN_OUT
    }

    private record BreakEntry(int breakerId, BlockPos pos, int progress, long updatedAt) {
    }
}
