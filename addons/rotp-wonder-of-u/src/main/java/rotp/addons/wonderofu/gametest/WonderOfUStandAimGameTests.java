package rotp.addons.wonderofu.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.wonderofu.WonderOfUAddon;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.util.functions.MathUtil;

/**
 * 1.16 StandEntity.aimWithStandOrUser(100, task.getTarget()), which LightningStrike and ExplodeVariations aimed with: a
 * Stand that follows its user turns toward what the user's precision ray meets by at most getAttackSpeed() / 16 * 18
 * degrees, then casts its own precision ray from its eyes (StandEntity.precisionRayTrace): hitboxes grown by the Stand's
 * precision, a player or Stand before a hostile mob before the rest.
 */
@GameTestHolder("rotp_wou_car")
@PrefixGameTestTemplate(false)
public final class WonderOfUStandAimGameTests {
    private WonderOfUStandAimGameTests() {}

    @GameTest(template = "empty", batch = "wou_aim_explosion", timeoutTicks = 100)
    public static void explosionStrikesOnlyWhatTheTurnedStandMeets(GameTestHelper helper) {
        try (Rig rig = new Rig(helper, "wou-aim-explosion")) {
            Mob cow = rig.spawn(EntityType.COW, 6.0D, 0.5D, 0.0D);
            rig.userLooksEast();
            float maxAngle = (float) rig.stand.getAttackSpeed() / 16.0F * 18.0F;
            float away = Math.min(170.0F, maxAngle * 2.0F + 30.0F);

            // 1.16 turned a Stand that far off by one turn only and cast its ray from the Stand's eyes, so the explosion
            // met no entity; a ray from the user's eyes would have met the cow
            rig.faceStand(cow.getEyePosition(), away);
            rig.perform("explosion");
            helper.assertTrue(rig.strikes.isEmpty(),
                    "1.16 ExplodeVariations aimed with the Stand, turned " + away + " degrees off its user's aim by at"
                            + " most " + maxAngle + " degrees, so its ray met no entity and nothing exploded; it exploded at "
                            + rig.strikes);

            rig.faceStand(cow.getEyePosition(), maxAngle / 2.0F);
            rig.perform("explosion");
            AABB hitbox = HitResultUtil.standPrecisionTargetHitbox(cow.getBoundingBox(), rig.stand.getPrecision())
                    .inflate(0.01D);
            helper.assertTrue(rig.strikes.size() == 1 && hitbox.contains(rig.strikes.get(0)),
                    "1.16 turned a Stand " + maxAngle / 2.0F + " degrees off the user's aim onto the cow, so the"
                            + " explosion came at the cow " + hitbox + "; it came at " + rig.strikes);
            LogUtils.getLogger().info("R770_WOU_AIM_PASS explosion_turn maxAngle={} strike={}", maxAngle,
                    rig.strikes.get(0));
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "wou_aim_precision", timeoutTicks = 100)
    public static void lightningMeetsAMobBesideTheRayByTheStandsPrecision(GameTestHelper helper) {
        try (Rig rig = new Rig(helper, "wou-aim-precision")) {
            // the user's ray passes a block beside the cow: its own box is missed, its precision box is met
            Mob cow = rig.spawn(EntityType.COW, 12.0D, 0.5D, 1.0D);
            rig.userLooksEast();
            float maxAngle = (float) rig.stand.getAttackSpeed() / 16.0F * 18.0F;
            helper.assertTrue(!cow.getBoundingBox().intersects(rig.userRay(40.0D)),
                    "Setup: the user's eye line meets the cow's own box");

            // off by less than one turn, by more than the Stand's precision box lets its own ray reach the cow
            rig.faceStand(cow.getEyePosition(), maxAngle - 0.5F);
            rig.perform("lightning_strike");
            AABB hitbox = HitResultUtil.standPrecisionTargetHitbox(cow.getBoundingBox(), rig.stand.getPrecision())
                    .inflate(0.01D);
            helper.assertTrue(rig.strikes.size() == 1 && hitbox.contains(rig.strikes.get(0)),
                    "1.16 grew the hitboxes by the Stand's precision (" + rig.stand.getPrecision() + "), so the user's"
                            + " ray met the cow beside it, turned the Stand onto the cow and the bolt struck the cow "
                            + hitbox + "; it struck at " + rig.strikes);
            LogUtils.getLogger().info("R770_WOU_AIM_PASS lightning_precision strike={}", rig.strikes.get(0));
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "wou_aim_priority", timeoutTicks = 100)
    public static void lightningPicksAHostileMobBeforeANearerAnimal(GameTestHelper helper) {
        try (Rig rig = new Rig(helper, "wou-aim-priority")) {
            Mob cow = rig.spawn(EntityType.COW, 5.0D, 0.5D, 0.0D);
            Mob zombie = rig.spawn(EntityType.ZOMBIE, 10.0D, 0.0D, 0.0D);
            rig.userLooksEast();
            rig.faceStand(zombie.getEyePosition(), 0.0F);
            AABB cowHitbox = HitResultUtil.standPrecisionTargetHitbox(cow.getBoundingBox(), rig.stand.getPrecision());
            helper.assertTrue(cowHitbox.clip(rig.stand.getEyePosition(), zombie.getEyePosition()).isPresent(),
                    "Setup: the Stand's ray to the zombie misses the nearer cow's precision box " + cowHitbox);
            rig.perform("lightning_strike");
            AABB hitbox = HitResultUtil.standPrecisionTargetHitbox(zombie.getBoundingBox(), rig.stand.getPrecision())
                    .inflate(0.01D);
            helper.assertTrue(rig.strikes.size() == 1 && hitbox.contains(rig.strikes.get(0)),
                    "1.16 precisionRayTrace took a hostile mob before a nearer animal on the ray, so the bolt struck the"
                            + " zombie " + hitbox + " behind the cow; it struck at " + rig.strikes);
            LogUtils.getLogger().info("R770_WOU_AIM_PASS lightning_priority strike={}", rig.strikes.get(0));
            helper.succeed();
        }
    }

    private static final class Rig implements AutoCloseable {
        final ServerLevel level;
        final Player user;
        final Vec3 pos;
        final List<Vec3> strikes = new ArrayList<>();
        StandPower power;
        StandEntity stand;
        private final List<Entity> spawned = new ArrayList<>();
        private boolean listening;
        private final Consumer<EntityJoinLevelEvent> joinListener;
        private final Consumer<ExplosionEvent.Start> explosionListener;

        Rig(GameTestHelper helper, String name) {
            level = helper.getLevel();
            user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), name)) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            // the west edge of the test's chunk, so that every mob below stands in that chunk
            ChunkPos chunk = new ChunkPos(helper.absolutePos(new BlockPos(0, 2, 0)));
            pos = new Vec3(chunk.getMinBlockX() + 1.5D, helper.absoluteVec(new Vec3(0.0D, 12.0D, 0.0D)).y,
                    chunk.getMinBlockZ() + 8.5D);
            joinListener = event -> {
                if (event.getLevel() == level && (event.getEntity() instanceof LightningBolt
                        || event.getEntity() instanceof PrimedTnt || event.getEntity() instanceof Creeper)) {
                    strikes.add(event.getEntity().position());
                    event.setCanceled(true);
                }
            };
            explosionListener = event -> {
                if (event.getLevel() == level) {
                    strikes.add(event.getExplosion().center());
                    event.setCanceled(true);
                }
            };
            try {
                user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
                user.setNoGravity(true);
                user.moveTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
                helper.assertTrue(level.addFreshEntity(user), "Could not add the Wonder of U user");
                power = PowerClass.STAND.attachGet(user);
                helper.assertTrue(StandPowerTransitions.insert(power,
                                new StandInstance(WonderOfUAddon.WONDER_OF_YOU.get())).status()
                                == StandPowerTransitions.Status.APPLIED
                                && WonderOfUAddon.WONDER_OF_YOU.get().summon(user, power),
                        "Could not summon Wonder of U");
                power.skipProgression();
                power.setStamina(power.getMaxStamina());
                stand = power.getSummonedStandEntity();
                helper.assertTrue(stand.isFollowingUser() && !stand.isManuallyControlled(),
                        "Setup: Wonder of U is not a Stand following its user");
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, joinListener);
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ExplosionEvent.Start.class, explosionListener);
                listening = true;
            }
            catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        <E extends Mob> E spawn(EntityType<E> type, double east, double up, double south) {
            E mob = type.create(level);
            mob.moveTo(pos.x + east, pos.y + up, pos.z + south, 0.0F, 0.0F);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setSilent(true);
            if (!level.addFreshEntity(mob)) {
                throw new IllegalStateException("Could not add a " + type);
            }
            spawned.add(mob);
            return mob;
        }

        void userLooksEast() {
            user.setYRot(-90.0F);
            user.yRotO = -90.0F;
            user.setYHeadRot(-90.0F);
            user.yHeadRotO = -90.0F;
            user.setXRot(0.0F);
            user.xRotO = 0.0F;
            user.yBodyRot = -90.0F;
            user.yBodyRotO = -90.0F;
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
            stand.updatePosition(user);
        }

        AABB userRay(double length) {
            Vec3 eyes = user.getEyePosition();
            return new AABB(eyes, eyes.add(user.getLookAngle().scale(length))).inflate(0.001D);
        }

        // the Stand looks at the point, yawOff degrees off in yaw
        void faceStand(Vec3 point, float yawOff) {
            Vec3 toPoint = point.subtract(stand.getEyePosition());
            stand.setYRot(MathUtil.yRotDegFromVec(toPoint) + yawOff);
            stand.setXRot(MathUtil.xRotDegFromVec(toPoint));
        }

        void perform(String abilityName) {
            strikes.clear();
            power.setStamina(power.getMaxStamina());
            EntityActionInstance action = ((EntityActionType) power.getAbility(abilityName))
                    .initActionOnAbilityUse(level, user, stand, null);
            LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
            action.setPhaseStart(ActionPhase.PERFORM);
            action._tickAction();
        }

        @Override
        public void close() {
            if (listening) {
                NeoForge.EVENT_BUS.unregister(joinListener);
                NeoForge.EVENT_BUS.unregister(explosionListener);
                listening = false;
            }
            try {
                if (power != null && power.isSummoned()) {
                    WonderOfUAddon.WONDER_OF_YOU.get().forceUnsummon(user, power);
                }
            }
            finally {
                spawned.forEach(Entity::discard);
                user.discard();
            }
        }
    }
}
