package rotp.addons.stone_legacy_zombie_orders.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.impl.powers.vampirism.abilities.VampirismActionAbility;
import rotp.core.impl.powers.vampirism.entity.HungryZombieEntity;

import rotp.addons.stone_legacy_zombie_orders.ZombieOwnership;
import rotp.addons.stone_legacy_zombie_orders.ZombieOrdersAddon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public final class WaitOrderAbility
        extends VampirismActionAbility {
    public static final int COOLDOWN_TICKS = 30;

    public WaitOrderAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                Integer.MAX_VALUE,
                0.0F,
                WaitOrderInstance::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    @Override
    public String getResourceNamespace() {
        return ZombieOrdersAddon.RESOURCE_NAMESPACE;
    }

    @Override
    public void setCooldownOnUse(Power<?> context) {
        setVampirismCooldown(
                context, COOLDOWN_TICKS, COOLDOWN_TICKS);
    }

    public static final class WaitOrderInstance
            extends EntityActionInstance {
        public WaitOrderInstance(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel level)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }
            int range =
                    16 * level.getDifficulty().getId() - 8;
            if (range <= 0) {
                return;
            }
            ZombieOwnership.ownedInLevel(user).stream()
                    .filter(HungryZombieEntity.class::isInstance)
                    .map(HungryZombieEntity.class::cast)
                    .filter(zombie ->
                            zombie.distanceToSqr(user)
                                    <= (double) range * range)
                    .forEach(zombie ->
                            ZombieOrdersAddon.state(zombie)
                                    .setOrderedToWait(
                                            zombie, true));
        }
    }
}
