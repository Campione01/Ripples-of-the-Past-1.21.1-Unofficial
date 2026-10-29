package rotp.addons.metallica.state;

import java.util.List;

import rotp.addons.metallica.MetallicaAddon;
import rotp.addons.metallica.MetallicaPhase1Spec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class MetallicaIronService {
    private static final double PULL_ACCELERATION = 0.15D;

    private MetallicaIronService() {}

    public static void tickActiveUser(
            ServerPlayer user) {
        if (!(user.level() instanceof ServerLevel level)) {
            return;
        }
        AABB area = pullBox(user.position());
        pullItems(level, user, area);
        pullMinecarts(level, user, area);
        stripEquipment(level, user, area);
    }

    // 1.16 MetallicaStandType.tickUser and pullIron: x - range .. x + range + 1, y - range .. y + range and
    // z - range .. z + range + 1 around the user's position, not a box grown from its hitbox.
    private static AABB pullBox(Vec3 position) {
        double range = MetallicaPhase1Spec.MAX_RANGE;
        return new AABB(
                position.x - range,
                position.y - range,
                position.z - range,
                position.x + range + 1.0D,
                position.y + range,
                position.z + range + 1.0D);
    }

    private static void pullItems(
            ServerLevel level,
            ServerPlayer user,
            AABB area) {
        List<ItemEntity> items = level.getEntitiesOfClass(
                ItemEntity.class,
                area,
                item -> item.isAlive()
                        && item.getItem().is(
                                MetallicaAddon.IRON_ITEMS));
        for (ItemEntity item : items) {
            double distance = item.distanceTo(user);
            if (distance <= 1.0D) {
                item.playerTouch(user);
            } else {
                pullEntity(item, user.position(), distance);
            }
        }
    }

    private static void pullMinecarts(
            ServerLevel level,
            ServerPlayer user,
            AABB area) {
        List<AbstractMinecart> minecarts =
                level.getEntitiesOfClass(
                        AbstractMinecart.class,
                        area,
                        minecart -> minecart.isAlive()
                                && !minecart.getPassengers()
                                        .contains(user));
        for (AbstractMinecart minecart : minecarts) {
            pullEntity(
                    minecart,
                    user.position(),
                    minecart.distanceTo(user));
        }
    }

    private static void stripEquipment(
            ServerLevel level,
            ServerPlayer user,
            AABB area) {
        List<LivingEntity> targets =
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        // 1.16 gated each entity on its own tick count
                        target -> target != user
                                && target.isAlive()
                                && target.tickCount
                                        % MetallicaPhase1Spec
                                                .STRIP_INTERVAL_TICKS
                                        == 0);
        for (LivingEntity target : targets) {
            EquipmentSlot slot = selectCanonicalSlot(
                    target,
                    target.getRandom().nextFloat());
            if (slot == null) {
                continue;
            }
            ItemStack equipped =
                    target.getItemBySlot(slot);
            if (equipped.isEmpty()
                    || !equipped.is(
                            MetallicaAddon.IRON_ITEMS)) {
                continue;
            }
            ItemStack removed = equipped.copy();
            target.setItemSlot(slot, ItemStack.EMPTY);
            ItemEntity dropped = new ItemEntity(
                    level,
                    target.getX(),
                    target.getY() + 0.5D,
                    target.getZ(),
                    removed);
            dropped.setPickUpDelay(20);
            level.addFreshEntity(dropped);
        }
    }

    private static EquipmentSlot selectCanonicalSlot(
            LivingEntity target,
            float random) {
        if (target.getItemBySlot(EquipmentSlot.MAINHAND)
                .is(MetallicaAddon.IRON_ITEMS)) {
            return random > 0.75F
                    ? EquipmentSlot.MAINHAND : null;
        }
        if (target.getItemBySlot(EquipmentSlot.OFFHAND)
                .is(MetallicaAddon.IRON_ITEMS)) {
            return random < 0.15F
                    ? EquipmentSlot.OFFHAND : null;
        }
        if (target.getItemBySlot(EquipmentSlot.HEAD)
                .is(MetallicaAddon.IRON_ITEMS)) {
            return random > 0.15F && random < 0.30F
                    ? EquipmentSlot.HEAD : null;
        }
        if (target.getItemBySlot(EquipmentSlot.CHEST)
                .is(MetallicaAddon.IRON_ITEMS)) {
            return random > 0.30F && random < 0.45F
                    ? EquipmentSlot.CHEST : null;
        }
        if (target.getItemBySlot(EquipmentSlot.LEGS)
                .is(MetallicaAddon.IRON_ITEMS)) {
            return random > 0.45F && random < 0.60F
                    ? EquipmentSlot.LEGS : null;
        }
        if (target.getItemBySlot(EquipmentSlot.FEET)
                .is(MetallicaAddon.IRON_ITEMS)) {
            return random > 0.60F && random < 0.75F
                    ? EquipmentSlot.FEET : null;
        }
        return null;
    }

    private static void pullEntity(
            net.minecraft.world.entity.Entity entity,
            Vec3 destination,
            double distance) {
        Vec3 toward = destination.subtract(
                entity.getBoundingBox().getCenter());
        if (toward.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vec3 impulse = toward.normalize().scale(
                PULL_ACCELERATION);
        Vec3 motion = distance > 2.0D
                ? entity.getDeltaMovement().add(
                        impulse.scale(1.0D / distance))
                : impulse.scale(
                        Math.max(distance - 1.0D, 0.0D));
        entity.setDeltaMovement(motion);
        entity.hasImpulse = true;
    }
}
