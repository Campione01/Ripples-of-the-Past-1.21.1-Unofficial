package rotp.addons.stone_legacy_zombie_orders.ability;

import java.util.List;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.impl.powers.vampirism.abilities.VampirismActionAbility;
import rotp.addons.stone_legacy_zombie_orders.ZombieOwnership;
import rotp.addons.stone_legacy_zombie_orders.ZombieOrdersAddon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class RecallZombiesAbility
        extends VampirismActionAbility {
    public static final int COOLDOWN_TICKS = 60;

    public RecallZombiesAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                Integer.MAX_VALUE,
                0.0F,
                RecallInstance::new);
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

    public static final class RecallInstance
            extends EntityActionInstance {
        public RecallInstance(EntityActionType ability) {
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
            List<LivingEntity> owned =
                    ZombieOwnership.ownedInLevel(user);
            int count = owned.size();
            if (count == 0) {
                return;
            }
            double radius =
                    Math.sqrt(4.0D * count / (Math.PI * 2.0D));
            for (int i = 0; i < count; i++) {
                double angle =
                        Math.PI * 2.0D / count * i;
                Vec3 position = user.position().add(
                        radius * Math.cos(angle),
                        0.0D,
                        radius * Math.sin(angle));
                owned.get(i).teleportTo(
                        position.x,
                        position.y,
                        position.z);
            }
        }
    }
}
