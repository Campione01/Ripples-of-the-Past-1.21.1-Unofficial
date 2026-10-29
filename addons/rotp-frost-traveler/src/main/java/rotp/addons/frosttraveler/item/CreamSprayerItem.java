package rotp.addons.frosttraveler.item;

import rotp.addons.frosttraveler.FrostTravelerAddon;
import rotp.addons.frosttraveler.FrostTravelerTags;
import rotp.addons.frosttraveler.entity.HealSprayEntity;
import rotp.addons.frosttraveler.entity.SprayBulletEntity;
import rotp.addons.frosttraveler.state.CreamStarterService;
import rotp.addons.frosttraveler.state.CreamStarterState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public final class CreamSprayerItem extends Item {
    public static final int USE_DURATION = 40;
    public static final int RELEASE_COOLDOWN = 20;
    public static final float PROJECTILE_SPEED = 5.0F;
    public static final float PROJECTILE_INACCURACY = 0.5F;

    public CreamSprayerItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        SprayerData data = stack.get(
                FrostTravelerAddon.SPRAYER_DATA);
        Component mode = Component.translatable(
                data != null
                        && data.mode()
                                == CreamStarterState.SprayMode.HEAL
                        ? "rotp_zcs.mode.heal"
                        : "rotp_zcs.mode.attack");
        return Component.translatable(
                "item.rotp_zcs.cream_starter", mode);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CreamStarterState state = CreamStarterService.state(player);
        if (!CreamStarterService.isOwnedSprayer(player, stack)
                || !FrostTravelerAddon.isActiveUser(player)
                || state.ammo() <= 0
                || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(
            Level level,
            LivingEntity living,
            ItemStack stack,
            int remainingTicks) {
        if (level.isClientSide()
                || !(living instanceof Player player)
                || !CreamStarterService.isOwnedSprayer(player, stack)
                || !FrostTravelerAddon.isActiveUser(player)) {
            return;
        }
        if (remainingTicks <= 1) {
            player.releaseUsingItem();
            return;
        }
        CreamStarterState state = CreamStarterService.state(player);
        if (!state.consumeAmmo(1)) {
            player.releaseUsingItem();
            return;
        }
        CreamStarterService.syncStateToStack(player, stack);

        if (state.mode() == CreamStarterState.SprayMode.HEAL) {
            HealSprayEntity projectile =
                    new HealSprayEntity(player, level);
            projectile.shootFromRotation(
                    player,
                    PROJECTILE_SPEED,
                    PROJECTILE_INACCURACY);
            level.addFreshEntity(projectile);
        } else {
            SprayBulletEntity projectile =
                    new SprayBulletEntity(player, level);
            projectile.shootFromRotation(
                    player,
                    PROJECTILE_SPEED,
                    PROJECTILE_INACCURACY);
            level.addFreshEntity(projectile);
        }
        level.playSound(
                null,
                player.blockPosition(),
                FrostTravelerAddon.CS_SHOT.get(),
                player.getSoundSource(),
                1.0F,
                1.0F);
    }

    @Override
    public void releaseUsing(
            ItemStack stack,
            Level level,
            LivingEntity living,
            int remainingTicks) {
        if (!level.isClientSide()
                && living instanceof Player player) {
            player.getCooldowns().addCooldown(
                    this, RELEASE_COOLDOWN);
            SprayerData data = stack.get(
                    FrostTravelerAddon.SPRAYER_DATA);
            if (data != null) {
                stack.set(
                        FrostTravelerAddon.SPRAYER_DATA,
                        data.withTransformationTicks(0));
            }
        }
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public boolean hurtEnemy(
            ItemStack stack,
            LivingEntity target,
            LivingEntity attacker) {
        if (attacker instanceof Player player
                && CreamStarterService.isOwnedSprayer(
                        player, stack)
                && !target.getType().is(
                        FrostTravelerTags.NO_MEATABLE)) {
            CreamStarterState state =
                    CreamStarterService.state(player);
            if (state.ammo() < CreamStarterState.MAX_AMMO) {
                state.setAmmo(
                        state.ammo()
                                + 2 * Math.round(
                                        target.getHealth()));
                CreamStarterService.syncStateToStack(
                        player, stack);
            }
        }
        return false;
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
            boolean selected) {
        if (level.isClientSide()) {
            return;
        }
        SprayerData data = stack.get(
                FrostTravelerAddon.SPRAYER_DATA);
        if (data == null
                || !(level.getServer()
                        .getPlayerList()
                        .getPlayer(data.ownerUuid())
                        instanceof ServerPlayer owner)
                || !FrostTravelerAddon.isActiveUser(owner)) {
            stack.setCount(0);
        }
    }

    @Override
    public boolean onEntityItemUpdate(
            ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide()) {
            SprayerData data = stack.get(
                    FrostTravelerAddon.SPRAYER_DATA);
            Player owner = data != null
                    ? entity.level()
                            .getServer()
                            .getPlayerList()
                            .getPlayer(data.ownerUuid())
                    : null;
            if (owner == null
                    || !FrostTravelerAddon.isActiveUser(owner)) {
                entity.discard();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        SprayerData data = stack.get(
                FrostTravelerAddon.SPRAYER_DATA);
        return data != null
                && data.ammo() < CreamStarterState.MAX_AMMO;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        SprayerData data = stack.get(
                FrostTravelerAddon.SPRAYER_DATA);
        return data != null
                ? Math.round(
                        13.0F * data.ammo()
                                / CreamStarterState.MAX_AMMO)
                : 0;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        SprayerData data = stack.get(
                FrostTravelerAddon.SPRAYER_DATA);
        float fraction = data != null
                ? (float) data.ammo()
                        / CreamStarterState.MAX_AMMO
                : 0.0F;
        return Mth.hsvToRgb(fraction / 3.0F, 1.0F, 1.0F);
    }
}
