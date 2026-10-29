package rotp.addons.hermitpurple.ability;

import rotp.core.network.s2c.PhotoForOtherPlayerPacket;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class HermitSpiritCameraAbility
        extends NoPoseStandEntityAbility {
    public HermitSpiritCameraAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, SpiritCameraAction::new);
        usageGroup = AbilityUsageGroup.UTILITY;
        setDefaultPhaseLength(
                ActionPhase.WINDUP,
                HermitPurpleSpec.CAMERA_WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 10);
        setDefaultPhaseLength(
                ActionPhase.RECOVERY,
                HermitPurpleSpec.CAMERA_RECOVERY_TICKS);
        cooldown(HermitPurpleSpec.CAMERA_COOLDOWN_TICKS);
    }


    /**
     * 1.16 named this after WHO or WHAT the divination is pointed at: {@code HPCamera} overrode
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
        if (!(user instanceof Player player)
                || !player.getOffhandItem()
                        .is(HermitPurpleAddon.CAMERA_ITEMS)) {
            return ConditionCheck.createNegative(
                    "hp_camera_polaroid");
        }
        HermitPurpleStandEntity stand =
                HermitPurpleAddon.controller(user);
        if (stand == null
                || stand.getTargetMode()
                        != HermitPurpleSpec
                                .TARGET_MODE_PLAYER
                        && stand.getTargetMode()
                                != HermitPurpleSpec
                                        .TARGET_MODE_STAND) {
            return ConditionCheck.createNegative(
                    "no_player_target");
        }
        if (!hasPaper(player)) {
            return ConditionCheck.createNegative(
                    Component.translatable(
                            "jojo.polaroid.paper"));
        }
        return ConditionCheck.POSITIVE;
    }

    private static boolean hasPaper(Player player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        return !findPaper(player).isEmpty();
    }

    private static ItemStack findPaper(Player player) {
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            ItemStack stack =
                    player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(Items.PAPER)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static final class SpiritCameraAction
            extends EntityActionInstance {
        public SpiritCameraAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {}

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer cameraUser)
                    || !(performer
                            instanceof HermitPurpleStandEntity stand)) {
                return;
            }
            if (!cameraUser.getOffhandItem()
                    .is(HermitPurpleAddon.CAMERA_ITEMS)) {
                fail(cameraUser, "hp_camera_polaroid");
                startRecovery();
                return;
            }
            ItemStack paper = findPaper(cameraUser);
            if (!cameraUser.getAbilities().instabuild
                    && paper.isEmpty()) {
                fail(cameraUser, "jojo.polaroid.paper");
                startRecovery();
                return;
            }

            ServerPlayer photoPlayer =
                    HermitDivination.resolvePhotoTarget(
                            cameraUser, stand);
            if (photoPlayer == null
                    || photoPlayer == cameraUser
                    || !photoPlayer.isAlive()) {
                fail(cameraUser, "hp_camera_player");
                startRecovery();
                return;
            }

            StandPower power = StandPower.get(cameraUser);
            // 1.16 StandEntityTask paid the cost with the perform and ignored
            // the result: a short bar only drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    power,
                    HermitPurpleSpec.CAMERA_STAMINA,
                    false);
            // 1.16 hp_camera .shout(USER_HP)
            sayShoutOf(ability, cameraUser, HermitPurpleAddon.JOSEPH_HP);
            if (!cameraUser.getAbilities().instabuild) {
                paper.shrink(1);
            }
            if (!PhotoForOtherPlayerPacket.sendToCapturingPlayer(
                    photoPlayer, cameraUser)) {
                fail(cameraUser, "hp_camera_player");
                startRecovery();
                return;
            }
            if (cameraUser.getRandom().nextFloat() * 3.5F
                    > power.getResolveLevel()) {
                cameraUser.getOffhandItem().shrink(1);
                // 1.16 HPCamera then called broadcastBreakEvent(OFFHAND):
                // the break sound and particles, no broken-item stat.
                cameraUser.level().broadcastEntityEvent(
                        cameraUser, EntityEvent.OFFHAND_BREAK);
            }
            StandUtil.playStandEntitySound(
                    stand,
                    HermitPurpleAddon.HP_SPARK.get(),
                    1.0F,
                    1.0F);
        }

        private void fail(
                ServerPlayer user, String messageKey) {
            user.displayClientMessage(
                    Component.translatable(
                            messageKey.startsWith("jojo.")
                                    ? messageKey
                                    : "jojo.message."
                                            + "action_condition."
                                            + messageKey),
                    true);
        }

        // No release handler: 1.16 hp_camera had no holdType or holdToFire,
        // so letting go of the key never cancelled the shot.

        // 1.16 hp_camera was a non-hold StandEntityAction nothing cancelled
        // (canBeCanceled :507-512): another press waits in the input buffer,
        // so the shot is not lost; its own press is refused below. A client's
        // copy holds the base-moveset ability, so identity would not match it.
        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return isSameAbility(cancellingAbility, ability);
        }

        // 1.16 StandEntityAction.canClickDuringTask :495-497: not chainable
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }
    }
}
