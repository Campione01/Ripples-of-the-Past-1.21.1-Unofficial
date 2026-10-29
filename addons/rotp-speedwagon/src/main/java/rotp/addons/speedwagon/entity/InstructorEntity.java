package rotp.addons.speedwagon.entity;

import javax.annotation.Nullable;

import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

import rotp.addons.speedwagon.SpeedwagonAddon;
import rotp.addons.speedwagon.power.SpeedwagonTeaching;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.common.NeoForgeMod;

public final class InstructorEntity extends Mob implements Npc {
    public InstructorEntity(
            EntityType<? extends InstructorEntity> type,
            Level level) {
        super(type, level);
        PowerClass.PLAYER_POWER.attachGet(this);
    }

    @Override
    public boolean removeWhenFarAway(
            double distanceFromClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()
                && tickCount % 20 == 0) {
            ensureSpeedwagonPower();
        }
    }

    @Override
    protected InteractionResult mobInteract(
            Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return super.mobInteract(player, hand);
        }
        if (!level().isClientSide()
                && player
                        instanceof net.minecraft.server.level.ServerPlayer
                                serverPlayer) {
            SpeedwagonTeaching.grantFromInstructor(
                    serverPlayer);
        }
        return InteractionResult.sidedSuccess(
                level().isClientSide());
    }

    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
            @Nullable SpawnGroupData spawnGroupData) {
        ensureSpeedwagonPower();
        setPersistenceRequired();
        return super.finalizeSpawn(
                level, difficulty, spawnType, spawnGroupData);
    }

    public void ensureSpeedwagonPower() {
        PlayerPower power =
                PowerClass.PLAYER_POWER.attachGet(this);
        if (power.getPowerType()
                != SpeedwagonAddon.SPEEDWAGON.get()) {
            power.setPowerType(
                    SpeedwagonAddon.SPEEDWAGON.get());
        }
    }

    @Override
    protected void registerGoals() {}

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.ATTACK_SPEED, 0.3D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(NeoForgeMod.SWIM_SPEED, 2.0D);
    }
}
