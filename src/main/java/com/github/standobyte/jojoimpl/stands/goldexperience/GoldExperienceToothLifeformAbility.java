package com.github.standobyte.jojoimpl.stands.goldexperience;

import java.util.Optional;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.customobjects.ObjectEntity;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.power.ModStandAbilities;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.AbilityUsageGroup;
import com.github.standobyte.jojo.powersystem.ability.condition.AvailableAbilities;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.util.mc.entitysubtype.EntitySubtype;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceHeavyPunchAbility.ToothKnockingHeavyPunch;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class GoldExperienceToothLifeformAbility extends GoldExperienceUtilityAbility {
    private static final int MAX_LIFEFORM_ID_LENGTH = 256;
    public static final String CREATE_LIFEFORM_ABILITY = GoldExperienceCreateLifeformAbility.CREATE_LIFEFORM_ABILITY_NAME;
    public static final String TOOTH_LIFEFORM_ABILITY = "tooth_lifeform";

    public GoldExperienceToothLifeformAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        usageGroup = AbilityUsageGroup.UTILITY;
        isSubAbility = true;
    }

    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        StandPower standPower = PowerClass.STAND.cast(context);
        return standPower != null && standPower.isAbilityUnlocked(CREATE_LIFEFORM_ABILITY);
    }

    @Override
    public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
        StandPower standPower = PowerClass.STAND.cast(context);
        if (abilities != null && hasCompatibleToothPunch(standPower)) {
            abilities.replaceOtherAbilityWith(standPower, "heavy_punch", this);
        }
        return null;
    }

    @Override
    public Component getName(Power<?> context) {
        LivingEntity user = context != null ? context.getUser() : null;
        if (user != null && hasCompatibleToothPunch(PowerClass.STAND.cast(context))) {
            Optional<EntitySubtype<?>> selectedLifeform = GoldExperienceCreateLifeformAbility.selectedLifeformSubtype(
                    user.level(), user, selectedOrFirstLifeformId(user));
            if (selectedLifeform.isPresent()) {
                return abilityName(context, ".param", selectedLifeform.get().getDescription());
            }
        }
        return super.getName(context);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        StandPower standPower = PowerClass.STAND.cast(context);
        LivingEntity user = standPower != null ? standPower.getUser() : null;
        if (!hasCompatibleToothPunch(standPower)) {
            return ConditionCheck.NEGATIVE;
        }
        ConditionCheck check = toothLifeformCondition(standPower, user, selectedOrFirstLifeformId(user));
        return check.isPositive() ? super.checkSpecificConditions(context) : check;
    }

    @Override
    public void writeExtraInput(FriendlyByteBuf serverboundBuf, LivingEntity user, boolean isClientPlayer) {
        String selectedLifeformId = selectedOrFirstLifeformId(user);
        serverboundBuf.writeBoolean(selectedLifeformId != null);
        if (selectedLifeformId != null) {
            serverboundBuf.writeUtf(selectedLifeformId, MAX_LIFEFORM_ID_LENGTH);
        }
    }

    @Override
    public void onClick(net.minecraft.world.level.Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        if (level.isClientSide()) {
            return;
        }
        StandPower standPower = StandPower.get(user);
        String selectedLifeformId = readSelectedLifeformInput(extraClientInput, user);
        if (selectedLifeformId == null) {
            selectedLifeformId = selectedOrFirstLifeformId(user);
        }
        ConditionCheck check = toothLifeformCondition(standPower, user, selectedLifeformId);
        if (!check.isPositive()) {
            ConditionCheck.sendActionFailedMessage(this, check, user);
            return;
        }
        ToothKnockingHeavyPunch punch = getCurrentToothPunch(standPower);
        if (punch != null) {
            punch.queueToothLifeform(selectedLifeformId);
        }
    }

    public static boolean canUseToothLifeform(StandPower standPower, LivingEntity user) {
        return toothLifeformCondition(standPower, user, selectedOrFirstLifeformId(user)).isPositive();
    }

    private static ConditionCheck toothLifeformCondition(@Nullable StandPower standPower, @Nullable LivingEntity user,
            @Nullable String selectedLifeformId) {
        if (standPower == null || user == null) {
            return ConditionCheck.NEGATIVE;
        }
        if (!standPower.isAbilityUnlocked(CREATE_LIFEFORM_ABILITY)) {
            return ConditionCheck.createNegative("not_unlocked");
        }
        if (!GoldExperienceCreateLifeformAbility.canCreateMoreLifeforms(standPower)) {
            return ConditionCheck.createNegative("ge_too_many_mobs");
        }
        if (GoldExperienceCreateLifeformAbility.selectedLifeformSubtype(user.level(), user, selectedLifeformId).isEmpty()) {
            return ConditionCheck.createNegative("choose_lifeform");
        }
        return ConditionCheck.POSITIVE;
    }

    @Nullable
    private static String selectedOrFirstLifeformId(@Nullable LivingEntity user) {
        return user != null ? GoldExperienceLifeformState.get(user).selectedOrFirstMetId(user.level()) : null;
    }

    @Nullable
    private static String readSelectedLifeformInput(@Nullable FriendlyByteBuf extraClientInput, LivingEntity user) {
        if (extraClientInput == null || extraClientInput.readableBytes() <= 0) {
            return selectedOrFirstLifeformId(user);
        }
        try {
            return extraClientInput.readBoolean()
                    ? extraClientInput.readUtf(MAX_LIFEFORM_ID_LENGTH)
                    : null;
        }
        catch (RuntimeException e) {
            JojoMod.getLogger().warn("Ignoring malformed Gold Experience tooth lifeform input from {}.",
                    user.getName().getString(), e);
            return selectedOrFirstLifeformId(user);
        }
    }

    private static boolean hasCompatibleToothPunch(@Nullable StandPower standPower) {
        ToothKnockingHeavyPunch punch = getCurrentToothPunch(standPower);
        return punch != null && punch.canAcceptToothLifeformFollowup();
    }

    @Nullable
    private static ToothKnockingHeavyPunch getCurrentToothPunch(@Nullable StandPower standPower) {
        StandEntity stand = standPower != null ? standPower.getSummonedStandEntity() : null;
        EntityActionInstance curAction = stand != null ? stand.getCurStandAction() : null;
        return curAction instanceof ToothKnockingHeavyPunch punch ? punch : null;
    }

    public static boolean isToothSourceTarget(LivingEntity target) {
        return target instanceof Player
                || target instanceof AbstractSkeleton
                || target instanceof Zombie
                || target instanceof AbstractVillager
                || target instanceof AbstractPiglin
                || target instanceof PatrollingMonster
                || target instanceof Bat
                || target instanceof AbstractHorse
                || target instanceof Cow
                || target instanceof Fox
                || target instanceof Hoglin
                || target instanceof Ocelot
                || target instanceof Panda
                || target instanceof Pig
                || target instanceof PolarBear
                || target instanceof Rabbit
                || target instanceof Sheep
                || target instanceof Cat
                || target instanceof Wolf
                || target instanceof WaterAnimal;
    }

    public static boolean tryTransformToothObject(ServerLevel level, StandPower standPower, LivingEntity user,
            LivingEntity hitTarget, ObjectEntity toothObject, @Nullable String selectedLifeformId) {
        if (!toothObject.isAlive()
                || toothObject.getObjectType() != ObjectEntity.Type.TOOTH
                || !toothLifeformCondition(standPower, user, selectedLifeformId).isPositive()) {
            return false;
        }

        Entity createdEntity = GoldExperienceCreateLifeformAbility.createLifeformEntity(level, user, selectedLifeformId);
        GETransformationEntity transformation = ModEntityTypes.GE_LIFEFORM_TRANSFORMATION.get().create(level);
        if (createdEntity == null || transformation == null) {
            return false;
        }

        CompoundTag sourceEntityNbt = GoldExperienceCreateLifeformAbility.saveSourceEntity(toothObject);
        if (sourceEntityNbt.isEmpty()) {
            return false;
        }

        int ticks = GoldExperienceCreateLifeformAbility.getTicksToCreate(user, standPower, createdEntity);
        transformation.moveTo(toothObject.getX(), toothObject.getY(), toothObject.getZ(), toothObject.getYRot(), toothObject.getXRot());
        createdEntity.moveTo(toothObject.getX(), toothObject.getY(), toothObject.getZ(), toothObject.getYRot(), toothObject.getXRot());
        if (createdEntity instanceof LivingEntity livingCreated) {
            livingCreated.setYHeadRot(livingCreated.getYRot());
        }
        transformation
                .withSourceEntity(sourceEntityNbt, ItemStack.EMPTY)
                .withTransformationTarget(createdEntity)
                .withDuration(ticks)
                .withFollowTarget(toothObject.getOwner() != null ? toothObject.getOwner() : hitTarget.getUUID(),
                        GETransformationEntity.FollowTargetMode.AGGRO_TRACK, user);

        toothObject.discard();
        if (!standPower.isUserCreative()) {
            int cooldown = Math.max(ticks / 2, 1);
            transformation.actionCooldown = cooldown;
            standPower.setAbilityCooldown(CREATE_LIFEFORM_ABILITY, cooldown, cooldown);
        }
        level.addFreshEntity(transformation);

        GECreatedLifeformEffect createdLifeform = ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get().create(level);
        createdLifeform.setSourceEntity(sourceEntityNbt, ItemStack.EMPTY);
        createdLifeform.setFollowTarget(transformation.getFollowTarget(), transformation.getFollowTargetMode());
        createdLifeform.withTarget(transformation);
        standPower.userStandEffects.addEffect(createdLifeform);
        return true;
    }
}
