package com.github.standobyte.jojoimpl.stands.goldexperience;

import java.util.Optional;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.power.ModStandAbilities;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.AbilityUsageGroup;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.effect.UserStandEffects;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class GoldExperienceRevertLifeformAbility extends GoldExperienceUtilityAbility {
    private static final double MAX_LIFEFORM_DISTANCE = 128.0;

    public GoldExperienceRevertLifeformAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        usageGroup = AbilityUsageGroup.UTILITY;
    }

    @Override
    public Component getName(Power<?> context) {
        return getTargetedLifeformEffect(context)
                .map(GECreatedLifeformEffect::getSourceName)
                .filter(name -> !name.getString().isEmpty())
                .map(name -> abilityName(context, ".param", name))
                .orElseGet(() -> super.getName(context));
    }

    @Override
    public void renderAbilityIcon(Power<?> context, GuiGraphics guiGraphics, TextureAtlasSprite sprite, float x, float y, int color) {
        getTargetedLifeformEffect(context)
                .map(GECreatedLifeformEffect::getItemView)
                .filter(item -> !item.isEmpty())
                .ifPresentOrElse(
                        item -> guiGraphics.renderItem(item, (int) x, (int) y),
                        () -> super.renderAbilityIcon(context, guiGraphics, sprite, x, y, color));
    }

    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        if (!super.isAbilityAvailable(context)) {
            return false;
        }
        LivingEntity user = context.getUser();
        return user == null || !user.level().isClientSide()
                || context instanceof StandPower standPower && hasCreatedLifeformEffect(standPower, user);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        LivingEntity user = context.getUser();
        if (user != null && user.level().isClientSide()
                && (!(context instanceof StandPower standPower) || findCreatedLifeformEffect(standPower, user).isEmpty())) {
            return ConditionCheck.createNegative("ge_no_lifeform");
        }
        return super.checkSpecificConditions(context);
    }

    private static Optional<GECreatedLifeformEffect> getTargetedLifeformEffect(Power<?> context) {
        if (context instanceof StandPower standPower) {
            LivingEntity user = context.getUser();
            return findCreatedLifeformEffect(standPower, user);
        }
        return Optional.empty();
    }

    @Override
    public void writeExtraInput(FriendlyByteBuf serverboundBuf, LivingEntity user, boolean isClientPlayer) {
        StandPower standPower = StandPower.get(user);
        int effectId = findCreatedLifeformEffect(standPower, user)
                .map(GECreatedLifeformEffect::getId)
                .orElse(-1);
        serverboundBuf.writeVarInt(effectId);
    }

    @Override
    public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        if (level.isClientSide()) {
            return;
        }
        StandPower standPower = StandPower.get(user);
        if (standPower != null) {
            Optional<Integer> inputEffectId = readEffectId(extraClientInput, user);
            Optional<GECreatedLifeformEffect> effect = inputEffectId.isPresent()
                    ? createdLifeformEffectFromInput(standPower, user, inputEffectId.get())
                    : findCreatedLifeformEffect(standPower, user);
            effect.ifPresent(lifeform -> revertLifeform(standPower, lifeform));
        }
    }

    private static Optional<Integer> readEffectId(FriendlyByteBuf extraClientInput, LivingEntity user) {
        if (extraClientInput == null || extraClientInput.readableBytes() <= 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(extraClientInput.readVarInt());
        }
        catch (RuntimeException e) {
            JojoMod.getLogger().warn("Ignoring malformed Gold Experience revert lifeform input from {}.",
                    user.getName().getString(), e);
            return Optional.empty();
        }
    }

    private static void revertLifeform(StandPower standPower, GECreatedLifeformEffect effect) {
        Entity target = effect.getTarget();
        effect.remove();
        if (target instanceof GETransformationEntity transformation && transformation.actionCooldown > 0) {
            int createCooldown = standPower.getAbilityCooldown(GoldExperienceCreateLifeformAbility.CREATE_LIFEFORM_ABILITY_NAME);
            standPower.setAbilityCooldown(
                    GoldExperienceCreateLifeformAbility.CREATE_LIFEFORM_ABILITY_NAME,
                    createCooldown - transformation.actionCooldown);
        }
    }

    private static boolean hasCreatedLifeformEffect(StandPower standPower, LivingEntity user) {
        if (standPower == null || user == null) {
            return false;
        }
        double rangeSqr = MAX_LIFEFORM_DISTANCE * MAX_LIFEFORM_DISTANCE;
        return standPower.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get())
                .anyMatch(effect -> effect.getTarget() != null && effect.getTarget().distanceToSqr(user) < rangeSqr);
    }

    private static Optional<GECreatedLifeformEffect> findCreatedLifeformEffect(StandPower standPower, LivingEntity user) {
        if (standPower == null || user == null) {
            return Optional.empty();
        }
        return UserStandEffects.getEffectLookedAt(standPower, ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get(), MAX_LIFEFORM_DISTANCE, user);
    }

    private static Optional<GECreatedLifeformEffect> createdLifeformEffectFromInput(StandPower standPower, LivingEntity user, int effectId) {
        if (standPower == null || user == null || effectId <= 0) {
            return Optional.empty();
        }
        if (!(standPower.userStandEffects.getById(effectId) instanceof GECreatedLifeformEffect effect)
                || effect.effectType != ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get()
                || effect.getStandUser() != user) {
            return Optional.empty();
        }
        Entity target = effect.getTarget();
        double rangeSqr = MAX_LIFEFORM_DISTANCE * MAX_LIFEFORM_DISTANCE;
        return target != null && target.distanceToSqr(user) < rangeSqr ? Optional.of(effect) : Optional.empty();
    }
}
