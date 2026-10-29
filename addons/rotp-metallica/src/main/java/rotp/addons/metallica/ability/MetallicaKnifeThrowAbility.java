package rotp.addons.metallica.ability;

import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.metallica.MetallicaAddon;
import rotp.addons.metallica.MetallicaPhase1Spec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;

public class MetallicaKnifeThrowAbility
        extends StandEntityAbility {
    public static final int WINDUP_TICKS = 5;
    public static final float PROJECTILE_SPEED = 3.0F;
    public static final float BASE_INACCURACY = 1.0F;
    public static final int TIME_STOP_FLIGHT_TICKS = 5;

    private final int projectileCount;
    private final float staminaCost;

    public MetallicaKnifeThrowAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        this(
                abilityType,
                abilityId,
                1,
                MetallicaPhase1Spec.SINGLE_SCALPEL_STAMINA,
                MetallicaPhase1Spec
                        .SINGLE_SCALPEL_COOLDOWN_TICKS);
    }

    protected MetallicaKnifeThrowAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            int projectileCount,
            float staminaCost,
            int cooldownTicks) {
        super(abilityType, abilityId, ThrowAction::new);
        this.projectileCount = projectileCount;
        this.staminaCost = staminaCost;
        setDefaultPhaseLength(
                ActionPhase.WINDUP, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(cooldownTicks);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    private static final class ThrowAction
            extends EntityActionInstance {
        private ThrowAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel level)
                    || !(ability
                            instanceof MetallicaKnifeThrowAbility
                            throwAbility)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null || power == null) {
                forceStop();
                return;
            }

            for (int index = 0;
                    index < throwAbility.projectileCount;
                    index++) {
                KnifeEntity scalpel = new KnifeEntity(
                        level,
                        user,
                        new ItemStack(
                                MetallicaAddon.SCALPEL.get()));
                scalpel.pickup =
                        AbstractArrow.Pickup.DISALLOWED;
                scalpel.setKnifeType(
                        KnifeEntity.TexVariant.SCALPEL);
                scalpel.setTimeStopFlightTicks(
                        TIME_STOP_FLIGHT_TICKS);
                scalpel.shootFromRotation(
                        user,
                        user.getXRot(),
                        user.getYRot(),
                        0.0F,
                        PROJECTILE_SPEED,
                        BASE_INACCURACY + index % 3);
                level.addFreshEntity(scalpel);
            }

            // 1.16 spawned the knives before draining stamina, even when short.
            StandAbilityStamina.consume(
                    (Ability) throwAbility,
                    power,
                    throwAbility.staminaCost,
                    false);

            // 1.16 standSound(KNIFE_THROW / KNIVES_THROW): a stand sound the
            // user hears too, which Player.playSound on the server leaves out.
            if (throwAbility.projectileCount == 1) {
                playStandSound(ModSoundEvents.KNIFE_THROW.get());
                JojoModUtil.sayVoiceLine(
                        user,
                        MetallicaAddon.USER_KNIFE_THROW);
            } else {
                playStandSound(ModSoundEvents.KNIVES_THROW.get());
                JojoModUtil.sayVoiceLine(
                        user,
                        MetallicaAddon.USER_KNIVES_THROW);
            }
            throwAbility.setCooldownOnUse(power);
        }

        private void playStandSound(SoundEvent sound) {
            if (performer instanceof StandEntity stand) {
                StandUtil.playStandEntitySound(stand, sound, 1.0F, 1.0F);
            }
        }
    }
}
