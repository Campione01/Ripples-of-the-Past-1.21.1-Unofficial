package rotp.addons.tusk.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.impl.stands.starplatinum.HeavyPunchUppercutAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import rotp.addons.tusk.TuskAddon;

// 1.16 TuskUppercut: knockback 0.5 + atk/16 * finisher,
// knockbackXRot(-60) and disableBlocking(1.0), as the core uppercut
public final class TuskUppercutAbility
        extends HeavyPunchUppercutAbility {
    public TuskUppercutAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        // 1.16 SHIFT variation: .shout(JOHNNY_HEAVY_ATTACK) even on sneak
        setPlaysVoiceLineOnSneak();
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity standUser,
            LivingEntity standEntity) {
        super.initActionFromConfig(
                action, level, standUser, standEntity);
        if (!level.isClientSide() && standUser != null) {
            sayShout(standUser, TuskAddon.JOHNNY_HEAVY_ATTACK);
        }
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        Ability inherited =
                super.replaceWithSubAbility(context, abilities);
        if (inherited != this
                || context == null
                || !TuskAddon.hasInfiniteRotationCharge(
                        context.getUser())) {
            return inherited;
        }
        Ability variation = abilities.getContextVariation(
                TuskAddon.INFINITE_ROTATION_NAME);
        return variation != null ? variation : this;
    }
}
