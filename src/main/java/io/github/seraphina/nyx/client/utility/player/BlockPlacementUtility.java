package io.github.seraphina.nyx.client.utility.player;

import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

public final class BlockPlacementUtility {
    private static final Minecraft MC = Minecraft.getInstance();

    private BlockPlacementUtility() {
    }

    public static boolean canPlace(BlockPos pos, boolean ignoreEndCrystals) {
        if (MC.player == null || MC.level == null || pos == null || !MC.level.getBlockState(pos).canBeReplaced()) {
            return false;
        }

        for (Entity entity : MC.level.getEntities(null, new AABB(pos))) {
            if (blocksPlacement(entity, ignoreEndCrystals)) {
                return false;
            }
        }

        return true;
    }

    public static Placement findPlacement(BlockPos pos, double range) {
        if (MC.player == null || MC.level == null || pos == null) {
            return null;
        }

        for (Direction direction : Direction.values()) {
            BlockPos clickedBlock = pos.relative(direction);
            BlockState state = MC.level.getBlockState(clickedBlock);
            if (state.canBeReplaced() || !state.blocksMotion()) {
                continue;
            }

            Direction face = direction.getOpposite();
            Vec3 hitVec = Vec3.atCenterOf(clickedBlock).add(
                    face.getStepX() * 0.5D,
                    face.getStepY() * 0.5D,
                    face.getStepZ() * 0.5D
            );
            if (MC.player.getEyePosition().distanceTo(hitVec) <= range) {
                return new Placement(clickedBlock, face, hitVec);
            }
        }

        return null;
    }

    public static boolean placeFromHotbar(int hotbarSlot, Placement placement, boolean rotate, boolean packetPlace) {
        Vector2f rotations = rotate && placement != null ? RotationUtility.calculate(placement.hitVec()) : null;
        return placeFromHotbar(hotbarSlot, placement, rotations, packetPlace);
    }

    public static boolean placeFromHotbar(int hotbarSlot, Placement placement, Vector2f rotations, boolean packetPlace) {
        if (MC.player == null
                || MC.level == null
                || MC.gameMode == null
                || MC.player.connection == null
                || placement == null
                || !Inventory.isHotbarSlot(hotbarSlot)) {
            return false;
        }

        BlockHitResult hitResult = new BlockHitResult(
                placement.hitVec(),
                placement.face(),
                placement.clickedBlock(),
                false
        );
        if (rotations != null) {
            MC.player.connection.send(new ServerboundMovePlayerPacket.Rot(
                    rotations.x,
                    rotations.y,
                    MC.player.onGround(),
                    MC.player.horizontalCollision
            ));
        }

        if (packetPlace) {
            return PacketUtility.useHotbarItemOnBlock(hotbarSlot, hitResult);
        }

        if (!InventoryUtility.selectHotbarSlot(hotbarSlot, true)) {
            return false;
        }

        float previousYaw = MC.player.getYRot();
        float previousPitch = MC.player.getXRot();
        if (rotations != null) {
            MC.player.setYRot(rotations.x);
            MC.player.setXRot(rotations.y);
        }

        try {
            InteractionResult result = MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, hitResult);
            if (result.consumesAction()) {
                MC.player.swing(InteractionHand.MAIN_HAND);
            }
            return result.consumesAction();
        } finally {
            if (rotations != null) {
                MC.player.setYRot(previousYaw);
                MC.player.setXRot(previousPitch);
            }
        }
    }

    private static boolean blocksPlacement(Entity entity, boolean ignoreEndCrystals) {
        return entity != MC.player
                && entity.isAlive()
                && !(entity instanceof ItemEntity)
                && !(entity instanceof ExperienceOrb)
                && !(entity instanceof Projectile)
                && (!ignoreEndCrystals || !(entity instanceof EndCrystal));
    }

    public record Placement(BlockPos clickedBlock, Direction face, Vec3 hitVec) {
    }
}
