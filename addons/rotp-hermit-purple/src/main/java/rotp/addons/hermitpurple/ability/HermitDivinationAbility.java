package rotp.addons.hermitpurple.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.hermitpurple.HermitDivination;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class HermitDivinationAbility
        extends NoPoseStandEntityAbility {
    public HermitDivinationAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, DivinationAction::new);
        usageGroup = AbilityUsageGroup.UTILITY;
        setDefaultPhaseLength(
                ActionPhase.WINDUP,
                HermitPurpleSpec.DOXX_WINDUP_TICKS);
        setDefaultPhaseLength(
                ActionPhase.PERFORM,
                HermitPurpleSpec.DOXX_PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(HermitPurpleSpec.DOXX_COOLDOWN_TICKS);
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        if (abilities != null
                && context.getUser() != null
                && context.getUser()
                        .getOffhandItem()
                        .is(HermitPurpleAddon.CAMERA_ITEMS)) {
            Ability camera = abilities.getContextVariation(
                    HermitPurpleAddon.HP_CAMERA_NAME);
            if (camera != null) {
                return camera;
            }
        }
        return this;
    }


    /**
     * 1.16 named this after WHO or WHAT the divination is pointed at: {@code HPDoxx} overrode
     * {@code getTranslatedName} to return {@code .stand} with the Stand's name when the target is a Stand user,
     * or {@code .s} with the target's name otherwise, falling back to the plain name only in random mode. The
     * port dropped the override, so the ability reads the same whatever you selected and the only way to check
     * a selection is to run the command again.
     *
     * <p>The label comes from {@link HermitDivination#targetLabel}, which the divination map title already uses
     * - one resolver, two surfaces, as 1.16 had it. Random mode keeps the argument-free key, so no string with
     * an unfilled placeholder is ever rendered.
     */
    @Override
    public Component getName(Power<?> context) {
        HermitPurpleStandEntity stand =
                HermitPurpleAddon.controller(context.getUser());
        if (stand == null
                || stand.getTargetMode()
                        == HermitPurpleSpec.TARGET_MODE_RANDOM) {
            return super.getName(context);
        }
        String postfix = stand.getTargetMode()
                        == HermitPurpleSpec.TARGET_MODE_STAND
                ? ".stand"
                : ".s";
        return abilityName(
                context,
                postfix,
                HermitDivination.targetLabel(stand));
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        if (!(user instanceof ServerPlayer)
                && !user.level().isClientSide()) {
            return ConditionCheck.NEGATIVE;
        }
        if (!user.getMainHandItem().isEmpty()
                || !user.getOffhandItem().is(Items.MAP)) {
            return ConditionCheck.createNegative("hp_doxx_map");
        }
        return ConditionCheck.POSITIVE;
    }

    public static final class DivinationAction
            extends EntityActionInstance {
        public DivinationAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer user)
                    || !(performer
                            instanceof HermitPurpleStandEntity stand)) {
                return;
            }
            if (!user.getMainHandItem().isEmpty()
                    || !user.getOffhandItem().is(Items.MAP)) {
                payPerform(user);
                startRecovery();
                return;
            }
            BlockPos target =
                    HermitDivination.resolvePosition(user, stand);
            if (target == null) {
                user.displayClientMessage(
                        Component.translatable(
                                "jojo.message.action_condition."
                                        + "hp_doxx_target"),
                        true);
                payPerform(user);
                startRecovery();
                return;
            }
            // 1.16 hp_doxx .shout(USER_HP)
            sayShoutOf(ability, user, HermitPurpleAddon.JOSEPH_HP);

            if (!user.getAbilities().instabuild) {
                user.getOffhandItem().shrink(1);
            }
            byte scale = (byte) (
                    user.isShiftKeyDown() ? 0 : 2);
            ItemStack map = MapItem.create(
                    user.serverLevel(),
                    target.getX(),
                    target.getZ(),
                    scale,
                    true,
                    true);
            MapItem.renderBiomePreviewMap(
                    user.serverLevel(), map);
            MapItemSavedData.addTargetDecoration(
                    map,
                    target,
                    "+",
                    MapDecorationTypes.RED_X);
            map.set(
                    DataComponents.ITEM_NAME,
                    Component.translatable(
                                    "filled_map.divination")
                            .append(": ")
                            .append(HermitDivination
                                    .targetLabel(stand)));
            map.set(
                    DataComponents.MAP_COLOR,
                    new MapItemColor(stand.getHermitColor()));
            user.setItemInHand(InteractionHand.MAIN_HAND, map);
            StandUtil.playStandEntitySound(
                    stand,
                    HermitPurpleAddon.HP_SPARK.get(),
                    1.0F,
                    1.0F);
            payPerform(user);
        }

        // 1.16 StandEntityTask paid the cost after every perform, whether or
        // not HPDoxx found a target, and ignored the result: a short bar only
        // drops to 0.
        private void payPerform(ServerPlayer user) {
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    HermitPurpleSpec.DOXX_STAMINA,
                    false);
        }
    }
}
