package com.github.standobyte.jojoimpl.stands.goldexperience;

import java.util.Comparator;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.power.ModStandAbilities;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.subsystems.itemtracking.ItemTracker;
import com.github.standobyte.jojo.subsystems.itemtracking.ItemTracking;
import com.github.standobyte.jojo.subsystems.itemtracking.KnownItemState;
import com.github.standobyte.jojo.util.functions.ItemUtil;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class GoldExperienceMarkItemAbility extends GoldExperienceUtilityAbility {
    public static final double MARKED_ITEM_TARGET_RANGE = 128.0D;
    private static final double MARKED_ITEM_LOOK_MIN_DOT = 0.92388D;

    public GoldExperienceMarkItemAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        partsRequired(StandPart.ARMS);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        LivingEntity user = context.getUser();
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        ItemStack offHandItem = user.getOffhandItem();
        if (offHandItem.isEmpty()) {
            return ConditionCheck.createNegative("ge_lifeform_material_only_item");
        }
        if (!GoldExperienceCreateLifeformAbility.canGiveLifeTo(offHandItem)) {
            return ConditionCheck.createNegative("ge_lifeform_material_item");
        }
        return super.checkSpecificConditions(context);
    }

    @Nullable
    public static ItemTracker getTargetedMarkedItem(StandPower standPower, LivingEntity player) {
        GEItemMarkEffect effect = getTargetedEffect(standPower, player);
        return effect != null ? effect.getItemTracker(false) : null;
    }

    public static boolean hasMarkedItemTracker(StandPower standPower, ItemTracker itemTracker) {
        return standPower != null && itemTracker != null
                && standPower.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_GE_ITEM_MARK.get())
                        .anyMatch(effect -> itemTracker.trackerId.equals(effect.getItemTrackerId()));
    }

    @Nullable
    public static GEItemMarkEffect getTargetedEffect(StandPower standPower, LivingEntity player) {
        if (standPower == null || player == null) {
            return null;
        }
        Level level = player.level();
        Vec3 lookAngle = player.getLookAngle();
        Vec3 eyePos = player.getEyePosition(1.0F);
        double rangeSqr = MARKED_ITEM_TARGET_RANGE * MARKED_ITEM_TARGET_RANGE;
        return standPower.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_GE_ITEM_MARK.get())
                .map(effect -> {
                    ItemTracker tracker = effect.getItemTracker(true);
                    Vec3 markerPos = tracker != null ? tracker.getPos(level, 1.0F) : null;
                    double lookDot = markerPos != null ? lookAngle.dot(markerPos.subtract(eyePos).normalize()) : -1.0D;
                    return new MarkedItemTarget(effect, tracker, markerPos, lookDot);
                })
                .filter(target -> target.tracker() != null
                        && target.markerPos() != null
                        && target.tracker().getAtEntity(level) != player
                        && target.markerPos().distanceToSqr(player.position()) < rangeSqr
                        && target.lookDot() > MARKED_ITEM_LOOK_MIN_DOT)
                .max(Comparator.comparingDouble(MarkedItemTarget::lookDot))
                .map(MarkedItemTarget::effect)
                .orElse(null);
    }

    private static record MarkedItemTarget(GEItemMarkEffect effect, @Nullable ItemTracker tracker,
            @Nullable Vec3 markerPos, double lookDot) {}

    @Override
    public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(user instanceof ServerPlayer serverPlayer)) {
            return;
        }
        StandPower standPower = StandPower.get(user);
        if (standPower == null) {
            return;
        }

        ItemStack heldItem = user.getItemInHand(InteractionHand.OFF_HAND);
        if (heldItem.isEmpty() || !GoldExperienceCreateLifeformAbility.canGiveLifeTo(heldItem)) {
            return;
        }

        ItemStack markedStack;
        boolean give = false;
        if (heldItem.getCount() == 1) {
            markedStack = heldItem;
            ItemStack mainHandItem = user.getItemInHand(InteractionHand.MAIN_HAND);
            if (!mainHandItem.isEmpty()) {
                user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                give = true;
            }
            else {
                user.setItemInHand(InteractionHand.OFF_HAND, mainHandItem);
                user.setItemInHand(InteractionHand.MAIN_HAND, heldItem);
            }
        }
        else {
            markedStack = heldItem.split(1);
            if (user.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
                user.setItemInHand(InteractionHand.MAIN_HAND, markedStack);
            }
            else {
                give = true;
            }
        }

        user.stopUsingItem();
        standPower.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_GE_ITEM_MARK.get())
                .toList()
                .forEach(standPower.userStandEffects::removeEffect);

        ItemTracker itemTracker = ItemTracking.getItemTracking(level).startTracking(markedStack, serverLevel);
        itemTracker.setTrackedByPlayer(serverPlayer);
        itemTracker.context = "ge_item_mark";
        itemTracker.setAtEntity(markedStack, user.getId(), level, KnownItemState.ENTITY_HAS_ITEM,
                trackerId -> ItemTracking.hasTrackerId(markedStack, trackerId));

        GEItemMarkEffect effect = ModStandAbilities.EFFECT_GE_ITEM_MARK.get().create(level);
        effect.withItemTracker(itemTracker);
        standPower.userStandEffects.addEffect(effect);

        StandUtil.broadcastSound(serverLevel, user.position(),
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSoundEvents.GOLD_EXPERIENCE_LIFE_ITEM.get()),
                true, standPower, user.getSoundSource(), 0.5F, 1.0F);

        if (give) {
            ItemUtil.giveItemTo(user, markedStack, true);
        }
    }
}
