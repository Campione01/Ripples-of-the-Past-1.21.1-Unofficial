package rotp.addons.shadowtheworld.entity;

import rotp.core.JojoModLivingVariables;
import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.item.KnifeItem;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.shadowtheworld.ShadowTheWorldAddon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 1.16 SDworldEntity: the Stand throws its own knife volley.
public final class ShadowStandEntity extends StandEntity {

    // 1.16 SDworldEntity.getDefaultOffsetFromUser: idle 0.45 to the right
    // of and 0.75 behind the user, feet 0.2 up, turned with the view yaw;
    // arms-only and grab stay default.
    public ShadowStandEntity(
            EntityType<? extends ShadowStandEntity> type,
            Level level) {
        super(type, level);
        offsetFromUser = new StandOffsetFromUser(
                this,
                new Vec3(-0.45D, Y_OFFSET, -0.75D),
                StandOffsetFromUser.Rotations.HEAD)
                .armsOnlyIdleOffset(
                        new Vec3(0.0D, 0.0D, 0.15D),
                        StandOffsetFromUser.Rotations.HEAD)
                .grabOffset(new Vec3(-1.0D, Y_OFFSET, 1.5D));
    }

    // 1.16 SDworldEntity.onKnivesThrow; no 5-seconds line there.
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

        JojoModLivingVariables vars =
                JojoModLivingVariables.get(player);
        if (knivesThrown == 1 && vars.knivesThrewTicks > 0) {
            JojoModUtil.sayVoiceLine(
                    player, ShadowTheWorldAddon.SDDIO_ONE_MORE);
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

        int standKnives = Math.min(
                knives.getCount(), KnifeItem.MAX_KNIVES_THROW);
        for (int i = 0; i < standKnives; i++) {
            ItemStack projectileStack = knives.copy();
            projectileStack.setCount(1);
            KnifeEntity knife =
                    new KnifeEntity(level, player, projectileStack);
            // from the Stand's eye, not the user's
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
    }
}
