package rotp.addons.planetwaves.ability;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.planetwaves.PlanetWavesAddon;
import rotp.addons.planetwaves.entity.MeteoriteEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class MeteorAttractionAbility extends StandEntityAbility {
    public static final int WINDUP_TICKS = 15;
    public static final int SINGLE_PERFORM_TICKS = 1;
    public static final int SHOWER_PERFORM_TICKS = 11;
    public static final int SINGLE_COOLDOWN_TICKS = 120;
    public static final int SHOWER_COOLDOWN_TICKS = 200;
    public static final float SINGLE_STAMINA_COST = 100.0F;
    public static final float SHOWER_STAMINA_COST = 175.0F;

    // 1.16 PlanetWaves*MeteoriteShower.standPerform scheduled all three
    // meteors at the shower's first perform tick, off the Stand's task
    // (0, 240 and 480 ms), so a press that cut the perform short lost none.
    // The later two fall due here at the server ticks of the perform's
    // ticks 5 and 10; the running action spawns them itself on those
    // ticks, and whichever comes first spawns each one once.
    private static final List<DueShowerMeteor> DUE_SHOWER_METEORS =
            new ArrayList<>();

    private record DueShowerMeteor(
            MeteorAction action,
            ServerLevel level,
            StandEntity stand,
            LivingEntity user,
            int meteor,
            long gameTime) {}

    private final Trajectory trajectory;
    private final boolean shower;
    private final ActionAnimIdentifier animation;

    public MeteorAttractionAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Trajectory trajectory,
            boolean shower) {
        super(
                abilityType,
                abilityId,
                type -> new MeteorAction(type, trajectory, shower));
        this.trajectory = trajectory;
        this.shower = shower;
        this.animation = ActionAnimIdentifier.getOrCreate(
                trajectory.animationName(shower), false);
        // 1.16 holdToFire(15, true): the 15 ticks are a charge that fires
        // when the key is let go.
        setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, WINDUP_TICKS);
        setDefaultPhaseLength(
                ActionPhase.PERFORM,
                shower
                        ? SHOWER_PERFORM_TICKS
                        : SINGLE_PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.ARMS);
        cooldown(
                0,
                shower
                        ? SHOWER_COOLDOWN_TICKS
                        : SINGLE_COOLDOWN_TICKS);
        resolveLevelToUnlock(shower ? 3 : trajectory.resolveLevel);
        standAutoSummonMode(AutoSummonMode.FULL);
    }

    // 1.16 set the cooldown when the hold fired, so a cancelled hold
    // leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    public Trajectory trajectory() {
        return trajectory;
    }

    public static void tickDueShowerMeteors(LevelTickEvent.Post event) {
        if (DUE_SHOWER_METEORS.isEmpty()
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<DueShowerMeteor> due = new ArrayList<>();
        Iterator<DueShowerMeteor> pending = DUE_SHOWER_METEORS.iterator();
        while (pending.hasNext()) {
            DueShowerMeteor meteor = pending.next();
            if (meteor.level() == level
                    && meteor.gameTime() <= level.getGameTime()) {
                pending.remove();
                due.add(meteor);
            }
        }
        for (DueShowerMeteor meteor : due) {
            MeteorAction action = meteor.action();
            // A meteor whose Stand or target is gone would only hang as an
            // orphan until MeteoriteEntity cleans it up.
            if (action.spawned < meteor.meteor()
                    && !meteor.stand().isRemoved()
                    && meteor.user().isAlive()) {
                action.spawnMeteor(meteor.stand(), meteor.user());
                action.spawned = meteor.meteor();
            }
        }
    }

    public static void clearDueShowerMeteors(ServerStoppedEvent event) {
        DUE_SHOWER_METEORS.clear();
    }

    public boolean shower() {
        return shower;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return animation;
    }

    public enum Trajectory {
        VERTICAL(1, false),
        HORIZONTAL(2, true),
        DIAGONAL(3, false);

        private final int resolveLevel;
        private final boolean verticalAttraction;

        Trajectory(
                int resolveLevel,
                boolean verticalAttraction) {
            this.resolveLevel = resolveLevel;
            this.verticalAttraction = verticalAttraction;
        }

        private String animationName(boolean shower) {
            return name().toLowerCase(Locale.ROOT)
                    + (shower ? "_shower" : "");
        }
    }

    public static final class MeteorAction
            extends EntityActionInstance {
        private final Trajectory trajectory;
        private final boolean shower;
        private int spawned;

        public MeteorAction(
                EntityActionType ability,
                Trajectory trajectory,
                boolean shower) {
            super(ability);
            this.trajectory = trajectory;
            this.shower = shower;
        }

        @Override
        public void onActionSet(
                @Nullable EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            // 1.16 PlanetWaves*Meteorite* gave no offset: the Stand keeps
            // its idle place, and a vertical meteor falls from above it
            if (getPerformer() instanceof StandEntity stand) {
                stand.offsetFromUser.resetToIdle();
            }
        }

        // 1.16 holdToFire(15, true): a full charge waits for the key to
        // be let go and fires then; letting go earlier cancels it.
        @Override
        protected boolean shouldHoldPhaseAtEnd() {
            return getPhase() == ActionPhase.BUTTON_CHARGE;
        }

        // 1.16 stopHeldAction(true) checked the requirements once more
        // before the released charge fired.
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                if (getPhaseTick() >= getCurPhaseLength()
                        && ability instanceof MeteorAttractionAbility held
                        && held.canFireReleasedHold(this)) {
                    setPhaseStart(ActionPhase.WINDUP);
                }
                else {
                    forceStop();
                }
                syncPhaseChanges();
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel)
                    || !(getPerformer()
                            instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower standPower =
                    user != null ? StandPower.get(user) : null;
            float cost = shower
                    ? SHOWER_STAMINA_COST
                    : SINGLE_STAMINA_COST;
            if (user == null || standPower == null) {
                startRecovery();
                return;
            }
            if (ability instanceof MeteorAttractionAbility meteor) {
                meteor.setCooldownOnUse(standPower);
            }
            spawnMeteor(stand, user);
            spawned = 1;
            if (shower) {
                ServerLevel serverLevel = (ServerLevel) level();
                long now = serverLevel.getGameTime();
                DUE_SHOWER_METEORS.add(new DueShowerMeteor(
                        this, serverLevel, stand, user, 2, now + 5));
                DUE_SHOWER_METEORS.add(new DueShowerMeteor(
                        this, serverLevel, stand, user, 3, now + 10));
            }
            StandUtil.playStandEntitySound(
                    stand,
                    PlanetWavesAddon.METEORITE_SUMMON,
                    1.0F,
                    1.0F);
            // 1.16 StandEntityTask paid staminaCost after standPerform and
            // ignored the result; a short bar just drops to 0
            StandAbilityStamina.consume(ability, standPower, cost, false);
        }

        @Override
        public void actionTick() {
            if (!shower
                    || getPhase() != ActionPhase.PERFORM
                    || !(level() instanceof ServerLevel)
                    || !(getPerformer()
                            instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }
            int phaseTick = (int) getPhaseTick();
            if (spawned == 1 && phaseTick >= 5) {
                spawnMeteor(stand, user);
                spawned = 2;
            }
            if (spawned == 2 && phaseTick >= 10) {
                spawnMeteor(stand, user);
                spawned = 3;
            }
        }

        private void spawnMeteor(
                StandEntity stand, LivingEntity user) {
            if (!(level() instanceof ServerLevel serverLevel)) {
                return;
            }
            Vec3 origin = spawnOrigin(stand, user);
            if (shower) {
                origin = origin.add(
                        stand.getRandom().nextDouble() * 5.0D - 2.5D,
                        0.0D,
                        stand.getRandom().nextDouble() * 5.0D - 2.5D);
            }
            MeteoriteEntity meteor =
                    new MeteoriteEntity(
                            PlanetWavesAddon.METEORITE.get(),
                            serverLevel);
            meteor.moveTo(
                    origin.x,
                    origin.y,
                    origin.z,
                    user.getYRot(),
                    90.0F);
            meteor.initialize(
                    stand,
                    user,
                    trajectory.verticalAttraction,
                    MeteoriteEntity.LEGACY_SIZE);
            serverLevel.addFreshEntity(meteor);
        }

        private Vec3 spawnOrigin(
                StandEntity stand, LivingEntity user) {
            if (trajectory == Trajectory.VERTICAL) {
                return new Vec3(
                        stand.getX(),
                        stand.getY() + 60.0D,
                        stand.getZ());
            }
            Vec3 look = user.getLookAngle();
            return user.position().add(
                    look.x * 60.0D,
                    60.0D,
                    look.z * 60.0D);
        }
    }
}
