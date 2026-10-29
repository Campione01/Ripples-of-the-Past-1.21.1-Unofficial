package rotp.addons.d4c;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.customobjects.entity_projectile.BlockShardEntity;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModParticles;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.MathUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CRapidPunchStrikeGameTests {
    private D4CRapidPunchStrikeGameTests() {}

    /**
     * 1.16 aa/aa/ac.java standTickPerform: each rapid punch hit was StandEntity.punch at the task's target, struck
     * while the Stand reached it (aimWithThisOrUser) whatever the user looked at, otherwise aimed from the user's view
     * again; what a hit struck became the task's target (setTaskTarget), the one the next hit took.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_rapid_punch_strike")
    public static void rapidPunchStrikesItsTaskTargetAsTheDonorDid(GameTestHelper helper) {
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "d4c-rapid-punch-strike")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        Cow kept = sturdyCow(helper);
        Cow looked = sturdyCow(helper);
        BlockPos block = helper.absolutePos(new BlockPos(4, 13, 2));
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the D4C user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant D4C");
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(type.summon(user, power), "Could not summon D4C");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && !stand.isArmsOnlyMode(), "setup: D4C is not a full Stand");
            place(helper, kept, user.position().add(2.0D, 0.0D, 0.0D));
            place(helper, looked, user.position().add(0.0D, 0.0D, -6.0D));

            // pressed at the cow east of the user, who then looks south at a second cow, both in D4C's reach
            turn(user, -90.0F);
            aim(user, stand, new ActionTarget(kept));
            EntityActionInstance rapid = start(helper, user, power, stand);
            helper.assertTrue(rapid.getTaskTarget(helper.getLevel()).getEntity() == kept,
                    "setup: the rapid punch did not keep the cow: " + rapid.getTaskTarget(helper.getLevel()));
            stand.updatePosition(user);
            turn(user, 0.0F);
            place(helper, looked, user.position().add(0.0D, 0.0D, 2.0D));
            aim(user, stand, new ActionTarget(looked));
            for (int tick = 0; tick < 20 && !hurt(kept) && !hurt(looked); tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            helper.assertTrue(hurt(kept) && !hurt(looked),
                    "1.16 StandEntity.punch struck the cow the rapid punch kept, not the one its user looked at "
                            + "(struck: kept " + hurt(kept) + ", looked at " + hurt(looked) + ")");

            // the kept cow is now out of the Stand's reach, still within the task's 8 blocks
            place(helper, kept, user.position().add(5.0D, 0.0D, 0.0D));
            for (int tick = 0; tick < 4 && !hurt(looked); tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            helper.assertTrue(hurt(looked),
                    "1.16 aimed the next hit from the user's view again once the kept cow was out of reach, at "
                            + "the cow the user looked at");
            ActionTarget after = rapid.getTaskTarget(helper.getLevel());
            helper.assertTrue(after.getEntity() == looked,
                    "1.16 StandEntity.punch made the cow the hit struck its task's target; the task holds " + after);
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);

            // pressed at a dirt block east of the user: the light punch's StandBlockPunch breaks it
            stand.setPos(user.position());
            turn(user, -90.0F);
            helper.getLevel().setBlockAndUpdate(block, Blocks.DIRT.defaultBlockState());
            aim(user, stand, new ActionTarget(block, Direction.WEST));
            EntityActionInstance atBlock = start(helper, user, power, stand);
            helper.assertTrue(block.equals(atBlock.getTaskTarget(helper.getLevel()).getBlockPos()),
                    "setup: the rapid punch did not keep the block: " + atBlock.getTaskTarget(helper.getLevel()));
            stand.updatePosition(user);
            for (int tick = 0; tick < 20 && !helper.getLevel().getBlockState(block).isAir(); tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            helper.assertTrue(helper.getLevel().getBlockState(block).isAir(),
                    "1.16 StandEntity.punch broke the dirt block the rapid punch kept (StandBlockPunch); it is "
                            + helper.getLevel().getBlockState(block));
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            helper.succeed();
        }
        finally {
            helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
            kept.discard();
            looked.discard();
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 aa/aa/ac.java: standPerform (StandEntityLightAttack.standPerform: 0.1 to the finisher meter and a punch)
     * on perform tick 0, then standTickPerform's hit on every even task tick while ae was under 6: punches on ticks
     * 0, 0, 2, 4, 6, 8 and 10, each of ac.punchEntity's 0.05 finisher. Its stopTask after the sixth was refused in
     * PERFORM (StandEntityAction.canBeCanceled: no hold, no new action, not RECOVERY).
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_rapid_punch_count")
    public static void rapidPunchStrikesSevenTimesOnTheDonorsTicks(GameTestHelper helper) {
        Player user = testUser(helper, "d4c-rapid-punch-count");
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        Cow cow = sturdyCow(helper);
        List<String> hits = new ArrayList<>();
        EntityActionInstance[] running = new EntityActionInstance[1];
        Consumer<LivingDamageEvent.Post> observer = event -> {
            if (event.getEntity() == cow && running[0] != null) {
                hits.add(running[0].getPhase() + ":" + (int) running[0].getPhaseTick());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDamageEvent.Post.class, observer);
        try {
            power = grantAndSummon(helper, user);
            StandEntity stand = power.getSummonedStandEntity();
            place(helper, cow, user.position().add(2.0D, 0.0D, 0.0D));
            turn(user, -90.0F);
            aim(user, stand, new ActionTarget(cow));
            EntityActionInstance rapid = start(helper, user, power, stand);
            running[0] = rapid;
            stand.updatePosition(user);
            for (int tick = 0; tick < 10 && rapid.getPhase() != ActionPhase.PERFORM; tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            helper.assertTrue(rapid.getPhase() == ActionPhase.PERFORM && rapid.getPhaseTick() < 1.0F,
                    "setup: the rapid punch did not reach its perform: " + rapid.getPhase());
            helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(stand, new ActionTarget(cow))
                            .getEntity() == cow,
                    "setup: the cow is not in D4C's reach");
            float meter = stand.getFinisherMeter();
            LivingComponentAction.getComponent(stand).tick();
            float added = stand.getFinisherMeter() - meter;
            for (int tick = 0; tick < 40 && stand.getCurStandAction() == rapid && !rapid.isOver(); tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            List<String> expected = List.of("PERFORM:0", "PERFORM:0", "PERFORM:2", "PERFORM:4", "PERFORM:6",
                    "PERFORM:8", "PERFORM:10");
            helper.assertTrue(hits.equals(expected),
                    "1.16 ac.java punched 7 times, at perform start and on task ticks 0, 2, 4, 6, 8 and 10: "
                            + expected + "; the port struck " + hits);
            helper.assertTrue(Math.abs(added - (D4CPhase2BSpec.RAPID_PERFORM_FINISHER
                            + 2 * D4CPhase2BSpec.RAPID_FINISHER_PER_HIT)) < 1.0E-4F,
                    "1.16 added 0.1 (StandEntityLightAttack.standPerform) and 0.05 for each of the two punches of "
                            + "perform tick 0 to the finisher meter; it added " + added);
            helper.succeed();
        }
        finally {
            NeoForge.EVENT_BUS.unregister(observer);
            running[0] = null;
            cow.discard();
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 aa/aa/ac.java:117-118: standTickPerform played STAND_PUNCH_SWING at the Stand, volume 0.5, on every one of
     * the 15 perform ticks; ac.java:158: each punch that hurt the cow played its impact sound STAND_PUNCH_LIGHT at the
     * middle of the cow's box (IHasStandPunch.playPunchImpactSound), 7 in all.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_rapid_punch_sounds")
    public static void rapidPunchPlaysTheDonorsSwingAndImpactSounds(GameTestHelper helper) {
        Player user = testUser(helper, "d4c-rapid-punch-sounds");
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        Cow cow = sturdyCow(helper);
        List<String> swings = new ArrayList<>();
        List<String> impacts = new ArrayList<>();
        EntityActionInstance[] running = new EntityActionInstance[1];
        StandEntity[] standOf = new StandEntity[1];
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (running[0] == null || event.getLevel() != helper.getLevel() || event.getSound() == null) {
                return;
            }
            String when = running[0].getPhase() + ":" + (int) running[0].getPhaseTick();
            if (event.getSound().value() == ModSoundEvents.STAND_PUNCH_SWING.get()) {
                swings.add(when + (event.getOriginalVolume() == 0.5F
                        && event.getPosition().distanceTo(standOf[0].position()) < 1.0E-6D ? "" : " off"));
            }
            else if (event.getSound().value() == ModSoundEvents.STAND_PUNCH_LIGHT.get()) {
                impacts.add(when + (event.getOriginalVolume() == 1.0F
                        && event.getPosition().distanceTo(cow.getBoundingBox().getCenter()) < 1.0E-6D ? "" : " off"));
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, PlayLevelSoundEvent.AtPosition.class, listener);
        try {
            power = grantAndSummon(helper, user);
            StandEntity stand = power.getSummonedStandEntity();
            standOf[0] = stand;
            place(helper, cow, user.position().add(2.0D, 0.0D, 0.0D));
            turn(user, -90.0F);
            aim(user, stand, new ActionTarget(cow));
            EntityActionInstance rapid = start(helper, user, power, stand);
            running[0] = rapid;
            stand.updatePosition(user);
            for (int tick = 0; tick < 40 && stand.getCurStandAction() == rapid && !rapid.isOver(); tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            List<String> expectedSwings = new ArrayList<>();
            for (int tick = 0; tick < 15; tick++) {
                expectedSwings.add("PERFORM:" + tick);
            }
            helper.assertTrue(swings.equals(expectedSwings),
                    "1.16 ac.java:117-118 swung STAND_PUNCH_SWING at the Stand at volume 0.5 on each of the 15 "
                            + "perform ticks: " + expectedSwings + "; the port played " + swings);
            List<String> expectedImpacts = List.of("PERFORM:0", "PERFORM:0", "PERFORM:2", "PERFORM:4", "PERFORM:6",
                    "PERFORM:8", "PERFORM:10");
            helper.assertTrue(impacts.equals(expectedImpacts),
                    "1.16 ac.java:158 gave each punch that hurt the cow STAND_PUNCH_LIGHT at the middle of its box: "
                            + expectedImpacts + "; the port played " + impacts);
            helper.succeed();
        }
        finally {
            NeoForge.EVENT_BUS.unregister(listener);
            running[0] = null;
            cow.discard();
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 StandEntityPunch.doHit (punch/StandEntityPunch.java:241) added the punch's finisher (ac.punchEntity's 0.05)
     * after every entity hit, also one that did not hurt: 0.1 from standPerform and 0.05 for each of the 7 punches.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_rapid_punch_finisher_unhurt")
    public static void rapidPunchAddsItsFinisherOnHitsThatDoNotHurt(GameTestHelper helper) {
        Player user = testUser(helper, "d4c-rapid-punch-unhurt");
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        Cow cow = sturdyCow(helper);
        try {
            cow.setInvulnerable(true);
            power = grantAndSummon(helper, user);
            StandEntity stand = power.getSummonedStandEntity();
            place(helper, cow, user.position().add(2.0D, 0.0D, 0.0D));
            turn(user, -90.0F);
            aim(user, stand, new ActionTarget(cow));
            EntityActionInstance rapid = start(helper, user, power, stand);
            stand.updatePosition(user);
            float meter = stand.getFinisherMeter();
            for (int tick = 0; tick < 60 && stand.getCurStandAction() == rapid && !rapid.isOver()
                    && rapid.getPhase() != ActionPhase.RECOVERY; tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            float added = stand.getFinisherMeter() - meter;
            helper.assertTrue(!hurt(cow) && rapid.getPhase() == ActionPhase.RECOVERY,
                    "setup: the invulnerable cow was hurt, or the rapid punch did not reach its recovery: "
                            + rapid.getPhase());
            float expected = D4CPhase2BSpec.RAPID_PERFORM_FINISHER
                    + (D4CPhase2BSpec.RAPID_HITS + 1) * D4CPhase2BSpec.RAPID_FINISHER_PER_HIT;
            helper.assertTrue(Math.abs(added - expected) < 1.0E-4F,
                    "1.16 StandEntityPunch.doHit added 0.05 for each of the 7 punches at the cow, though none hurt "
                            + "it, after standPerform's 0.1: " + expected + "; the port added " + added);
            helper.succeed();
        }
        finally {
            cow.discard();
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 aa/aa/ac.java:123-131: while the task still held an entity that had died (StandEntityTask.keepTarget kept it
     * until removed or 20 ticks dead) a hit punched nothing at all; its stopTask was refused in PERFORM. A mob the user
     * turns to is not struck by the rest of the combo.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_rapid_punch_dead")
    public static void rapidPunchStrikesNothingWhileItsTaskHoldsADeadMob(GameTestHelper helper) {
        Player user = testUser(helper, "d4c-rapid-punch-dead");
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        Cow frail = EntityType.COW.create(helper.getLevel());
        Cow looked = sturdyCow(helper);
        try {
            helper.assertTrue(frail != null, "setup: no cow");
            frail.setNoAi(true);
            frail.setNoGravity(true);
            frail.setHealth(1.0F);
            power = grantAndSummon(helper, user);
            StandEntity stand = power.getSummonedStandEntity();
            place(helper, frail, user.position().add(2.0D, 0.0D, 0.0D));
            turn(user, -90.0F);
            aim(user, stand, new ActionTarget(frail));
            EntityActionInstance rapid = start(helper, user, power, stand);
            stand.updatePosition(user);
            for (int tick = 0; tick < 20 && frail.isAlive(); tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            helper.assertTrue(!frail.isAlive() && !frail.isRemoved() && rapid.getPhase() == ActionPhase.PERFORM,
                    "setup: the first punch did not kill the cow: alive " + frail.isAlive() + " in "
                            + rapid.getPhase());
            turn(user, 0.0F);
            place(helper, looked, user.position().add(0.0D, 0.0D, 2.0D));
            aim(user, stand, new ActionTarget(looked));
            helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(stand, new ActionTarget(looked))
                            .getEntity() == looked,
                    "setup: the cow the user looks at is not in D4C's reach");
            for (int tick = 0; tick < 40 && stand.getCurStandAction() == rapid && !rapid.isOver(); tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            helper.assertTrue(!hurt(looked),
                    "1.16 ac.java punched nothing while its task held the dead cow; the port struck the cow the "
                            + "user turned to");
            helper.succeed();
        }
        finally {
            if (frail != null) {
                frail.discard();
            }
            looked.discard();
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 StandEntityLightAttack.punchBlock (StandEntityLightAttack.java:73-76), which ac.java kept, gave the rapid
     * punch's block hit the impact sound STAND_PUNCH_LIGHT at the block's centre after the hit, when the block as it was
     * struck did not have hardness 0 (StandBlockPunch.playImpactSound, StandBlockPunch.java:70): once for a dirt block
     * the first hit breaks, never for a slime block.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_rapid_punch_block_sound")
    public static void rapidPunchGivesABlockTheDonorsImpactSound(GameTestHelper helper) {
        Player user = testUser(helper, "d4c-rapid-punch-block-sound");
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        BlockPos block = helper.absolutePos(new BlockPos(4, 13, 2));
        List<Vec3> impacts = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() == helper.getLevel() && event.getSound() != null
                    && event.getSound().value() == ModSoundEvents.STAND_PUNCH_LIGHT.get()) {
                impacts.add(event.getPosition());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, PlayLevelSoundEvent.AtPosition.class, listener);
        try {
            power = grantAndSummon(helper, user);
            StandEntity stand = power.getSummonedStandEntity();
            turn(user, -90.0F);
            for (Block kind : new Block[] {Blocks.DIRT, Blocks.SLIME_BLOCK}) {
                impacts.clear();
                stand.setPos(user.position());
                helper.getLevel().setBlockAndUpdate(block, kind.defaultBlockState());
                ActionTarget target = new ActionTarget(block, Direction.WEST);
                aim(user, stand, target);
                EntityActionInstance rapid = start(helper, user, power, stand);
                stand.updatePosition(user);
                helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(stand, target).getType()
                                == ActionTarget.TargetType.BLOCK,
                        "setup: the " + kind + " is not in D4C's reach");
                for (int tick = 0; tick < 40 && stand.getCurStandAction() == rapid
                        && rapid.getPhase() != ActionPhase.RECOVERY; tick++) {
                    LivingComponentAction.getComponent(stand).tick();
                }
                helper.assertTrue(helper.getLevel().getBlockState(block).isAir(),
                        "setup: the rapid punch did not break the " + kind + ": "
                                + helper.getLevel().getBlockState(block));
                int expected = kind == Blocks.DIRT ? 1 : 0;
                helper.assertTrue(impacts.size() == expected && impacts.stream()
                                .allMatch(pos -> pos.distanceTo(Vec3.atCenterOf(block)) < 1.0E-6D),
                        "1.16 StandBlockPunch played STAND_PUNCH_LIGHT at the centre " + Vec3.atCenterOf(block)
                                + " of a struck block that did not have hardness 0, " + expected + " time(s) for the "
                                + kind + "; the port played it at " + impacts);
                LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            }
            helper.succeed();
        }
        finally {
            NeoForge.EVENT_BUS.unregister(listener);
            helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
            helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(block).inflate(8.0D)).forEach(Entity::discard);
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 ac.punchEntity, a StandEntityPunch that StandEntity.attackTarget dealt to any entity, struck a minecart too:
     * each of the 7 punches tried to hurt it (StandEntity.hurtTarget), added its 0.05 to the finisher meter, played
     * STAND_PUNCH_LIGHT at the middle of its box although it was not hurt (playPunchSound for an entity that is not
     * living), and StandEntityPunch.knockbackTarget set its motion to half the additional knockback away from the Stand,
     * 0.75 after the sixth hit's 1.5. A minecart that can be hurt is broken.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_rapid_punch_not_living")
    public static void rapidPunchStrikesEntitiesThatAreNotLiving(GameTestHelper helper) {
        Player user = testUser(helper, "d4c-rapid-punch-not-living");
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        Minecart sturdy = EntityType.MINECART.create(helper.getLevel());
        Minecart frail = EntityType.MINECART.create(helper.getLevel());
        List<String> impacts = new ArrayList<>();
        EntityActionInstance[] running = new EntityActionInstance[1];
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (running[0] != null && event.getLevel() == helper.getLevel() && event.getSound() != null
                    && event.getSound().value() == ModSoundEvents.STAND_PUNCH_LIGHT.get()) {
                impacts.add(running[0].getPhase() + ":" + (int) running[0].getPhaseTick()
                        + (event.getPosition().distanceTo(sturdy.getBoundingBox().getCenter()) < 1.0E-6D ? "" : " off"));
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, PlayLevelSoundEvent.AtPosition.class, listener);
        try {
            helper.assertTrue(sturdy != null && frail != null, "setup: no minecart");
            power = grantAndSummon(helper, user);
            StandEntity stand = power.getSummonedStandEntity();
            turn(user, -90.0F);
            sturdy.setInvulnerable(true);
            sturdy.setNoGravity(true);
            sturdy.moveTo(user.position().add(2.0D, 0.0D, 0.0D));
            helper.assertTrue(helper.getLevel().addFreshEntity(sturdy), "setup: the minecart was not added");
            lookAt(user, sturdy.getBoundingBox().getCenter());
            aim(user, stand, new ActionTarget(sturdy));
            EntityActionInstance rapid = start(helper, user, power, stand);
            running[0] = rapid;
            stand.updatePosition(user);
            helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(stand, new ActionTarget(sturdy))
                            .getEntity() == sturdy,
                    "setup: the minecart is not in D4C's reach");
            float meter = stand.getFinisherMeter();
            for (int tick = 0; tick < 40 && stand.getCurStandAction() == rapid
                    && rapid.getPhase() != ActionPhase.RECOVERY; tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            running[0] = null;
            float added = stand.getFinisherMeter() - meter;
            List<String> expected = List.of("PERFORM:0", "PERFORM:0", "PERFORM:2", "PERFORM:4", "PERFORM:6",
                    "PERFORM:8", "PERFORM:10");
            helper.assertTrue(impacts.equals(expected),
                    "1.16 StandEntity.attackTarget played STAND_PUNCH_LIGHT at the middle of the minecart for each of "
                            + "the 7 punches, hurt or not: " + expected + "; the port played " + impacts);
            float expectedMeter = D4CPhase2BSpec.RAPID_PERFORM_FINISHER
                    + (D4CPhase2BSpec.RAPID_HITS + 1) * D4CPhase2BSpec.RAPID_FINISHER_PER_HIT;
            helper.assertTrue(Math.abs(added - expectedMeter) < 1.0E-4F,
                    "1.16 StandEntityPunch.doHit added 0.05 for each punch at the minecart after standPerform's 0.1: "
                            + expectedMeter + "; the port added " + added);
            Vec3 toCart = sturdy.position().subtract(stand.position());
            float yaw = (float) -Mth.atan2(toCart.x, toCart.z) * Mth.RAD_TO_DEG;
            Vec3 push = new Vec3(0.0D, 0.0D, D4CPhase2BSpec.RAPID_FINAL_BASE_KNOCKBACK * 0.5D)
                    .yRot(-yaw * Mth.DEG_TO_RAD);
            helper.assertTrue(sturdy.getDeltaMovement().distanceTo(push) < 1.0E-6D,
                    "1.16 StandEntityPunch.knockbackTarget set the minecart's motion to " + push
                            + " after the last punch; it is " + sturdy.getDeltaMovement());
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);

            sturdy.discard();
            stand.setPos(user.position());
            frail.setNoGravity(true);
            frail.moveTo(user.position().add(2.0D, 0.0D, 0.0D));
            helper.assertTrue(helper.getLevel().addFreshEntity(frail), "setup: the minecart was not added");
            lookAt(user, frail.getBoundingBox().getCenter());
            aim(user, stand, new ActionTarget(frail));
            EntityActionInstance second = start(helper, user, power, stand);
            stand.updatePosition(user);
            for (int tick = 0; tick < 40 && stand.getCurStandAction() == second
                    && second.getPhase() != ActionPhase.RECOVERY && !frail.isRemoved(); tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            helper.assertTrue(frail.isRemoved() || frail.getDamage() > 0.0F,
                    "1.16 StandEntity.hurtTarget hurt a minecart the rapid punch struck; it is removed "
                            + frail.isRemoved() + ", damage " + frail.getDamage());
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            helper.succeed();
        }
        finally {
            NeoForge.EVENT_BUS.unregister(listener);
            running[0] = null;
            if (sturdy != null) {
                sturdy.discard();
            }
            if (frail != null) {
                frail.discard();
            }
            helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(user.blockPosition()).inflate(8.0D)).forEach(Entity::discard);
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 StandEntityLightAttack.noFinisherBarDecay (StandEntityLightAttack.java:160), which ac.java kept: the finisher
     * meter did not decay while the rapid punch was the Stand's task (StandEntity.tickFinisherMeter), and decayed again
     * once it was over.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_rapid_punch_no_decay")
    public static void rapidPunchKeepsTheFinisherMeterFromDecaying(GameTestHelper helper) {
        Player user = testUser(helper, "d4c-rapid-punch-no-decay");
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        try {
            power = grantAndSummon(helper, user);
            StandEntity stand = power.getSummonedStandEntity();
            Field noDecay = StandEntity.class.getDeclaredField("noFinisherDecayTicks");
            noDecay.setAccessible(true);
            java.lang.reflect.Method tickMeter = StandEntity.class.getDeclaredMethod("tickFinisherMeter");
            tickMeter.setAccessible(true);
            turn(user, 0.0F);
            aim(user, stand, ActionTarget.EMPTY);
            stand.setFinisherMeter(0.8F);
            start(helper, user, power, stand);
            noDecay.setInt(stand, 0);
            for (int tick = 0; tick < 10; tick++) {
                tickMeter.invoke(stand);
            }
            helper.assertTrue(Math.abs(stand.getFinisherMeter() - 0.8F) < 1.0E-4F,
                    "1.16 StandEntityLightAttack.noFinisherBarDecay kept the finisher meter at 0.8 through the rapid "
                            + "punch; the port let it decay to " + stand.getFinisherMeter());
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            tickMeter.invoke(stand);
            helper.assertTrue(stand.getFinisherMeter() < 0.8F - 1.0E-4F,
                    "setup: the finisher meter does not decay without an action: " + stand.getFinisherMeter());
            helper.succeed();
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        finally {
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 aa/aa/aa.java: d4c_hand_chop, a StandEntityHeavyAttack, punched the task's target (standPerform:
     * StandEntity.punch(task, this, task.getTarget())), the entity it was pressed at, while the Stand reached it, not
     * what the user looked at by then; the strike's target became the task's.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_hand_chop_strike")
    public static void handChopStrikesItsTaskTargetAsTheDonorDid(GameTestHelper helper) {
        Player user = testUser(helper, "d4c-hand-chop-strike");
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        Cow kept = sturdyCow(helper);
        Cow looked = sturdyCow(helper);
        BlockPos block = helper.absolutePos(new BlockPos(2, 13, 4));
        try {
            power = grantAndSummon(helper, user);
            StandEntity stand = power.getSummonedStandEntity();
            place(helper, kept, user.position().add(1.5D, 0.0D, 0.0D));
            place(helper, looked, user.position().add(0.0D, 0.0D, -6.0D));
            turn(user, -90.0F);
            aim(user, stand, new ActionTarget(kept));
            EntityActionInstance chop = start(helper, user, power, stand, "d4c_hand_chop");
            helper.assertTrue(chop.getTaskTarget(helper.getLevel()).getEntity() == kept,
                    "setup: the hand chop did not keep the cow: " + chop.getTaskTarget(helper.getLevel()));
            turn(user, 0.0F);
            place(helper, looked, user.position().add(0.0D, 0.0D, 1.5D));
            aim(user, stand, new ActionTarget(looked));
            stand.updatePosition(user);
            helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(stand, new ActionTarget(kept))
                            .getEntity() == kept
                            && StandEntityPunchAbility.validatePunchTarget(stand, new ActionTarget(looked))
                                    .getEntity() == looked,
                    "setup: both cows are not in D4C's reach");
            for (int tick = 0; tick < 20 && !hurt(kept) && !hurt(looked); tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            helper.assertTrue(hurt(kept) && !hurt(looked),
                    "1.16 StandEntityHeavyAttack.standPerform struck the cow the hand chop kept, not the one its user "
                            + "looked at (struck: kept " + hurt(kept) + ", looked at " + hurt(looked) + ")");
            ActionTarget after = chop.getTaskTarget(helper.getLevel());
            helper.assertTrue(after.getEntity() == kept,
                    "1.16 StandEntity.punch left the struck cow the hand chop's task target; it holds " + after);
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);

            // pressed at the cow, now out of reach, the strike meets a dirt block: a heavy attack does not keep a
            // block (standKeepsTarget), so setTaskTarget left the cow the task's target
            looked.discard();
            stand.setPos(user.position());
            place(helper, kept, user.position().add(5.0D, 0.0D, 0.0D));
            turn(user, -90.0F);
            aim(user, stand, new ActionTarget(kept));
            EntityActionInstance atBlock = start(helper, user, power, stand, "d4c_hand_chop");
            helper.assertTrue(atBlock.getTaskTarget(helper.getLevel()).getEntity() == kept,
                    "setup: the second hand chop did not keep the cow: "
                            + atBlock.getTaskTarget(helper.getLevel()));
            turn(user, 0.0F);
            helper.getLevel().setBlockAndUpdate(block, Blocks.DIRT.defaultBlockState());
            ActionTarget dirt = new ActionTarget(block, Direction.NORTH);
            aim(user, stand, dirt);
            stand.updatePosition(user);
            helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(stand, dirt).getType()
                            == ActionTarget.TargetType.BLOCK
                            && StandEntityPunchAbility.validatePunchTarget(stand, new ActionTarget(kept))
                                    .getType() == ActionTarget.TargetType.EMPTY,
                    "setup: the dirt block is not in D4C's reach, or the cow is");
            for (int tick = 0; tick < 20 && atBlock.getPhase() != ActionPhase.RECOVERY; tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            ActionTarget afterBlock = atBlock.getTaskTarget(helper.getLevel());
            helper.assertTrue(atBlock.getPhase() == ActionPhase.RECOVERY && afterBlock.getEntity() == kept,
                    "1.16 StandEntityTask.setTarget refused the block a hand chop struck and kept the cow; the task "
                            + "holds " + afterBlock + " in " + atBlock.getPhase());
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            helper.succeed();
        }
        finally {
            helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
            kept.discard();
            looked.discard();
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 ai/ah.java:72 registered d4c_hand_chop as a StandEntityHeavyAttack with punchSound THE_WORLD_PUNCH_HEAVY, so a
     * chop at a block was StandEntityHeavyAttack.punchBlock's HeavyPunchBlockInstance: the impact sound at the block, the
     * plain block punch by D4C's strength (obsidian breaks), then the blast along the Stand's look, which broke the glass
     * on both sides into shards.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_hand_chop_block")
    public static void handChopHitsABlockAsTheDonorsHeavyAttackDid(GameTestHelper helper) {
        Player user = testUser(helper, "d4c-hand-chop-block");
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        BlockPos block = helper.absolutePos(new BlockPos(2, 13, 4));
        List<BlockPos> glass = List.of(block.east(), block.west());
        AABB around = new AABB(block).inflate(8.0D);
        int[] impacts = new int[1];
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() == helper.getLevel() && event.getSound() != null
                    && event.getSound().value() == ModSoundEvents.THE_WORLD_PUNCH_HEAVY.get()
                    && event.getPosition().distanceTo(Vec3.atCenterOf(block)) < 1.0E-6D) {
                impacts[0]++;
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, PlayLevelSoundEvent.AtPosition.class, listener);
        try {
            power = grantAndSummon(helper, user);
            StandEntity stand = power.getSummonedStandEntity();
            stand.gradualSummonWeaknessTicks = 0;
            stand.updateStrengthMultipliers();
            helper.getLevel().setBlockAndUpdate(block, Blocks.OBSIDIAN.defaultBlockState());
            glass.forEach(pos -> helper.getLevel().setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()));
            turn(user, 0.0F);
            ActionTarget obsidian = new ActionTarget(block, Direction.NORTH);
            aim(user, stand, obsidian);
            EntityActionInstance chop = start(helper, user, power, stand, "d4c_hand_chop");
            stand.updatePosition(user);
            helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(stand, obsidian).getType()
                            == ActionTarget.TargetType.BLOCK,
                    "setup: the obsidian is not in D4C's reach");
            for (int tick = 0; tick < 20 && chop.getPhase() != ActionPhase.RECOVERY; tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            helper.assertTrue(chop.getPhase() == ActionPhase.RECOVERY,
                    "setup: the hand chop did not strike: " + chop.getPhase());
            List<BlockPos> standing = glass.stream()
                    .filter(pos -> !helper.getLevel().getBlockState(pos).isAir()).toList();
            int shards = helper.getLevel().getEntitiesOfClass(BlockShardEntity.class, around).size();
            helper.assertTrue(helper.getLevel().getBlockState(block).isAir() && standing.isEmpty() && shards > 0
                            && impacts[0] == 1,
                    "1.16 StandEntityHeavyAttack.punchBlock (HeavyPunchBlockInstance) broke the obsidian the hand chop "
                            + "struck, blasted the glass beside it into shards and played THE_WORLD_PUNCH_HEAVY once at "
                            + "the block; the port left the obsidian " + helper.getLevel().getBlockState(block)
                            + ", glass standing at " + standing + ", " + shards + " shards, " + impacts[0]
                            + " impact sounds");
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            helper.succeed();
        }
        finally {
            NeoForge.EVENT_BUS.unregister(listener);
            helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
            glass.forEach(pos -> helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
            helper.getLevel().getEntitiesOfClass(BlockShardEntity.class, around).forEach(Entity::discard);
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).forEach(Entity::discard);
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 aa/aa/aa.java:80-84: the hand chop's punchEntity was a plain StandEntityPunch (aa.aa), not the heavy punch's
     * HeavyPunchInstance. Its doHit struck any entity, a minecart too (StandEntity.hurtTarget), added the punch's
     * finisher, none, with StandEntity.FINISHER_NO_DECAY_TICKS (40), and StandEntity.attackTarget then played its impact
     * sound d4c_handchop at the middle of the entity's box, once the hit landed or always on an entity that is not
     * living. StandEntityHeavyAttack.noFinisherBarDecay kept the meter from decaying through the hand chop.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_hand_chop_entity")
    public static void handChopStrikesEntitiesAsTheDonorsPlainPunchDid(GameTestHelper helper) {
        Player user = testUser(helper, "d4c-hand-chop-entity");
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        Cow cow = sturdyCow(helper);
        Minecart cart = EntityType.MINECART.create(helper.getLevel());
        List<Vec3> chops = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() == helper.getLevel() && event.getSound() != null
                    && event.getSound().value() == D4CAddon.D4C_HANDCHOP.get()) {
                chops.add(event.getPosition());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, PlayLevelSoundEvent.AtPosition.class, listener);
        try {
            helper.assertTrue(cart != null, "setup: no minecart");
            cart.setNoGravity(true);
            power = grantAndSummon(helper, user);
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(power.getAbility("d4c_hand_chop") instanceof StandEntityAbility chopAbility
                            && chopAbility.noFinisherBarDecay,
                    "1.16 StandEntityHeavyAttack.noFinisherBarDecay kept D4C's finisher meter from decaying through "
                            + "the hand chop; the port's hand chop lets it decay");
            Field noDecay = StandEntity.class.getDeclaredField("noFinisherDecayTicks");
            noDecay.setAccessible(true);

            place(helper, cow, user.position().add(1.5D, 0.0D, 0.0D));
            turn(user, -90.0F);
            aim(user, stand, new ActionTarget(cow));
            stand.setFinisherMeter(1.0F);
            EntityActionInstance chop = start(helper, user, power, stand, "d4c_hand_chop");
            stand.updatePosition(user);
            noDecay.setInt(stand, 0);
            for (int tick = 0; tick < 20 && chop.getPhase() != ActionPhase.RECOVERY; tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            Vec3 cowMiddle = cow.getBoundingBox().getCenter();
            helper.assertTrue(hurt(cow), "setup: the hand chop did not strike the cow");
            helper.assertTrue(chops.size() == 1 && chops.get(0).distanceTo(cowMiddle) < 1.0E-6D,
                    "1.16 StandEntity.attackTarget played the hand chop's d4c_handchop once at the middle of the cow's "
                            + "box " + cowMiddle + "; the port played it at " + chops);
            helper.assertTrue(noDecay.getInt(stand) == 40,
                    "1.16 StandEntityPunch.doHit held the finisher meter from decaying for 40 ticks after the hand "
                            + "chop's hit; the port holds it " + noDecay.getInt(stand));
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);

            chops.clear();
            cow.discard();
            stand.setPos(user.position());
            cart.moveTo(user.position().add(1.5D, 0.0D, 0.0D));
            helper.assertTrue(helper.getLevel().addFreshEntity(cart), "setup: the minecart was not added");
            lookAt(user, cart.getBoundingBox().getCenter());
            aim(user, stand, new ActionTarget(cart));
            stand.setFinisherMeter(1.0F);
            EntityActionInstance atCart = start(helper, user, power, stand, "d4c_hand_chop");
            stand.updatePosition(user);
            Vec3 cartMiddle = cart.getBoundingBox().getCenter();
            for (int tick = 0; tick < 20 && atCart.getPhase() != ActionPhase.RECOVERY; tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            helper.assertTrue((cart.isRemoved() || cart.getDamage() > 0.0F) && chops.size() == 1
                            && chops.get(0).distanceTo(cartMiddle) < 1.0E-6D,
                    "1.16 StandEntityPunch struck a minecart the hand chop met (hurtTarget) and played d4c_handchop at "
                            + "the middle of its box " + cartMiddle + "; the port left it removed " + cart.isRemoved()
                            + ", damage " + cart.getDamage() + ", sounds at " + chops);
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            helper.succeed();
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        finally {
            NeoForge.EVENT_BUS.unregister(listener);
            cow.discard();
            if (cart != null) {
                cart.discard();
            }
            helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(user.blockPosition()).inflate(8.0D)).forEach(Entity::discard);
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 StandEntityHeavyAttack.onTaskSet (StandEntityHeavyAttack.java:141): the hand chop, a heavy attack, took 0.51
     * off the finisher meter as its task was set on the server.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_hand_chop_finisher_cost")
    public static void handChopTakesTheHeavyAttacksFinisherCost(GameTestHelper helper) {
        Player user = testUser(helper, "d4c-hand-chop-finisher");
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        try {
            power = grantAndSummon(helper, user);
            StandEntity stand = power.getSummonedStandEntity();
            turn(user, 0.0F);
            aim(user, stand, ActionTarget.EMPTY);
            stand.setFinisherMeter(0.8F);
            helper.assertTrue(Math.abs(stand.getFinisherMeter() - 0.8F) < 1.0E-4F,
                    "setup: the finisher meter is not 0.8: " + stand.getFinisherMeter());
            start(helper, user, power, stand, "d4c_hand_chop");
            helper.assertTrue(Math.abs(stand.getFinisherMeter() - (0.8F - 0.51F)) < 1.0E-4F,
                    "1.16 StandEntityHeavyAttack.onTaskSet took 0.51 off the finisher meter when the hand chop's task "
                            + "was set: 0.8 to 0.29; the port left " + stand.getFinisherMeter());
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 ai/ah.java:72 registered d4c_hand_chop with StandEntityHeavyAttack.Builder, the finisher variation of
     * d4c_heavy_punch (ah.java:89), and aa.java kept what StandEntityHeavyAttack gave it:
     * onClick recorded the meter (setHeavyPunchFinisher, StandEntityHeavyAttack.java:130-134); onTaskSet alternated the
     * Stand's hands (:139); the windup was getHeavyAttackWindup(speed, meter) (:187-189), the perform round(1 x 0.8)
     * and the recovery round(getHeavyAttackRecovery(speed, recorded meter) x 0.8) (aa.java:64-72); STAND_PUNCH_HEAVY_SWING
     * played at the Stand with 3 windup ticks left, volume 1, pitch 0.65 to 0.85 (:177-184); the perform cost the
     * Builder's 50 stamina (:272, :279); and its recovery was free (isFreeRecovery: isFinisher, :228), so another action
     * could replace it at once (StandEntityAction.canBeCanceled, :503-511).
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_hand_chop_heavy_attack")
    public static void handChopRunsAsTheDonorsHeavyAttack(GameTestHelper helper) {
        Player user = testUser(helper, "d4c-hand-chop-heavy");
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        List<String> swings = new ArrayList<>();
        EntityActionInstance[] running = new EntityActionInstance[1];
        StandEntity[] standOf = new StandEntity[1];
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (running[0] != null && event.getLevel() == helper.getLevel() && event.getSound() != null
                    && event.getSound().value() == ModSoundEvents.STAND_PUNCH_HEAVY_SWING.get()) {
                boolean donor = event.getOriginalVolume() == 1.0F && event.getOriginalPitch() >= 0.65F
                        && event.getOriginalPitch() < 0.85F + 1.0E-6F
                        && event.getPosition().distanceTo(standOf[0].position()) < 1.0E-6D;
                swings.add(running[0].getPhase() + ":" + (int) running[0].getPhaseTick() + (donor ? "" : " off"));
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, PlayLevelSoundEvent.AtPosition.class, listener);
        try {
            power = grantAndSummon(helper, user);
            StandEntity stand = power.getSummonedStandEntity();
            standOf[0] = stand;
            turn(user, 0.0F);
            aim(user, stand, ActionTarget.EMPTY);
            stand.setFinisherMeter(0.2F);
            stand.setHeavyPunchFinisher();
            stand.setFinisherMeter(0.6F);
            float speed = (float) stand.getAttackSpeed();
            int windup = StandStatFormulas.getHeavyAttackWindup(speed, 0.6F);
            int recovery = Math.round(StandStatFormulas.getHeavyAttackRecovery(speed, 0.6F) * 0.8F);
            float stamina = power.getStamina();
            // a press more than a tick after the last swing: StandEntity.alternateHands takes the other hand
            stand.tickCount += 10;
            InteractionHand hand = stand.getPunchingHand();

            EntityActionInstance chop = start(helper, user, power, stand, "d4c_hand_chop");
            running[0] = chop;
            helper.assertTrue(Math.abs(stand.getLastHeavyFinisherValue() - 0.6F) < 1.0E-4F,
                    "1.16 StandEntityHeavyAttack.onClick recorded the finisher meter at the press, 0.6 "
                            + "(setHeavyPunchFinisher); the port holds " + stand.getLastHeavyFinisherValue());
            helper.assertTrue(stand.getCurrentPunchingHand() == hand && stand.getPunchingHand() != hand,
                    "1.16 StandEntityHeavyAttack.onTaskSet alternated D4C's hands: the hand chop swings " + hand
                            + "; the port swings " + stand.getCurrentPunchingHand() + ", next "
                            + stand.getPunchingHand());
            String lengths = chop.phasesLength.getFloat(ActionPhase.WINDUP) + "/"
                    + chop.phasesLength.getFloat(ActionPhase.PERFORM) + "/"
                    + chop.phasesLength.getFloat(ActionPhase.RECOVERY);
            helper.assertTrue(chop.phasesLength.getFloat(ActionPhase.WINDUP) == windup,
                    "1.16 StandEntityHeavyAttack.getStandWindupTicks: getHeavyAttackWindup(" + speed + ", 0.6) = "
                            + windup + " windup ticks at a meter of 0.6; the port's windup/perform/recovery are "
                            + lengths);
            helper.assertTrue(chop.phasesLength.getFloat(ActionPhase.PERFORM) == 1.0F
                            && chop.phasesLength.getFloat(ActionPhase.RECOVERY) == recovery,
                    "1.16 aa.java:64-72: perform round(1 x 0.8) = 1 and recovery round(getHeavyAttackRecovery(" + speed
                            + ", 0.6) x 0.8) = " + recovery + "; the port's windup/perform/recovery are " + lengths);
            helper.assertTrue(chop.getPhase() == ActionPhase.WINDUP
                            && !chop.canBeCancelledInto((EntityActionType) power.getAbility("d4c_rapid_punch")),
                    "setup: the hand chop is not in a windup that holds other actions back: " + chop.getPhase());
            int windupTicks = 0;
            while (windupTicks < 40 && chop.getPhase() == ActionPhase.WINDUP) {
                LivingComponentAction.getComponent(stand).tick();
                windupTicks++;
            }
            helper.assertTrue(windupTicks == windup,
                    "1.16 ran the hand chop's windup for " + windup + " ticks; the port ran " + windupTicks);
            List<String> expectedSwings = List.of("WINDUP:" + (windup - 3));
            helper.assertTrue(swings.equals(expectedSwings),
                    "1.16 StandEntityHeavyAttack.standTickWindup played STAND_PUNCH_HEAVY_SWING at the Stand, volume "
                            + "1, pitch 0.65 to 0.85, with 3 windup ticks left: " + expectedSwings + "; the port played "
                            + swings);
            for (int tick = 0; tick < 5 && chop.getPhase() != ActionPhase.RECOVERY; tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            helper.assertTrue(chop.getPhase() == ActionPhase.RECOVERY, "setup: the hand chop did not strike");
            helper.assertTrue(Math.abs(stamina - power.getStamina() - 50.0F) < 1.0E-3F,
                    "1.16 StandEntityHeavyAttack.Builder: the hand chop's perform cost 50 stamina; the port took "
                            + (stamina - power.getStamina()));
            EntityActionType rapidType = (EntityActionType) power.getAbility("d4c_rapid_punch");
            helper.assertTrue(chop.canBeCancelledInto(rapidType)
                            && !rapidType.shouldBufferInput(LivingComponentAction.getComponent(stand)),
                    "1.16 StandEntityHeavyAttack.isFreeRecovery let another action replace the hand chop, a "
                            + "finisher, in its recovery; the port holds the press back");
            int recoveryTicks = 0;
            while (recoveryTicks < 40 && stand.getCurStandAction() == chop && !chop.isOver()) {
                LivingComponentAction.getComponent(stand).tick();
                recoveryTicks++;
            }
            helper.assertTrue(recoveryTicks == recovery,
                    "1.16 ran the hand chop's recovery for " + recovery + " ticks; the port ran " + recoveryTicks);
            running[0] = null;
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            helper.succeed();
        }
        finally {
            NeoForge.EVENT_BUS.unregister(listener);
            running[0] = null;
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 aa/aa/aa.java:100-111: a hand chop that hurt a mob sent 40 blood particles one by one (sendParticles with a
     * count of 1), each at its own random spot within 1.25 sideways and 0 to 2 up of the mob's feet, with a spread of its
     * own (0.15 sideways, 0 to 0.2 up) and a speed of 0.15.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_hand_chop_blood")
    public static void handChopSpillsTheDonorsBlood(GameTestHelper helper) {
        Player user = testUser(helper, "d4c-hand-chop-blood");
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        Cow cow = sturdyCow(helper);
        D4CRecordingServerPlayer watcher = null;
        try {
            power = grantAndSummon(helper, user);
            StandEntity stand = power.getSummonedStandEntity();
            place(helper, cow, user.position().add(1.5D, 0.0D, 0.0D));
            watcher = new D4CRecordingServerPlayer(helper, "d4c-blood-watcher", user.position().add(0.0D, 0.0D, 3.0D));
            turn(user, -90.0F);
            aim(user, stand, new ActionTarget(cow));
            EntityActionInstance chop = start(helper, user, power, stand, "d4c_hand_chop");
            stand.updatePosition(user);
            watcher.clear();
            for (int tick = 0; tick < 20 && chop.getPhase() != ActionPhase.RECOVERY; tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            helper.assertTrue(hurt(cow), "setup: the hand chop did not hurt the cow");
            Vec3 feet = cow.position();
            List<ClientboundLevelParticlesPacket> blood = watcher.particles().stream()
                    .filter(packet -> packet.getParticle().getType() == ModParticles.BLOOD.get())
                    .toList();
            List<String> off = new ArrayList<>();
            for (ClientboundLevelParticlesPacket drop : blood) {
                double dx = drop.getX() - feet.x;
                double dy = drop.getY() - feet.y;
                double dz = drop.getZ() - feet.z;
                if (drop.getCount() != 1 || Math.abs(drop.getMaxSpeed() - 0.15F) > 1.0E-6F
                        || Math.abs(dx) > 1.25D || dy < 0.0D || dy > 2.0D || Math.abs(dz) > 1.25D
                        || Math.abs(drop.getXDist()) > 0.15F || drop.getYDist() < 0.0F || drop.getYDist() > 0.2F
                        || Math.abs(drop.getZDist()) > 0.15F) {
                    off.add(drop.getCount() + "@(" + dx + ", " + dy + ", " + dz + ") spread (" + drop.getXDist()
                            + ", " + drop.getYDist() + ", " + drop.getZDist() + ") speed " + drop.getMaxSpeed());
                }
            }
            long spots = blood.stream().map(drop -> drop.getX() + "/" + drop.getY() + "/" + drop.getZ())
                    .distinct().count();
            helper.assertTrue(blood.size() == D4CPhase2BSpec.HAND_CHOP_BLOOD_PARTICLES && off.isEmpty()
                            && spots == blood.size(),
                    "1.16 aa.java:100-111 sent 40 single blood particles, each at its own spot within 1.25 / 0 to 2 / "
                            + "1.25 of the cow's feet, spread 0.15 / 0 to 0.2 / 0.15, speed 0.15; the port sent "
                            + blood.size() + " packets at " + spots + " spots, out of the donor's ranges: " + off);
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            helper.succeed();
        }
        finally {
            if (watcher != null) {
                watcher.close();
            }
            cow.discard();
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 StandEntity.punch made a hit's target the task's through StandEntity.setTaskTarget, the public call any server
     * code could make on any Stand: StandEntity.setTaskTarget(new ActionTarget(null)) dropped what a rapid punch or a hand
     * chop had struck, and the next hit aimed again.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_struck_task_target")
    public static void theStruckTargetIsTheTasksOwnAsIn116(GameTestHelper helper) {
        Player user = testUser(helper, "d4c-struck-task-target");
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        Cow kept = sturdyCow(helper);
        Cow looked = sturdyCow(helper);
        try {
            power = grantAndSummon(helper, user);
            StandEntity stand = power.getSummonedStandEntity();
            place(helper, kept, user.position().add(2.0D, 0.0D, 0.0D));
            turn(user, -90.0F);
            aim(user, stand, new ActionTarget(kept));
            EntityActionInstance rapid = start(helper, user, power, stand);
            stand.updatePosition(user);
            for (int tick = 0; tick < 20 && !hurt(kept); tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            helper.assertTrue(hurt(kept) && rapid.getTaskTarget(helper.getLevel()).getEntity() == kept,
                    "setup: the rapid punch did not strike and keep the cow: " + rapid.getTaskTarget(helper.getLevel()));
            stand.setTaskTarget(null);
            helper.assertTrue(rapid.getTaskTarget(helper.getLevel()).getType() == ActionTarget.TargetType.EMPTY,
                    "1.16 StandEntity.setTaskTarget dropped the cow a rapid punch hit had made its task's target; the "
                            + "task holds " + rapid.getTaskTarget(helper.getLevel()));
            place(helper, looked, user.position().add(0.0D, 0.0D, 2.0D));
            turn(user, 0.0F);
            aim(user, stand, new ActionTarget(looked));
            for (int tick = 0; tick < 4 && !hurt(looked); tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            helper.assertTrue(hurt(looked) && rapid.getTaskTarget(helper.getLevel()).getEntity() == looked,
                    "1.16 aimed the next rapid punch hit again once the task's target was dropped, at the cow the user "
                            + "looked at; the task holds " + rapid.getTaskTarget(helper.getLevel()));
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);

            stand.setPos(user.position());
            looked.discard();
            turn(user, -90.0F);
            aim(user, stand, new ActionTarget(kept));
            EntityActionInstance chop = start(helper, user, power, stand, "d4c_hand_chop");
            stand.updatePosition(user);
            for (int tick = 0; tick < 20 && chop.getPhase() != ActionPhase.RECOVERY; tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            helper.assertTrue(chop.getPhase() == ActionPhase.RECOVERY
                            && chop.getTaskTarget(helper.getLevel()).getEntity() == kept,
                    "setup: the hand chop did not strike and keep the cow: " + chop.getTaskTarget(helper.getLevel()));
            stand.setTaskTarget(null);
            helper.assertTrue(chop.getTaskTarget(helper.getLevel()).getType() == ActionTarget.TargetType.EMPTY,
                    "1.16 StandEntity.setTaskTarget dropped the cow the hand chop had struck; the task holds "
                            + chop.getTaskTarget(helper.getLevel()));
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            helper.succeed();
        }
        finally {
            kept.discard();
            looked.discard();
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 PowerBaseImpl.checkTarget kept a pressed target only if standKeepsTarget took it and checkStandTarget
     * allowed it: the rapid punch (StandEntityLightAttack, ac.java:177) a mob D4C may attack or a block, the hand chop
     * (StandEntityHeavyAttack) only the mob.
     */
    @GameTest(template = "empty", timeoutTicks = 40, batch = "d4c_pressed_target")
    public static void pressedTargetsFollowTheDonorsTargetChecks(GameTestHelper helper) {
        Player user = testUser(helper, "d4c-pressed-target");
        StandPower power = null;
        Cow cow = sturdyCow(helper);
        BlockPos block = helper.absolutePos(new BlockPos(4, 13, 2));
        try {
            power = grantAndSummon(helper, user);
            StandEntity stand = power.getSummonedStandEntity();
            place(helper, cow, user.position().add(2.0D, 0.0D, 0.0D));
            helper.assertTrue(stand.canAttack(cow), "setup: D4C may not attack the cow");
            helper.getLevel().setBlockAndUpdate(block, Blocks.DIRT.defaultBlockState());
            ActionTarget atCow = new ActionTarget(cow);
            ActionTarget atBlock = new ActionTarget(block, Direction.WEST);
            Object[][] rows = { { "d4c_rapid_punch", true, true }, { "d4c_hand_chop", true, false } };
            for (Object[] row : rows) {
                String name = (String) row[0];
                helper.assertTrue(power.getAbility(name) instanceof StandEntityAbility, "D4C has no " + name);
                StandEntityAbility ability = (StandEntityAbility) power.getAbility(name);
                boolean cowTaken = ability.takesPressedTarget(stand, atCow);
                boolean blockTaken = ability.takesPressedTarget(stand, atBlock);
                helper.assertTrue(cowTaken == (Boolean) row[1] && blockTaken == (Boolean) row[2],
                        "1.16's target checks for " + name + ": cow " + row[1] + ", block " + row[2]
                                + "; the port took cow " + cowTaken + ", block " + blockTaken);
            }
            helper.succeed();
        }
        finally {
            helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
            cow.discard();
            if (power != null && power.isSummoned()) {
                D4CAddon.D4C.get().forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 StandEntity.punch aimed each rapid punch hit (ac.java:128, :133) and the hand chop
     * (StandEntityHeavyAttack.standPerform) with aimWithThisOrUser, which turned the Stand toward what it aimed at in
     * that same tick, before the hit (MCUtil.rotateTowards, at most attackSpeed / 16 x 18 degrees). The rapid punch's
     * perform tick 0 punched twice (StandEntityLightAttack.standPerform, then standTickPerform), so it turned twice.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_strike_turn")
    public static void rapidPunchAndHandChopTurnTheStandInTheirHitTickAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        for (String name : new String[] {"d4c_rapid_punch", "d4c_hand_chop"}) {
            Player user = testUser(helper, "d4c-strike-turn-" + name.length());
            StandPower power = null;
            Cow cow = sturdyCow(helper);
            try {
                power = grantAndSummon(helper, user);
                StandEntity stand = power.getSummonedStandEntity();
                place(helper, cow, user.position().add(1.5D, 0.0D, 0.0D));
                turn(user, -90.0F);
                aim(user, stand, new ActionTarget(cow));
                EntityActionInstance action = start(helper, user, power, stand, name);
                stand.updatePosition(user);
                float max = (float) stand.getAttackSpeed() / 16.0F * 18.0F;
                float before = 0.0F;
                for (int tick = 0; tick < 20 && !hurt(cow); tick++) {
                    // facing well away from the cow as each tick starts: only a turn in the tick itself shows
                    before = MathUtil.yRotDegFromVec(cow.getEyePosition().subtract(stand.getEyePosition())) + 90.0F;
                    stand.setYRot(before);
                    stand.setXRot(0.0F);
                    LivingComponentAction.getComponent(stand).tick();
                }
                if (!hurt(cow)) {
                    wrong.add(name + ": setup, no hit on the cow (phase " + action.getPhase() + ")");
                    continue;
                }
                // the rapid punch's first tick strikes twice: standPerform's punch and standTickPerform's first hit
                int strikes = name.equals("d4c_rapid_punch") ? 2 : 1;
                float expected = before - strikes * max;
                if (Math.abs(Mth.degreesDifference(stand.getYRot(), expected)) > 0.01F) {
                    wrong.add(name + ": yaw " + stand.getYRot() + " after the hit tick from " + before
                            + ", 1.16 turned it " + strikes + " x " + max + " toward the cow, to " + expected);
                }
                LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            }
            finally {
                cow.discard();
                if (power != null && power.isSummoned()) {
                    D4CAddon.D4C.get().forceUnsummon(user, power);
                }
                user.discard();
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 aimWithThisOrUser turned D4C in the hit's own tick: " + wrong);
        helper.succeed();
    }

    private static Player testUser(GameTestHelper helper, String name) {
        return new Player(helper.getLevel(), BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), name)) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
    }

    private static StandPower grantAndSummon(GameTestHelper helper, Player user) {
        StandType type = D4CAddon.D4C.get();
        user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
        Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
        user.setNoGravity(true);
        user.moveTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the D4C user");
        StandPower power = PowerClass.STAND.attachGet(user);
        helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
                        == StandPowerTransitions.Status.APPLIED,
                "Could not grant D4C");
        power.skipProgression();
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(type.summon(user, power), "Could not summon D4C");
        StandEntity stand = power.getSummonedStandEntity();
        helper.assertTrue(stand != null && !stand.isArmsOnlyMode(), "setup: D4C is not a full Stand");
        return power;
    }

    private static boolean hurt(Cow cow) {
        return cow.getLastDamageSource() != null;
    }

    private static Cow sturdyCow(GameTestHelper helper) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "setup: no cow");
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
        cow.setHealth(500.0F);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        return cow;
    }

    private static void place(GameTestHelper helper, Cow cow, Vec3 pos) {
        cow.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        if (!cow.isAddedToLevel()) {
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "setup: the cow was not added");
        }
    }

    private static void aim(Player user, StandEntity stand, ActionTarget target) {
        LivingComponentAction.getComponent(user).entityAim.setTarget(target);
        LivingComponentAction.getComponent(stand).entityAim.setTarget(target);
    }

    private static EntityActionInstance start(GameTestHelper helper, Player user, StandPower power,
            StandEntity stand) {
        return start(helper, user, power, stand, "d4c_rapid_punch");
    }

    private static EntityActionInstance start(GameTestHelper helper, Player user, StandPower power,
            StandEntity stand, String name) {
        helper.assertTrue(power.getAbility(name) instanceof EntityActionType, "D4C has no " + name);
        EntityActionType actionType = (EntityActionType) power.getAbility(name);
        EntityActionInstance action = actionType.createActionObj();
        actionType.initActionFromConfig(action, helper.getLevel(), user, stand);
        action.setStartingPhase();
        LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
        helper.assertTrue(stand.getCurStandAction() == action, "setup: " + name + " did not become D4C's action");
        return action;
    }

    // 1.16 checkStandTarget refused a pressed entity that is not living, so the strikes took the user's view: the user
    // looks at the minecart, as a player who clicked it did
    private static void lookAt(Player user, Vec3 point) {
        Vec3 to = point.subtract(user.getEyePosition());
        turn(user, (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F);
        float pitch = (float) -(Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
        user.setXRot(pitch);
        user.xRotO = pitch;
    }

    private static void turn(Player user, float viewYaw) {
        user.setYRot(viewYaw);
        user.yRotO = viewYaw;
        user.setYHeadRot(viewYaw);
        user.yHeadRotO = viewYaw;
        user.setXRot(0.0F);
        user.xRotO = 0.0F;
        user.yBodyRot = viewYaw;
        user.yBodyRotO = viewYaw;
    }
}
