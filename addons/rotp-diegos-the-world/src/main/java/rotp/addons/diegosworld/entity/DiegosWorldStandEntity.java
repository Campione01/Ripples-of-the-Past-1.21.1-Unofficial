package rotp.addons.diegosworld.entity;

import rotp.core.JojoModLivingVariables;
import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.diegosworld.DiegosTheWorldAddon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class DiegosWorldStandEntity extends StandEntity {
    public DiegosWorldStandEntity(
            EntityType<? extends DiegosWorldStandEntity> type,
            Level level) {
        super(type, level);
    }

    @Override
    public void onKnivesThrow(
            Level level,
            Player player,
            ItemStack knives,
            int knivesThrown) {
        if (level.isClientSide()
                || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        StandPower power = getUserPower();
        if (power == null
                || power.getPowerType()
                        != DiegosTheWorldAddon.THE_WORLD.get()) {
            return;
        }

        JojoModLivingVariables vars =
                JojoModLivingVariables.get(player);
        if (knivesThrown == 1 && vars.knivesThrewTicks > 0) {
            JojoModUtil.sayVoiceLine(
                    player, DiegosTheWorldAddon.DIEGO_ONE_MORE);
            vars.knivesThrewTicks = 0;
        }

        TimeStopState timeStop = serverLevel.getData(
                ModDataAttachmentTypes.TIME_STOP.get());
        if (knivesThrown > 1 && timeStop.isTimeStopped(player)) {
            vars.knivesThrewTicks = 80;
        }
        if (player.isShiftKeyDown() || knives.isEmpty()) {
            return;
        }

        int standKnives = Math.min(knives.getCount(), 8);
        for (int i = 0; i < standKnives; i++) {
            ItemStack projectileStack = knives.copy();
            projectileStack.setCount(1);
            KnifeEntity knife =
                    new KnifeEntity(
                            level, player, projectileStack);
            knife.setPos(getX(), getEyeY() - 0.1D, getZ());
            knife.setTimeStopFlightTicks(5);
            knife.shootFromRotation(
                    player,
                    player.getXRot(),
                    player.getYRot(),
                    0.0F,
                    1.5F,
                    i == 0 ? 1.0F : 16.0F);
            level.addFreshEntity(knife);
        }
        level.playSound(
                null,
                getX(),
                getY(),
                getZ(),
                standKnives == 1
                        ? ModSoundEvents.KNIFE_THROW.get()
                        : ModSoundEvents.KNIVES_THROW.get(),
                SoundSource.PLAYERS,
                0.5F,
                0.4F / (getRandom().nextFloat() * 0.4F + 0.8F));
        if (!player.getAbilities().instabuild) {
            knives.shrink(standKnives);
        }

        timeStop.getInstance(player.getId())
                .filter(instance -> instance.totalTicks() == 100)
                .filter(instance ->
                        instance.ticksLeft() > 50
                                && instance.ticksLeft() <= 80)
                .ifPresent(instance -> JojoModUtil.sayVoiceLine(
                        player,
                        DiegosTheWorldAddon.DIEGO_5_SECONDS));
    }
}
