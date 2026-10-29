package rotp.addons.frosttraveler.ability;

import java.util.Optional;

import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.standarrow.StandArrowItem;
import rotp.core.modcompat.ModInteractionUtil;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.util.functions.DamageUtil;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands.crazydiamond.CrazyDEyeOfEnderInsideEntity;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.frosttraveler.FrostTravelerAddon;
import rotp.addons.frosttraveler.FrostTravelerTags;
import rotp.addons.frosttraveler.ability.CreamStandActionAbility.Operation;
import rotp.addons.frosttraveler.item.CreamSprayerItem;
import rotp.addons.frosttraveler.item.DisguiseData;
import rotp.addons.frosttraveler.item.SprayerData;
import rotp.addons.frosttraveler.network.AttackStrengthResetPayload;
import rotp.addons.frosttraveler.particle.MeatParticleOptions;
import rotp.addons.frosttraveler.state.CreamStarterService;
import rotp.addons.frosttraveler.state.CreamStarterState;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CreamOperations {
    public static final double TARGET_RANGE = 4.5D;
    public static final double FILL_RANGE = 3.0D;
    public static final float SELF_HEAL_STAMINA = 10.0F;
    public static final float SELF_HEAL_AMOUNT = 0.25F;
    // 1.16 Healing healed while the user had more than 9 stamina.
    public static final float SELF_HEAL_STAMINA_FLOOR = 9.0F;
    public static final int SELF_HEAL_MAX_TICKS = 60;
    public static final float ITEM_INJECTION_STAMINA = 100.0F;
    public static final int DISGUISE_ITEM_AMMO = 50;

    private CreamOperations() {}

    public static boolean canBegin(
            Player player, Operation operation) {
        // 1.16 give_cs had no condition: pressed with the Stand away, it
        // summons it.
        if (operation == Operation.GIVE) {
            return true;
        }
        // 1.16 PutItemAction asked for power.isActive() after the press had
        // summoned the Stand and needed no Cream Starter, so pressed with the
        // Stand away it summoned it and went on. The other actions need the
        // sprayer, which only exists with the Stand out: 1.16 unsummoned the
        // Stand again after their refused press (afterClick), as here.
        if (operation == Operation.ITEM_INJECTION) {
            return canInjectOffhandItem(player);
        }
        if (!FrostTravelerAddon.isActiveUser(player)) {
            return false;
        }
        CreamStarterState state =
                CreamStarterService.state(player);
        ItemStack held =
                CreamStarterService.heldOwnedSprayer(player);
        return switch (operation) {
            case GIVE -> true;
            case SWITCH -> !held.isEmpty();
            case DEFORM ->
                    !held.isEmpty()
                            && findDeformDestination(player) != null;
            case HEAL -> !held.isEmpty() && state.ammo() > 0;
            case ITEM_FILL, ENTITY_FILL ->
                    !held.isEmpty()
                            && state.ammo()
                                    < CreamStarterState.MAX_AMMO;
            case DEFENSE ->
                    !held.isEmpty()
                            && impairmentTarget(player) instanceof LivingEntity target
                            && standMayTarget(player, target);
            case DISGUISE_ITEM ->
                    CreamStarterService.isOwnedSprayer(
                                    player,
                                    player.getMainHandItem())
                            && player.getOffhandItem().isEmpty()
                            && state.ammo() >= DISGUISE_ITEM_AMMO;
            case ITEM_INJECTION ->
                    canInjectOffhandItem(player);
        };
    }

    public static boolean hasMeatSource(Player player) {
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(FrostTravelerTags.EXCELLENT_MEAT)
                    || stack.is(FrostTravelerTags.GOOD_MEAT)
                    || stack.is(FrostTravelerTags.MID_MEAT)
                    || stack.is(FrostTravelerTags.BAD_MEAT)) {
                return true;
            }
        }
        return false;
    }

    public static void switchMode(Player player) {
        ItemStack sprayer =
                CreamStarterService.heldOwnedSprayer(player);
        if (!FrostTravelerAddon.isActiveUser(player)
                || sprayer.isEmpty()) {
            return;
        }
        CreamStarterState state =
                CreamStarterService.state(player);
        state.toggleMode();
        CreamStarterService.syncStateToStack(
                player, sprayer);
    }

    /**
     * 1.16 Healing.standTickPerform, on every perform tick of cs_heal's task: one cream is used if there is any, and
     * while the user has more than 9 stamina it heals a quarter heart with the spray at the Stand. StandEntityTask then
     * paid staminaCostTick(10) whatever the bar held; a short bar drops to 0 and the hold goes on.
     */
    public static void selfHealTick(
            Player player,
            StandEntity stand,
            StandPower power,
            EntityActionType ability) {
        if (power == null) {
            return;
        }
        CreamStarterState state =
                CreamStarterService.state(player);
        if (state.consumeAmmo(1)) {
            syncHeld(player);
        }
        if (power.isStaminaInfinite()
                || power.getStamina() > SELF_HEAL_STAMINA_FLOOR) {
            player.heal(SELF_HEAL_AMOUNT);
            player.level().playSound(
                    null,
                    stand.blockPosition(),
                    FrostTravelerAddon.CS_SHOT.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
        }
        StandAbilityStamina.consume(
                ability, power, SELF_HEAL_STAMINA, true);
    }

    public static boolean fillFromItem(Player player) {
        CreamStarterState state =
                CreamStarterService.state(player);
        ItemStack sprayer =
                CreamStarterService.heldOwnedSprayer(player);
        if (!FrostTravelerAddon.isActiveUser(player)
                || sprayer.isEmpty()
                || state.ammo() >= CreamStarterState.MAX_AMMO) {
            return false;
        }
        return consumeFirst(player, FrostTravelerTags.EXCELLENT_MEAT, 240)
                || consumeFirst(player, FrostTravelerTags.GOOD_MEAT, 80)
                || consumeFirst(player, FrostTravelerTags.MID_MEAT, 60)
                || consumeFirst(player, FrostTravelerTags.BAD_MEAT, 30);
    }

    private static boolean consumeFirst(
            Player player, TagKey<Item> tag, int amount) {
        CreamStarterState state =
                CreamStarterService.state(player);
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(tag)) {
                stack.shrink(1);
                state.setAmmo(state.ammo() + amount);
                syncHeld(player);
                return true;
            }
        }
        return false;
    }

    public static boolean fillFromEntity(Player player) {
        CreamStarterState state =
                CreamStarterService.state(player);
        ItemStack sprayer =
                CreamStarterService.heldOwnedSprayer(player);
        if (!FrostTravelerAddon.isActiveUser(player)
                || sprayer.isEmpty()
                || state.ammo() >= CreamStarterState.MAX_AMMO) {
            return false;
        }
        LivingEntity controller = FrostTravelerAddon.controller(player);
        ActionTarget clipped = HitResultUtil.clip(
                player.getEyePosition(),
                player.getLookAngle(),
                FILL_RANGE,
                FILL_RANGE,
                player.level(),
                entity -> entity != player && entity != controller,
                player,
                0.0D,
                // 1.16 FillingEntities: JojoModUtil.rayTrace with the OUTLINE block mode, so grass, a flower or a
                // torch in front is a block hit and not the miss that fills from the user
                ClipContext.Block.OUTLINE);
        LivingEntity target;
        if (clipped == null || clipped.isEmpty(player.level())) {
            target = player;
        } else if (HitResultUtil.isTargetWithinRange(
                clipped, player, player.level(), FILL_RANGE, FILL_RANGE)
                && clipped.getMainEntity() instanceof LivingEntity living
                && living.isAlive()) {
            target = living;
        } else {
            return false;
        }
        if (target != null
                && target.getType().is(
                        FrostTravelerTags.NO_MEATABLE)) {
            return false;
        }
        // 1.16 FillingEntities.fillEntities: under 0.5 health adds nothing.
        int amount = Math.round(target.getHealth());
        int rawAmmo = state.ammo() + amount;
        state.setAmmo(rawAmmo);
        CreamStarterService.syncStateToStack(player, sprayer);
        if (rawAmmo <= CreamStarterState.MAX_AMMO
                && player.getRandom().nextFloat() < 0.3F) {
            target.hurt(target.damageSources().generic(), 1.0F);
        }
        return true;
    }

    /** 1.16 DeformAction.standPerform: through the block its task holds, along the user's facing now. */
    public static boolean deform(Player player, BlockPos source) {
        BlockPos destination = deformDestination(player, source);
        if (destination == null) {
            return false;
        }
        BlockState head =
                player.level().getBlockState(destination.above());
        if (head.isSolid()) {
            player.setPose(Pose.CROUCHING);
        }
        BlockState below =
                player.level().getBlockState(destination.below());
        double yOffset = 0.0D;
        if (!below.isSolid()) {
            yOffset -= 1.0D;
            BlockState lower = player.level().getBlockState(
                    destination.below(2));
            if (lower.getBlock() instanceof FenceBlock
                    || lower.getBlock() instanceof WallBlock) {
                yOffset += 0.5D;
            }
        }
        if (below.getBlock() instanceof FenceBlock
                || below.getBlock() instanceof WallBlock) {
            yOffset += 0.5D;
        }
        player.teleportTo(
                destination.getX() + 0.5D,
                destination.getY() + yOffset,
                destination.getZ() + 0.5D);
        return true;
    }

    public static boolean impairTarget(Player player) {
        Impairment impairment = findImpairment(player);
        if (impairment == null) {
            return false;
        }
        // 1.16 DeformTargetAction: ambient, visible and showIcon all false.
        if (impairment.blind()) {
            impairment.target().addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS, 60, 0, false, false, false));
        } else if (impairment.slow()) {
            impairment.target().addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false, false));
        }
        player.level().playSound(
                null,
                impairment.target().blockPosition(),
                FrostTravelerAddon.CS_SHOT.get(),
                player.getSoundSource(),
                1.0F,
                1.0F);
        spawnImpairmentParticle(player, impairment.target());
        cooldownSprayer(player);
        // 1.16 cs_def was swingHand() without withUserPunch: going off swung
        // the main arm and reset the attack strength on both sides.
        player.swing(InteractionHand.MAIN_HAND, true);
        player.resetAttackStrengthTicker();
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(
                    serverPlayer, AttackStrengthResetPayload.INSTANCE);
        }
        return true;
    }

    public static boolean createDisguiseItem(Player player) {
        CreamStarterState state =
                CreamStarterService.state(player);
        ItemStack main = player.getMainHandItem();
        if (!CreamStarterService.isOwnedSprayer(player, main)
                || !player.getOffhandItem().isEmpty()
                || state.ammo() < DISGUISE_ITEM_AMMO) {
            return false;
        }
        if (!state.consumeAmmo(DISGUISE_ITEM_AMMO)) {
            return false;
        }
        ItemStack mask = new ItemStack(
                FrostTravelerAddon.MEAT_DGS_ITEM.get());
        mask.set(
                FrostTravelerAddon.DISGUISE_DATA,
                DisguiseData.blank(player.getUUID()));
        player.setItemInHand(
                net.minecraft.world.InteractionHand.OFF_HAND,
                mask);
        syncHeld(player);
        return true;
    }

    /**
     * 1.16 PutItemAction.standPerform: the offhand item goes into the entity its task holds. The stamina is the
     * action's, paid at the perform whatever this does (CreamStandActionAbility).
     */
    public static boolean injectOffhandItem(
            Player player, LivingEntity target) {
        ItemStack item = player.getOffhandItem();
        if (!FrostTravelerAddon.isActiveUser(player)
                || item.isEmpty()
                || !isInjectableItem(item)
                || target == null
                || !target.isAlive()
                || target.getType().is(
                        FrostTravelerTags.NO_MEATABLE)
                || !applyItemEffect(target, item, player)) {
            return false;
        }
        item.shrink(1);
        player.level().playSound(
                null,
                player.blockPosition(),
                FrostTravelerAddon.CS_SHOT.get(),
                player.getSoundSource(),
                1.0F,
                1.0F);
        return true;
    }

    // 1.16 PutItemAction checked no stamina: staminaCost(100) was only paid at the perform.
    private static boolean canInjectOffhandItem(Player player) {
        ItemStack item = player.getOffhandItem();
        LivingEntity target = injectionTarget(player);
        return !item.isEmpty()
                && isInjectableItem(item)
                && target != null
                && !target.getType().is(
                        FrostTravelerTags.NO_MEATABLE)
                && standMayTarget(player, target);
    }

    /**
     * 1.16 StandEntityAction.checkStandTarget, which refused an ENTITY target silently, on the press and on every
     * held tick, unless StandEntity.canAttack let the Stand attack it. cs_item's press had summoned the Stand first,
     * so with the Stand away the same rules are asked of its user: no friendly fire, not an animal riding with the
     * user, not the user's own tamed animal.
     */
    public static boolean standMayTarget(
            Player player, LivingEntity target) {
        StandEntity stand = FrostTravelerAddon.controller(player);
        if (stand != null) {
            return stand.canAttackEntity(target);
        }
        if (target == player
                || !DamageUtil.isNotFriendlyFire(
                        player, StandUtil.getStandUser(target))) {
            return false;
        }
        if (target instanceof Animal) {
            if (target.isPassengerOfSameVehicle(player)) {
                return false;
            }
            if (target instanceof TamableAnimal tameable
                    && tameable.getOwner() == player) {
                return false;
            }
        }
        return true;
    }

    /** The entity cs_item is pressed at. */
    public static LivingEntity injectionTarget(Player player) {
        return findLivingTarget(player, TARGET_RANGE);
    }

    /** The entity cs_def is pressed at. */
    public static LivingEntity impairmentTarget(Player player) {
        Impairment impairment = findImpairment(player);
        return impairment != null ? impairment.target() : null;
    }

    /**
     * 1.16 PutItemAction.onTaskStopped released the main-hand item whenever the task stopped, fired or not: the
     * Cream Starter's releaseUsing put it on cooldown.
     */
    public static void releaseMainHandSprayer(Player player) {
        if (CreamStarterService.isOwnedSprayer(
                player, player.getMainHandItem())) {
            cooldownSprayer(player);
        }
    }

    private static void cooldownSprayer(Player player) {
        player.getCooldowns().addCooldown(
                FrostTravelerAddon.CREAM_STARTER_ITEM.get(),
                CreamSprayerItem.RELEASE_COOLDOWN);
        ItemStack stack =
                CreamStarterService.heldOwnedSprayer(player);
        SprayerData data = stack.get(
                FrostTravelerAddon.SPRAYER_DATA);
        if (data != null) {
            stack.set(
                    FrostTravelerAddon.SPRAYER_DATA,
                    data.withTransformationTicks(0));
        }
    }

    private static void spawnImpairmentParticle(
            Player player, LivingEntity target) {
        if (!(player.level()
                instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 start = player.getEyePosition();
        Vec3 delta = target.getBoundingBox()
                .getCenter()
                .subtract(start);
        serverLevel.sendParticles(
                new MeatParticleOptions(player.getUUID()),
                player.getX(),
                player.getY()
                        + player.getBbHeight() * 0.625D,
                player.getZ(),
                0,
                delta.x,
                delta.y,
                delta.z,
                1.0D);
    }

    private static BlockPos findDeformDestination(Player player) {
        BlockHitResult hit = deformSource(player);
        return hit != null
                ? deformDestination(player, hit.getBlockPos())
                : null;
    }

    /** The block cs_deform is pressed at. */
    public static BlockHitResult deformSource(Player player) {
        return FrostTravelerAddon.isActiveUser(player)
                ? aimedBlock(player)
                : null;
    }

    /**
     * The block the user looks at within reach, the target 1.16 cs_deform checked. That was the vanilla crosshair
     * target: a pickable entity before the block (within the entity reach, or past it, which vanilla makes a miss)
     * took its place, and a BLOCK action refused the press without a message.
     */
    public static BlockHitResult aimedBlock(Player player) {
        if (!(player.pick(TARGET_RANGE, 1.0F, false)
                        instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        double blockDistance = Math.sqrt(
                eye.distanceToSqr(hit.getLocation()));
        return HitResultUtil.clipMultipleEntities(
                        eye,
                        player.getLookAngle(),
                        blockDistance,
                        player.level(),
                        player,
                        null,
                        0.0D,
                        0.0D).isEmpty()
                ? hit
                : null;
    }

    /**
     * 1.16 DeformAction.checkTarget and getTargetBlock: the space past the block along the user's facing now, if
     * the block lets a user through that way and the space is free. The sprayer is checked by the caller.
     */
    public static BlockPos deformDestination(
            Player player, BlockPos source) {
        if (!FrostTravelerAddon.isActiveUser(player)) {
            return null;
        }
        BlockState sourceState =
                player.level().getBlockState(source);
        float pitch = player.getXRot();
        if (Math.abs(pitch) == 75.0F) {
            return null;
        }
        Direction through;
        if (Math.abs(pitch) > 75.0F) {
            if (!sourceState.is(FrostTravelerTags.VERTICAL)) {
                return null;
            }
            through = pitch > 0.0F
                    ? Direction.DOWN
                    : Direction.UP;
        } else {
            if (!sourceState.is(FrostTravelerTags.HORIZONTAL)) {
                return null;
            }
            through = player.getDirection();
        }
        BlockPos destination = source.relative(through);
        return hasDeformSpace(player.level().getBlockState(destination))
                ? destination
                : null;
    }

    private static boolean hasDeformSpace(BlockState state) {
        return !state.blocksMotion()
                || state.liquid()
                || state.is(Tags.Blocks.GLASS_BLOCKS)
                || state.is(Tags.Blocks.GLASS_PANES)
                || state.is(BlockTags.LEAVES)
                || state.getBlock() instanceof IceBlock;
    }

    private static Impairment findImpairment(Player player) {
        LivingEntity target =
                findLivingTarget(player, TARGET_RANGE);
        if (target == null) {
            return null;
        }
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(
                player.getLookAngle().scale(TARGET_RANGE));
        Vec3 hit = target.getBoundingBox()
                .clip(start, end)
                .orElse(target.getBoundingBox().getCenter());
        double eyeY = target.getY() + target.getEyeHeight();
        Vec3 targetToPlayer = player.getEyePosition()
                .subtract(target.getEyePosition())
                .normalize();
        boolean blind = Math.abs(hit.y - eyeY) < 0.35D
                && target.getLookAngle()
                        .dot(targetToPlayer) > 0.5D;
        boolean slow =
                Math.abs(hit.y - (target.getY() + 0.25D))
                        < 0.5D;
        return blind || slow
                ? new Impairment(target, blind, slow)
                : null;
    }

    private static LivingEntity findLivingTarget(
            Player player, double range) {
        ActionTarget clipped = HitResultUtil.clip(
                player.getEyePosition(),
                player.getLookAngle(),
                range,
                range,
                player.level(),
                entity -> entity instanceof LivingEntity
                        && entity != player,
                player,
                0.0D);
        if (clipped == null
                || clipped.isEmpty(player.level())
                || !HitResultUtil.isTargetWithinRange(
                        clipped,
                        player,
                        player.level(),
                        range,
                        range)) {
            return null;
        }
        return clipped.getMainEntity()
                instanceof LivingEntity target
                && target.isAlive()
                ? target
                : null;
    }

    private static void syncHeld(Player player) {
        CreamStarterService.syncStateToStack(
                player,
                CreamStarterService.heldOwnedSprayer(player));
    }

    private static boolean applyItemEffect(
            LivingEntity target,
            ItemStack item,
            LivingEntity user) {
        if (item.is(Items.CHORUS_FRUIT)) {
            chorusFruitTeleport(target, user);
        } else if (item.is(Items.ENDER_EYE)) {
            // 1.16 ITEM_ACTION fired on the item alone: without a
            // stronghold the eye is still used up.
            enderEyeFlight(target, item, user);
        } else if (item.is(Items.SNOWBALL)) {
            target.addEffect(new MobEffectInstance(
                    ModStatusEffects.FREEZE, 40, 0));
        } else if (item.is(Items.SNOW)) {
            target.addEffect(new MobEffectInstance(
                    ModStatusEffects.FREEZE, 80, 0));
        } else if (item.is(Items.SNOW_BLOCK)) {
            target.addEffect(new MobEffectInstance(
                    ModStatusEffects.FREEZE, 120, 1));
        } else if (item.is(Items.ICE)) {
            target.addEffect(new MobEffectInstance(
                    ModStatusEffects.FREEZE, 200, 1));
        } else if (item.is(Items.PACKED_ICE)) {
            target.addEffect(new MobEffectInstance(
                    ModStatusEffects.FREEZE, 200, 2));
        } else if (item.is(Items.BLUE_ICE)) {
            target.addEffect(new MobEffectInstance(
                    ModStatusEffects.FREEZE, 200, 3));
        } else if (item.is(Items.FIRE_CHARGE)) {
            DamageUtil.setOnFire(target, 5 * 20, false);
        } else if (item.is(Items.BLAZE_POWDER)) {
            DamageUtil.setOnFire(target, 10 * 20, false);
        } else if (item.is(Items.LAVA_BUCKET)
                || item.is(Items.BLAZE_ROD)) {
            DamageUtil.setOnFire(target, 20 * 20, false);
        } else if (item.is(Items.GLOWSTONE_DUST)) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.GLOWING, 100, 0));
        } else if (item.is(Items.SPECTRAL_ARROW)) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.GLOWING, 200, 0));
        } else if (item.is(Items.GLOWSTONE)) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.GLOWING, 400, 0));
        } else if (item.is(Items.MILK_BUCKET)) {
            target.removeEffectsCuredBy(EffectCures.MILK);
        } else if (item.getItem() instanceof StandArrowItem) {
            return pierceWithStandArrow(target, item, user);
        } else {
            PotionContents potion =
                    item.get(DataComponents.POTION_CONTENTS);
            if (potion != null && potion.hasEffects()) {
                potion.forEachEffect(effect ->
                        target.addEffect(
                                new MobEffectInstance(effect)));
            } else if (item.get(DataComponents.FOOD) != null) {
                target.eat(
                        target.level(), item.copyWithCount(1));
                if (item.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                    VampirismData
                            .startCuringFromEnchantedGoldenApple(
                                    target);
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private static boolean isInjectableItem(ItemStack item) {
        PotionContents potion =
                item.get(DataComponents.POTION_CONTENTS);
        return item.is(Items.CHORUS_FRUIT)
                || item.is(Items.ENDER_EYE)
                || item.is(Items.SNOWBALL)
                || item.is(Items.SNOW)
                || item.is(Items.SNOW_BLOCK)
                || item.is(Items.ICE)
                || item.is(Items.PACKED_ICE)
                || item.is(Items.BLUE_ICE)
                || item.is(Items.FIRE_CHARGE)
                || item.is(Items.BLAZE_POWDER)
                || item.is(Items.BLAZE_ROD)
                || item.is(Items.LAVA_BUCKET)
                || item.is(Items.GLOWSTONE_DUST)
                || item.is(Items.SPECTRAL_ARROW)
                || item.is(Items.GLOWSTONE)
                || item.is(Items.MILK_BUCKET)
                || item.getItem() instanceof StandArrowItem
                || potion != null && potion.hasEffects()
                || item.get(DataComponents.FOOD) != null;
    }

    private static void chorusFruitTeleport(
            LivingEntity target, LivingEntity user) {
        if (ModInteractionUtil.isEntityEnderman(target)) {
            target.heal(6.0F);
            return;
        }
        double previousX = target.getX();
        double previousY = target.getY();
        double previousZ = target.getZ();
        for (int attempt = 0; attempt < 16; attempt++) {
            double y = Mth.clamp(
                    target.getY()
                            + target.getRandom().nextInt(16)
                            - 8.0D,
                    target.level().getMinBuildHeight(),
                    target.level().getMaxBuildHeight() - 1.0D);
            Vec3 middle = target.position().add(
                    user.getLookAngle().scale(12.0D));
            double x = middle.x
                    + (target.getRandom().nextDouble() - 0.5D)
                            * 8.0D;
            double z = middle.z
                    + (target.getRandom().nextDouble() - 0.5D)
                            * 8.0D;
            if (target.isPassenger()) {
                target.stopRiding();
            }
            EntityTeleportEvent.ChorusFruit event =
                    EventHooks.onChorusFruitTeleport(
                            target, x, y, z);
            if (!event.isCanceled()
                    && target.randomTeleport(
                            event.getTargetX(),
                            event.getTargetY(),
                            event.getTargetZ(),
                            true)) {
                float yaw = target.getRandom().nextFloat() * 360.0F;
                target.setYRot(yaw);
                target.yRotO = yaw;
                var sound = target instanceof Fox
                        ? SoundEvents.FOX_TELEPORT
                        : SoundEvents.CHORUS_FRUIT_TELEPORT;
                target.level().playSound(
                        null,
                        previousX,
                        previousY,
                        previousZ,
                        sound,
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F);
                target.playSound(sound, 1.0F, 1.0F);
                break;
            }
        }
    }

    private static void enderEyeFlight(
            LivingEntity target,
            ItemStack item,
            LivingEntity user) {
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos stronghold = serverLevel.findNearestMapStructure(
                StructureTags.EYE_OF_ENDER_LOCATED,
                target.blockPosition(),
                100,
                false);
        if (stronghold == null) {
            return;
        }
        CrazyDEyeOfEnderInsideEntity eye =
                new CrazyDEyeOfEnderInsideEntity(
                        serverLevel, target);
        eye.setItem(item.copyWithCount(1));
        eye.signalTo(stronghold);
        playEnderEyeLaunch(serverLevel, target);
        serverLevel.levelEvent(
                null, 1003, target.blockPosition(), 0);
        serverLevel.addFreshEntity(eye);
        if (target instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.USED_ENDER_EYE.trigger(
                    serverPlayer, stronghold);
        }
        if (user instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.USED_ENDER_EYE.trigger(
                    serverPlayer, stronghold);
        }
    }

    /** 1.16 PutItemAction.enderEyeFlight: the vanilla ender eye throw's sound, pitched by the target's random. */
    public static void playEnderEyeLaunch(
            ServerLevel serverLevel, LivingEntity target) {
        serverLevel.playSound(
                null,
                target.getX(),
                target.getY(),
                target.getZ(),
                SoundEvents.ENDER_EYE_LAUNCH,
                SoundSource.NEUTRAL,
                0.5F,
                0.4F / (target.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    private static boolean pierceWithStandArrow(
            LivingEntity target,
            ItemStack item,
            LivingEntity user) {
        if (target.level().isClientSide()
                || rotp.core.powersystem
                        .standpower.StandUtil
                        .isEntityStandUser(target)) {
            return false;
        }
        boolean pierced = StandArrowItem.onPiercedByArrow(
                target,
                item,
                target.level(),
                Optional.ofNullable(user));
        if (pierced && !StandArrowItem.isInvulnerable(target)) {
            StandArrowItem.dealDamageFromArrow(
                    target,
                    item,
                    user,
                    user,
                    false,
                    false);
        }
        if (pierced
                && target.level()
                        instanceof ServerLevel serverLevel) {
            ItemStack saved = item.copy();
            item.hurtAndBreak(
                    1,
                    serverLevel,
                    target,
                    broken -> StandArrowItem.onBreakArrow(
                            serverLevel,
                            target,
                            null,
                            null,
                            broken,
                            saved));
        }
        return pierced;
    }

    private record Impairment(
            LivingEntity target, boolean blind, boolean slow) {}
}
