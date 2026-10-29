package rotp.addons.emperor.item;

import java.util.Comparator;
import java.util.List;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.emperor.EmperorAddon;
import rotp.addons.emperor.entity.EmperorBulletEntity;
import rotp.addons.emperor.entity.EmperorStandEntity;
import rotp.addons.emperor.state.EmperorFireRules;
import rotp.addons.emperor.state.EmperorMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class EmperorItem extends Item {
    public static final int USE_DURATION =
            EmperorFireRules.USE_DURATION;
    public static final int NORMAL_COOLDOWN =
            EmperorFireRules.NORMAL_COOLDOWN;
    public static final int BURST_COOLDOWN =
            EmperorFireRules.BURST_COOLDOWN;
    public static final float NORMAL_STAMINA =
            EmperorFireRules.NORMAL_STAMINA;
    public static final float BURST_STAMINA =
            EmperorFireRules.BURST_STAMINA;
    public static final float RESOLVE_COST_MULTIPLIER =
            EmperorFireRules.RESOLVE_COST_MULTIPLIER;
    public static final float BULLET_SPEED =
            EmperorFireRules.BULLET_SPEED;
    public static final double AUTO_TARGET_HORIZONTAL =
            EmperorFireRules.AUTO_TARGET_HORIZONTAL;
    public static final double AUTO_TARGET_VERTICAL =
            EmperorFireRules.AUTO_TARGET_VERTICAL;

    public EmperorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isActiveOwnedGun(player, stack)
                || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(
            Level level,
            LivingEntity entity,
            ItemStack stack,
            int remainingTicks) {
        if (level.isClientSide()
                || !(entity instanceof Player player)
                || !isActiveOwnedGun(player, stack)) {
            return;
        }

        boolean burst = player.isShiftKeyDown();
        int cadence = remainingTicks % 13;
        boolean shotTick = EmperorFireRules.isShotTick(
                burst, remainingTicks);

        boolean fired = shotTick && fireServer(player, burst);
        if ((!burst && fired) || (burst && cadence == 1)) {
            player.releaseUsingItem();
        }
    }

    @Override
    public void releaseUsing(
            ItemStack stack,
            Level level,
            LivingEntity entity,
            int remainingTicks) {
        if (!level.isClientSide()
                && entity instanceof Player player) {
            player.getCooldowns().addCooldown(
                    this,
                    EmperorFireRules.cooldown(
                            player.isShiftKeyDown()));
        }
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(
            ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int itemSlot,
            boolean isSelected) {
        if (!level.isClientSide()
                && entity instanceof Player player
                && !isActiveOwnedGun(player, stack)) {
            stack.setCount(0);
        }
    }

    public static boolean isOwnedGunInHand(Player player) {
        return player.getMainHandItem().is(
                        EmperorAddon.EMPEROR_ITEM.get())
                || player.getOffhandItem().is(
                        EmperorAddon.EMPEROR_ITEM.get());
    }

    public static boolean isActiveOwnedGun(
            Player player, ItemStack stack) {
        return stack.is(EmperorAddon.EMPEROR_ITEM.get())
                && EmperorAddon.controller(player) != null
                && (player.getMainHandItem() == stack
                        || player.getOffhandItem() == stack);
    }

    public static void enforceOwnedGun(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        EmperorStandEntity controller =
                EmperorAddon.controller(player);
        if (controller == null) {
            purgeAll(player);
            return;
        }

        ItemStack keep = player.getMainHandItem().is(
                EmperorAddon.EMPEROR_ITEM.get())
                ? player.getMainHandItem()
                : player.getOffhandItem().is(
                        EmperorAddon.EMPEROR_ITEM.get())
                        ? player.getOffhandItem()
                        : ItemStack.EMPTY;
        if (keep.isEmpty()) {
            StandPower power = StandPower.get(player);
            if (power != null
                    && power.getPowerType()
                            == EmperorAddon.THE_EMPEROR.get()) {
                EmperorAddon.THE_EMPEROR.get()
                        .forceUnsummon(player, power);
            } else {
                purgeAll(player);
            }
            return;
        }
        purgeExcept(player, keep);
    }

    public static void purgeAll(Player player) {
        purgeExcept(player, ItemStack.EMPTY);
    }

    private static void purgeExcept(
            Player player, ItemStack keep) {
        for (int i = 0;
                i < player.getInventory().getContainerSize();
                i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack != keep
                    && stack.is(EmperorAddon.EMPEROR_ITEM.get())) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        if (player.containerMenu != null) {
            for (Slot slot : player.containerMenu.slots) {
                ItemStack stack = slot.getItem();
                if (stack != keep
                        && stack.is(
                                EmperorAddon.EMPEROR_ITEM.get())) {
                    slot.set(ItemStack.EMPTY);
                }
            }
            ItemStack carried = player.containerMenu.getCarried();
            if (carried != keep
                    && carried.is(EmperorAddon.EMPEROR_ITEM.get())) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
            }
        }
    }

    private static boolean fireServer(
            Player player, boolean burst) {
        StandPower power = StandPower.get(player);
        EmperorStandEntity controller =
                EmperorAddon.controller(player);
        if (power == null
                || power.getPowerType()
                        != EmperorAddon.THE_EMPEROR.get()
                || controller == null) {
            return false;
        }

        boolean resolve = player.hasEffect(ModStatusEffects.RESOLVE);
        float cost = EmperorFireRules.staminaCost(
                burst, resolve);
        if (!power.isStaminaInfinite()
                && power.getStamina() < cost) {
            return false;
        }
        if (!power.consumeStamina(cost)) {
            return false;
        }

        EmperorBulletEntity bullet =
                new EmperorBulletEntity(player, player.level());
        bullet.setBaseDamage(EmperorFireRules.shotDamage(
                burst, resolve));
        LivingEntity target = controller.getTargetEntity();
        if (target == null) {
            target = autoTarget(player);
        }
        bullet.setTargetEntity(target);
        bullet.shootFromRotation(player, BULLET_SPEED, 0.0F);
        player.level().addFreshEntity(bullet);
        player.level().playSound(
                null,
                player.blockPosition(),
                EmperorAddon.EMP_SHOT.get(),
                player.getSoundSource(),
                1.0F,
                1.0F);
        return true;
    }

    private static LivingEntity autoTarget(Player player) {
        EmperorMode mode = EmperorAddon.modeState(player).mode();
        if (mode == EmperorMode.NONE) {
            return null;
        }
        List<LivingEntity> targets = player.level()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        player.getBoundingBox().inflate(
                                AUTO_TARGET_HORIZONTAL,
                                AUTO_TARGET_VERTICAL,
                                AUTO_TARGET_HORIZONTAL),
                        target -> canAutoTarget(
                                player, target, mode));
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        return targets.stream()
                .max(Comparator.comparingDouble(target -> {
                    Vec3 direction = target.getBoundingBox()
                            .getCenter()
                            .subtract(eye);
                    return direction.lengthSqr() > 1.0E-7D
                            ? look.dot(direction.normalize())
                            : -1.0D;
                }))
                .orElse(null);
    }

    private static boolean canAutoTarget(
            Player player,
            LivingEntity target,
            EmperorMode mode) {
        if (!target.isAlive()
                || target == player
                || target instanceof EmperorStandEntity
                || !JojoModUtil.canHarm(player, target)) {
            return false;
        }
        return switch (mode) {
            case NONE -> false;
            case HOSTILE -> target instanceof Monster;
            case PLAYERS_AND_STANDS ->
                target instanceof Player
                        || target instanceof StandEntity;
            case ANY_LIVING -> true;
        };
    }
}
