package rotp.addons.ultimate_lifeform;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformCombat;
import rotp.addons.ultimate_lifeform.effect.UltimateHamonSpreadEffect;
import rotp.addons.ultimate_lifeform.entity.UltimateAreaEffectEntity;
import rotp.addons.ultimate_lifeform.network.UltimateSparkBurstPayload;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;
import rotp.core.impl.powers.hamon.entity.LeavesGliderEntity;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 rules of the Ultimate Lifeform's Hamon moves restored in wave 28 (lane F2): the Ultimate Hamon hit and burn,
 * the targets of the strikes, the swings, sounds and sparks, and the Hamon charge's save and sync.
 */
@GameTestHolder(UltimateLifeformAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimateHamonRulesGameTests {
    private UltimateHamonRulesGameTests() {}

    /*
     * 1.16 KarsUtil.dealUltimateHamonDamage gave every undead target the Hamon spread with no skill needed;
     * CustomDamageSources.stackUltimateHamonBurn burned an undead target 12000 ticks and any other 200, one level up
     * on each new burn; the burn's own hits were plain hurts under the hurt cooldown, without a knockback.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hamonHitSpreadsIntoUndeadAndTheBurnStacksAs116(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHamonRulesBurn", "b2d1e3f4-0b1d-4c2e-8f3a-5b6c7d8e9f01");
        holder.data.addHamonCharge(3_000);
        Zombie zombie = mob(helper, EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 4.5D));
        holder.aim(zombie);
        UltimateLifeformCombat.perform(holder.player, holder.data, UltimateLifeformSpec.HAMON_OVERDRIVE, 0);
        helper.assertTrue(zombie.hasEffect(ModStatusEffects.HAMON_SPREAD),
                "1.16 KarsUtil spread the Hamon into an undead target without any Hamon skill;"
                        + " the zombie hit by the Hamon Overdrive got no Hamon spread");
        MobEffectInstance undeadBurn = zombie.getEffect(UltimateLifeformAddon.HAMON_SPREAD);
        helper.assertTrue(undeadBurn != null && undeadBurn.getDuration() > 11_000,
                "1.16 burned an undead target 12000 ticks; got " + undeadBurn);
        zombie.discard();
        Cow cow = mob(helper, EntityType.COW, new Vec3(2.5D, 2.0D, 4.5D));
        holder.aim(cow);
        UltimateLifeformCombat.perform(holder.player, holder.data, UltimateLifeformSpec.HAMON_OVERDRIVE, 0);
        MobEffectInstance first = cow.getEffect(UltimateLifeformAddon.HAMON_SPREAD);
        helper.assertTrue(first != null && first.getDuration() == 200 && first.getAmplifier() == 0,
                "1.16 burned a living target 200 ticks at level I; got " + first);
        cow.invulnerableTime = 0;
        UltimateLifeformCombat.perform(holder.player, holder.data, UltimateLifeformSpec.HAMON_OVERDRIVE, 0);
        MobEffectInstance second = cow.getEffect(UltimateLifeformAddon.HAMON_SPREAD);
        helper.assertTrue(second != null && second.getAmplifier() == 1,
                "1.16 stacked a second burn one level up; got " + second);
        cow.discard();
        Cow burning = mob(helper, EntityType.COW, new Vec3(5.5D, 2.0D, 5.5D));
        burning.invulnerableTime = 0;
        float before = burning.getHealth();
        for (int tick = 0; tick < 5; tick++) {
            UltimateLifeformAddon.HAMON_SPREAD.get().applyEffectTick(burning, 0);
        }
        float lost = before - burning.getHealth();
        helper.assertTrue(Math.abs(lost - 2.0F) < 1.0E-4F,
                "1.16 burn hits respected the hurt cooldown: 5 burn ticks in a row dealt one hit of 2; lost " + lost);
        Vec3 push = burning.getDeltaMovement();
        helper.assertTrue(push.x * push.x + push.z * push.z < 1.0E-6D,
                "1.16 burn hits named no entity and pushed nothing; the cow moves " + push);
        burning.discard();
        holder.close();
        helper.succeed();
    }

    /*
     * 1.16 UltimateHamonSendoOverdriveEntity: each wave hurt with the raw damage through the hurt cooldown and burned
     * whether or not the hit landed; the spark sound came with each added wave but the last.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "ulf_own_sendo_waves_hit_raw_burn_always_and_ring_but")
    public static void sendoWavesHitRawBurnAlwaysAndRingButForTheLastWave(GameTestHelper helper) {
        Cow owner = mob(helper, EntityType.COW, new Vec3(1.5D, 2.0D, 1.5D));
        BlockPos block = helper.absolutePos(new BlockPos(4, 1, 4));
        UltimateAreaEffectEntity sendo = UltimateLifeformAddon.ULTIMATE_SENDO_HAMON_OVERDRIVE.get()
                .create(helper.getLevel());
        helper.assertTrue(sendo != null, "fixture: no Sendo entity");
        sendo.initializeSendo(owner, block, Direction.UP, 1.125F, 9.0F, 8);
        helper.getLevel().addFreshEntity(sendo);
        Cow target = mob(helper, EntityType.COW, new Vec3(4.5D, 2.0D, 5.0D));
        Cow shielded = mob(helper, EntityType.COW, new Vec3(5.5D, 2.0D, 4.0D));
        shielded.setInvulnerable(true);
        target.invulnerableTime = 20;
        float before = target.getHealth();
        try (Sounds sounds = new Sounds(helper)) {
            for (int tick = 0; tick < 4; tick++) {
                sendo.tick();
            }
            helper.assertTrue(Math.abs(before - target.getHealth() - 1.125F) < 1.0E-4F,
                    "1.16 waves hurt with the raw 1.125 through the hurt cooldown; lost "
                            + (before - target.getHealth()));
            helper.assertTrue(shielded.hasEffect(UltimateLifeformAddon.HAMON_SPREAD),
                    "1.16 waves burned a target whose hit did not land");
            for (int tick = 4; tick < 36; tick++) {
                sendo.tick();
            }
            long rings = sounds.heard.stream()
                    .filter(h -> h.sound.equals(ModSoundEvents.HAMON_SPARK.get().getLocation()))
                    .count();
            helper.assertTrue(rings == 7,
                    "1.16 rang the spark sound for each of the 8 waves but the last; rang " + rings);
        }
        sendo.discard();
        target.discard();
        shielded.discard();
        owner.discard();
        helper.succeed();
    }

    /*
     * 1.16 UltimateLifeformHamonOverdrive: ENTITY target requirement (silent without one, target_too_far beyond
     * reach), the perform at that entity only, and swingHand(): the main arm swung.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hamonOverdriveTakesTheCrosshairEntityAndSwings(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHamonRulesOverdrive", "b2d1e3f4-0b1d-4c2e-8f3a-5b6c7d8e9f02");
        holder.data.addHamonCharge(3_000);
        Ability ability = holder.power.getAbility(UltimateLifeformSpec.HAMON_OVERDRIVE.id);
        holder.aimNothing();
        ConditionCheck none = ability.checkConditions(holder.power);
        helper.assertTrue(!none.isPositive() && none.getWarning() == null,
                "1.16 refused a Hamon Overdrive with no entity under the crosshair without a message; got "
                        + (none.isPositive() ? "admitted" : String.valueOf(none.getWarning())));
        helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(2, 3, 5)),
                Blocks.STONE.defaultBlockState());
        Cow hidden = mob(helper, EntityType.COW, new Vec3(2.5D, 2.0D, 7.5D));
        holder.aim(hidden);
        ConditionCheck far = ability.checkConditions(holder.power);
        helper.assertTrue(!far.isPositive() && isWarning(far, "target_too_far"),
                "1.16 said target_too_far for an entity past reach without a line of sight; got "
                        + (far.isPositive() ? "admitted" : String.valueOf(far.getWarning())));
        hidden.discard();
        Cow cow = mob(helper, EntityType.COW, new Vec3(2.5D, 2.0D, 4.0D));
        holder.lookAt(cow);
        holder.aimNothing();
        UltimateLifeformCombat.perform(holder.player, holder.data, UltimateLifeformSpec.HAMON_OVERDRIVE, 0);
        helper.assertTrue(cow.getHealth() == cow.getMaxHealth(),
                "1.16 struck only the entity its press took; a mob in front with nothing under the crosshair took "
                        + (cow.getMaxHealth() - cow.getHealth()));
        holder.aim(cow);
        helper.assertTrue(ability.checkConditions(holder.power).isPositive(),
                "Premise: the Hamon Overdrive at a cow in reach was refused");
        holder.player.swinging = false;
        UltimateLifeformCombat.perform(holder.player, holder.data, UltimateLifeformSpec.HAMON_OVERDRIVE, 0);
        helper.assertTrue(holder.player.swinging && cow.getHealth() < cow.getMaxHealth(),
                "1.16 swung the main arm (swingHand) and struck the crosshair cow; swinging="
                        + holder.player.swinging + " health=" + cow.getHealth());
        cow.discard();
        holder.close();
        helper.succeed();
    }

    /*
     * 1.16 UltimateLifeformHamonBeat.Instance tick 5: the mouse target of that tick; punch re-checked the hands and
     * dealt the Hamon hit (and its burn) only with both free.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void beatTakesTheTickFiveCrosshairAndRechecksTheHands(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHamonRulesBeat", "b2d1e3f4-0b1d-4c2e-8f3a-5b6c7d8e9f03");
        Cow cow = mob(helper, EntityType.COW, new Vec3(2.5D, 2.0D, 6.0D));
        holder.lookAt(cow);
        holder.aimNothing();
        UltimateLifeformCombat.tickHeld(holder.player, holder.data, UltimateLifeformSpec.HAMON_OVERDRIVE_BEAT, 5);
        helper.assertTrue(cow.getHealth() == cow.getMaxHealth(),
                "1.16 punched only the tick 5 mouse target; a mob 3.5 blocks in front with nothing under the"
                        + " crosshair took " + (cow.getMaxHealth() - cow.getHealth()));
        holder.aim(cow);
        holder.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE));
        UltimateLifeformCombat.tickHeld(holder.player, holder.data, UltimateLifeformSpec.HAMON_OVERDRIVE_BEAT, 5);
        helper.assertTrue(!cow.hasEffect(UltimateLifeformAddon.HAMON_SPREAD),
                "1.16 dealt no Hamon hit (no burn) once a hand was filled at the punch");
        holder.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        cow.invulnerableTime = 0;
        UltimateLifeformCombat.tickHeld(holder.player, holder.data, UltimateLifeformSpec.HAMON_OVERDRIVE_BEAT, 5);
        helper.assertTrue(cow.hasEffect(UltimateLifeformAddon.HAMON_SPREAD),
                "Premise: the Beat with free hands did not burn the crosshair cow");
        cow.discard();
        holder.close();
        helper.succeed();
    }

    /*
     * 1.16 UltimateLifeformHamonUltimateOverdrive.Instance: ticks 1 and 3 PILLAR_MAN_SWING (1.0, 1.25) and a main hand
     * swing; tick 5 the first entity on the 5-block look ray (not the aim), punchPerform (PILLAR_MAN_PUNCH 1.2/0.8,
     * knockback, impact) and no HAMON_SYO_PUNCH (its client call passed a null player: silent); a block and the cube
     * around it broken by the dig rule.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ultimateOverdriveStrikesTheRayLikeAPunchAndBreaksTheCube(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHamonRulesStrike", "b2d1e3f4-0b1d-4c2e-8f3a-5b6c7d8e9f04");
        ServerPlayer player = holder.player;
        holder.data.addHamonCharge(3_000);
        try (Sounds sounds = new Sounds(helper)) {
            player.swinging = false;
            UltimateLifeformCombat.tickHeld(player, holder.data, UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE, 1);
            helper.assertTrue(player.swinging
                            && sounds.atEntity(ModSoundEvents.PILLAR_MAN_SWING.get(), player, 1.0F, 1.25F),
                    "1.16 swung and played PILLAR_MAN_SWING (1.0, 1.25) on the strike's tick 1; swinging="
                            + player.swinging + " heard " + sounds.heard);
            Cow aside = mob(helper, EntityType.COW, new Vec3(5.5D, 2.0D, 3.5D));
            Cow ahead = mob(helper, EntityType.COW, new Vec3(2.5D, 2.0D, 5.5D));
            ahead.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0D);
            holder.lookAt(ahead);
            holder.aimAt(aside);
            UltimateLifeformCombat.tickHeld(player, holder.data, UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE, 5);
            helper.assertTrue(aside.getHealth() == aside.getMaxHealth(),
                    "1.16 struck the first entity on the look ray, not the aimed one aside; it took "
                            + (aside.getMaxHealth() - aside.getHealth()));
            KnockbackCollisionImpact impact = KnockbackCollisionImpact.getExistingHandler(ahead);
            helper.assertTrue(ahead.getMaxHealth() - ahead.getHealth() >= 100.0F
                            && impact != null && impact.isActive(),
                    "1.16 struck the mob on the ray for 100 and armed its knockback impact (punchPerform); lost "
                            + (ahead.getMaxHealth() - ahead.getHealth()) + " impact="
                            + (impact != null && impact.isActive()));
            helper.assertTrue(sounds.atEntity(ModSoundEvents.PILLAR_MAN_PUNCH.get(), ahead, 1.2F, 0.8F),
                    "1.16 played PILLAR_MAN_PUNCH (1.2, 0.8) at the struck mob; heard " + sounds.heard);
            helper.assertTrue(!sounds.anyOf(ModSoundEvents.HAMON_SYO_PUNCH.get()),
                    "1.16 played no HAMON_SYO_PUNCH at the struck mob (its client call passed a null player); heard "
                            + sounds.heard);
            aside.discard();
            ahead.discard();
        }
        BlockPos center = helper.absolutePos(new BlockPos(2, 3, 5));
        BlockPos above = center.above();
        BlockPos hard = center.east();
        helper.getLevel().setBlockAndUpdate(center, Blocks.DIRT.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(above, Blocks.DIRT.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(hard, Blocks.OBSIDIAN.defaultBlockState());
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(center));
        UltimateLifeformCombat.tickHeld(player, holder.data, UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE, 5);
        helper.assertTrue(helper.getLevel().getBlockState(center).isAir()
                        && helper.getLevel().getBlockState(above).isAir()
                        && helper.getLevel().getBlockState(hard).is(Blocks.OBSIDIAN),
                "1.16 broke the block on the ray and the soft blocks of the cube around it, not obsidian; center="
                        + helper.getLevel().getBlockState(center) + " above="
                        + helper.getLevel().getBlockState(above));
        holder.close();
        helper.succeed();
    }

    /*
     * 1.16 UltimateLifeformHamonUltimateOverdrive.Instance tick 5 drew its 32 rainbow sparks on the client with
     * CustomParticles.createUltimateHamonSparkParticles(struckEntity, getX(1.0), getY(random), getZ(1.0), 32): particles
     * that moved with the struck entity from that one point, on the user's client and on each client tracking the user.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ultimateOverdriveSparksFollowTheStruckMobOnTheUserAndItsTrackers(GameTestHelper helper) {
        List<Packet<?>> userSent = new ArrayList<>();
        ServerPlayer user = recordingPlayer(helper, "UltimateHamonRulesSparks", "b2d1e3f4-0b1d-4c2e-8f3a-5b6c7d8e9f12",
                userSent);
        PlayerPower power = PlayerPower.get(user);
        UltimateLifeformData data = power.getCurTypeData(UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        data.addHamonCharge(3_000);
        SoundWatcher watcher = new SoundWatcher(helper.getLevel(), "UltimateSparksWatcher");
        SoundWatcher stranger = new SoundWatcher(helper.getLevel(), "UltimateSparksStranger");
        ChunkMap.TrackedEntity tracked = helper.getLevel().getChunkSource().chunkMap.entityMap.get(user.getId());
        helper.assertTrue(tracked != null, "Premise: the user is not tracked");
        tracked.seenBy.add(watcher.connection);
        Cow ahead = mob(helper, EntityType.COW, new Vec3(2.5D, 2.0D, 5.5D));
        try {
            user.lookAt(EntityAnchorArgument.Anchor.EYES, ahead.getBoundingBox().getCenter());
            userSent.clear();
            UltimateLifeformCombat.tickHeld(user, data, UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE, 5);
            helper.assertTrue(ahead.getMaxHealth() - ahead.getHealth() >= 100.0F,
                    "Premise: the strike did not land on the cow ahead");
            List<UltimateSparkBurstPayload> toUser = sparkBursts(userSent);
            List<UltimateSparkBurstPayload> toWatcher = sparkBursts(watcher.sent);
            helper.assertTrue(toUser.size() == 1 && toWatcher.size() == 1 && sparkBursts(stranger.sent).isEmpty(),
                    "1.16 drew the strike's sparks on the user's client and on each client tracking the user, on no"
                            + " other; the user got " + toUser.size() + " bursts, a tracker " + toWatcher.size()
                            + ", a player not tracking the user " + sparkBursts(stranger.sent).size());
            for (UltimateSparkBurstPayload burst : List.of(toUser.get(0), toWatcher.get(0))) {
                helper.assertTrue(burst.entityId() == ahead.getId() && burst.count() == 32
                                && Math.abs(burst.x() - ahead.getX(1.0D)) < 1.0E-6D
                                && Math.abs(burst.z() - ahead.getZ(1.0D)) < 1.0E-6D
                                && burst.y() >= ahead.getY() && burst.y() <= ahead.getY(1.0D),
                        "1.16 made the 32 sparks follow the struck mob from (getX(1.0), getY(random), getZ(1.0)); got "
                                + burst + " for the mob " + ahead.getId() + " at " + ahead.position());
            }
        }
        finally {
            tracked.seenBy.remove(watcher.connection);
            ahead.discard();
            power.setPowerType(null);
            user.discard();
        }
        helper.succeed();
    }

    /*
     * 1.16 UltimateLifeformHamonBarrage.holdTick drew 8 sparks on the user's client for each entity hit, following the
     * hit entity (createUltimateHamonSparkParticles(entity, ...)): no other client saw them.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hamonBarrageSparksFollowTheHitMobOnTheUsersClientOnly(GameTestHelper helper) {
        List<Packet<?>> userSent = new ArrayList<>();
        ServerPlayer user = recordingPlayer(helper, "UltimateHamonRulesBarrageSparks",
                "b2d1e3f4-0b1d-4c2e-8f3a-5b6c7d8e9f13", userSent);
        PlayerPower power = PlayerPower.get(user);
        UltimateLifeformData data = power.getCurTypeData(UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        data.addHamonCharge(3_000);
        SoundWatcher watcher = new SoundWatcher(helper.getLevel(), "UltimateBarrageSparksWatcher");
        ChunkMap.TrackedEntity tracked = helper.getLevel().getChunkSource().chunkMap.entityMap.get(user.getId());
        helper.assertTrue(tracked != null, "Premise: the user is not tracked");
        tracked.seenBy.add(watcher.connection);
        Cow cow = mob(helper, EntityType.COW, new Vec3(2.5D, 2.0D, 4.5D));
        try {
            user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
            LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(cow));
            user.tickCount = 2;
            userSent.clear();
            UltimateLifeformCombat.tickHeld(user, data, UltimateLifeformSpec.ULTIMATE_HAMON_BARRAGE, 2);
            List<UltimateSparkBurstPayload> toUser = sparkBursts(userSent);
            helper.assertTrue(toUser.size() == 1 && toUser.get(0).entityId() == cow.getId()
                            && toUser.get(0).count() == 8 && sparkBursts(watcher.sent).isEmpty(),
                    "1.16 drew the barrage's 8 sparks on the user's client alone, following the hit mob; the user got "
                            + toUser + ", a tracker " + sparkBursts(watcher.sent));
        }
        finally {
            tracked.seenBy.remove(watcher.connection);
            cow.discard();
            power.setPowerType(null);
            user.discard();
        }
        helper.succeed();
    }

    private static List<UltimateSparkBurstPayload> sparkBursts(List<Packet<?>> sent) {
        List<Packet<?>> parts = new ArrayList<>();
        for (Packet<?> packet : sent) {
            if (packet instanceof ClientboundBundlePacket bundle) {
                bundle.subPackets().forEach(parts::add);
            }
            else {
                parts.add(packet);
            }
        }
        List<UltimateSparkBurstPayload> found = new ArrayList<>();
        for (Packet<?> part : parts) {
            if (part instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof UltimateSparkBurstPayload burst) {
                found.add(burst);
            }
        }
        return found;
    }

    /*
     * 1.16 UltimateLifeformHamonUltimateOverdrive.Instance tick 1 (:151-153): every client that ran the strike instance,
     * the user's and those tracking the user (PlayerUtilCap.setContinuousAction sent TrPlayerContinuousActionPacket to
     * the trackers and the user), played KARS_OVERDRIVE on its own player (ClientTickingSoundsHelper.playEntitySound(
     * ClientUtil.getClientPlayer(), ...)): each heard it at full volume wherever it stood, a player not tracking the user
     * did not, and no sound was played into the level at the user.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ultimateOverdriveStrikeSoundPlaysOnEachTrackingPlayerAsIn116(GameTestHelper helper) {
        List<Packet<?>> userSent = new ArrayList<>();
        ServerPlayer user = recordingPlayer(helper, "UltimateHamonRulesKars", "b2d1e3f4-0b1d-4c2e-8f3a-5b6c7d8e9f11",
                userSent);
        PlayerPower power = PlayerPower.get(user);
        UltimateLifeformData data = power.getCurTypeData(UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        SoundWatcher watcher = new SoundWatcher(helper.getLevel(), "UltimateKarsWatcher");
        SoundWatcher stranger = new SoundWatcher(helper.getLevel(), "UltimateKarsStranger");
        // far beyond a level sound's 16 blocks
        watcher.setPos(user.position().add(0.0D, 0.0D, 48.0D));
        stranger.setPos(user.position().add(0.0D, 0.0D, 2.0D));
        ChunkMap.TrackedEntity tracked = helper.getLevel().getChunkSource().chunkMap.entityMap.get(user.getId());
        helper.assertTrue(tracked != null, "Premise: the user is not tracked");
        tracked.seenBy.add(watcher.connection);
        SoundEvent kars = UltimateLifeformAddon.SOUNDS.get("kars_hamon_overdrive").get();
        try (Sounds sounds = new Sounds(helper)) {
            userSent.clear();
            UltimateLifeformCombat.started(user, data, UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE);
            List<ClientboundSoundEntityPacket> toUser = karsSounds(userSent, kars);
            List<ClientboundSoundEntityPacket> toWatcher = karsSounds(watcher.sent, kars);
            helper.assertTrue(toUser.size() == 1 && boundTo(toUser.get(0), user)
                            && toWatcher.size() == 1 && boundTo(toWatcher.get(0), watcher)
                            && karsSounds(stranger.sent, kars).isEmpty(),
                    "1.16 played KARS_OVERDRIVE on the own player of the user's client and of each client tracking the"
                            + " user (volume 1, pitch 1, the player's sound source), and on no other client; the user got "
                            + describe(toUser) + ", a tracker 48 blocks away " + describe(toWatcher)
                            + ", a player not tracking the user " + describe(karsSounds(stranger.sent, kars)));
            helper.assertTrue(!sounds.anyOf(kars),
                    "1.16 played KARS_OVERDRIVE on each client's own player, not into the level at the user; heard "
                            + sounds.heard);
        }
        finally {
            tracked.seenBy.remove(watcher.connection);
            power.setPowerType(null);
            user.discard();
        }
        helper.succeed();
    }

    private static List<ClientboundSoundEntityPacket> karsSounds(List<Packet<?>> sent, SoundEvent kars) {
        List<Packet<?>> parts = new ArrayList<>();
        for (Packet<?> packet : sent) {
            if (packet instanceof ClientboundBundlePacket bundle) {
                bundle.subPackets().forEach(parts::add);
            }
            else {
                parts.add(packet);
            }
        }
        List<ClientboundSoundEntityPacket> found = new ArrayList<>();
        for (Packet<?> part : parts) {
            if (part instanceof ClientboundSoundEntityPacket sound && sound.getSound().value() == kars) {
                found.add(sound);
            }
        }
        return found;
    }

    private static boolean boundTo(ClientboundSoundEntityPacket sound, ServerPlayer listener) {
        return sound.getId() == listener.getId() && sound.getSource() == listener.getSoundSource()
                && sound.getVolume() == 1.0F && sound.getPitch() == 1.0F;
    }

    private static String describe(List<ClientboundSoundEntityPacket> sounds) {
        List<String> lines = new ArrayList<>();
        for (ClientboundSoundEntityPacket sound : sounds) {
            lines.add("entity " + sound.getId() + " " + sound.getSource().getName() + " " + sound.getVolume() + " "
                    + sound.getPitch());
        }
        return lines.toString();
    }

    // 1.16 the strike's ContinuousActionInstance walked at 0.5; 0.1 only while the key charged it
    @GameTest(template = "empty", timeoutTicks = 50)
    public static void ultimateOverdriveStrikeWalksAtHalfSpeed(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHamonRulesStrikeWalk", "b2d1e3f4-0b1d-4c2e-8f3a-5b6c7d8e9f05");
        holder.data.setCommandEnergy(holder.player, UltimateLifeformData.MAX_ENERGY);
        holder.data.addHamonCharge(3_000);
        short key = (short) 32_300;
        float[] charging = new float[1];
        helper.runAfterDelay(1, () -> holder.press(helper, UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE, key));
        helper.runAfterDelay(14, () -> {
            EntityActionInstance action = LivingComponentAction.getCurEntityAction(holder.player);
            charging[0] = action != null ? action.userWalkSpeed : -1.0F;
            AbilityInput.keyRelease(key, holder.player);
        });
        helper.runAfterDelay(17, () -> {
            EntityActionInstance action = LivingComponentAction.getCurEntityAction(holder.player);
            helper.assertTrue(action != null && action.getPhase() == ActionPhase.PERFORM,
                    "Premise: the released Ultimate Hamon Overdrive is not striking");
            helper.assertTrue(charging[0] == 0.1F && action.userWalkSpeed == 0.5F,
                    "1.16 walked at 0.1 while charging and 0.5 during the strike; got " + charging[0] + " / "
                            + action.userWalkSpeed);
            holder.close(key);
            helper.succeed();
        });
    }

    /*
     * 1.16 UltimateLifeformHamonShock.Instance tick 9: the crosshair target; a non-living one said living_mob_shock;
     * core Hamon sparks (with their sound) at the shocked target then and on every tick after tick 10.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hamonShockTakesTheCrosshairAndSparksAsTheCoreDid(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHamonRulesShock", "b2d1e3f4-0b1d-4c2e-8f3a-5b6c7d8e9f06");
        holder.data.addHamonCharge(3_000);
        ServerPlayer player = holder.player;
        Cow cow = mob(helper, EntityType.COW, new Vec3(2.5D, 2.0D, 4.5D));
        // a stun takes no mob without its AI (core StunEffect.isApplicable)
        cow.setNoAi(false);
        holder.lookAt(cow);
        holder.aimNothing();
        UltimateLifeformCombat.tickHeld(player, holder.data, UltimateLifeformSpec.HAMON_SHOCK, 9);
        helper.assertTrue(!cow.hasEffect(ModStatusEffects.HAMON_SHOCK) && holder.data.getHamonCharge() == 3_000
                        && holder.data.getShockedTarget() == null,
                "1.16 shocked only the tick 9 crosshair target; a mob in front with nothing under the crosshair"
                        + " was shocked (shock=" + cow.hasEffect(ModStatusEffects.HAMON_SHOCK) + ", Hamon "
                        + holder.data.getHamonCharge() + ")");
        Zombie zombie = mob(helper, EntityType.ZOMBIE, new Vec3(4.5D, 2.0D, 4.5D));
        holder.aim(zombie);
        UltimateLifeformCombat.tickHeld(player, holder.data, UltimateLifeformSpec.HAMON_SHOCK, 9);
        helper.assertTrue(holder.saw("living_mob_shock") && !zombie.hasEffect(ModStatusEffects.HAMON_SHOCK),
                "1.16 refused a non-living target with \"living_mob_shock\"; messages " + holder.messages);
        zombie.discard();
        holder.aim(cow);
        try (Sounds sounds = new Sounds(helper)) {
            Vec3 center = cow.getBoundingBox().getCenter();
            UltimateLifeformCombat.tickHeld(player, holder.data, UltimateLifeformSpec.HAMON_SHOCK, 9);
            helper.assertTrue(cow.hasEffect(ModStatusEffects.HAMON_SHOCK)
                            && sounds.atPosition(ModSoundEvents.HAMON_SPARK.get(), center),
                    "1.16 shocked the crosshair cow with core Hamon sparks and their sound; heard " + sounds.heard);
            sounds.heard.clear();
            UltimateLifeformCombat.tickHeld(player, holder.data, UltimateLifeformSpec.HAMON_SHOCK, 10);
            boolean tenth = sounds.atPosition(ModSoundEvents.HAMON_SPARK.get(), center);
            UltimateLifeformCombat.tickHeld(player, holder.data, UltimateLifeformSpec.HAMON_SHOCK, 11);
            helper.assertTrue(!tenth && sounds.atPosition(ModSoundEvents.HAMON_SPARK.get(), center),
                    "1.16 sparked at the shocked target on every tick after tick 10 (not on tick 10); heard "
                            + sounds.heard);
        }
        cow.discard();
        holder.data.releaseHeld(player);
        holder.close();
        helper.succeed();
    }

    /*
     * 1.16 UltimateLifeformHamonBarrage.holdTick: the crosshair target of the tick; the player's own attack with the
     * hurt cooldown put back, then a plain 3 Hamon hit under the cooldown; the burn on every fifth entity hit; at a
     * block the dig rule; the swing (every 2nd tick) and spark sounds for the user; no start sound.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hamonBarrageAttacksHitsRawUnderTheCooldownAndBurnsEveryFifthHit(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHamonRulesBarrage", "b2d1e3f4-0b1d-4c2e-8f3a-5b6c7d8e9f07");
        ServerPlayer player = holder.player;
        try (Sounds sounds = new Sounds(helper)) {
            UltimateLifeformCombat.started(player, holder.data, UltimateLifeformSpec.ULTIMATE_HAMON_BARRAGE);
            helper.assertTrue(!sounds.anyOf(UltimateLifeformAddon.SOUNDS.get("ultimate_hamon_barrage").get()),
                    "1.16 played no start sound for the Ultimate Hamon Barrage; heard " + sounds.heard);
        }
        Cow cow = mob(helper, EntityType.COW, new Vec3(2.5D, 2.0D, 4.5D));
        holder.lookAt(cow);
        holder.aimNothing();
        UltimateLifeformCombat.tickHeld(player, holder.data, UltimateLifeformSpec.ULTIMATE_HAMON_BARRAGE, 1);
        helper.assertTrue(cow.getHealth() == cow.getMaxHealth(),
                "1.16 hit only the crosshair entity; a mob in front with nothing under the crosshair took "
                        + (cow.getMaxHealth() - cow.getHealth()));
        holder.aim(cow);
        holder.notified.clear();
        player.tickCount = 2;
        UltimateLifeformCombat.tickHeld(player, holder.data, UltimateLifeformSpec.ULTIMATE_HAMON_BARRAGE, 2);
        float first = cow.getMaxHealth() - cow.getHealth();
        helper.assertTrue(first >= 3.0F,
                "1.16 hit the crosshair mob with the player's attack and a raw 3 Hamon (not 3 x 0.2); lost " + first);
        helper.assertTrue(holder.notified.contains(ModSoundEvents.HAMON_SPARK.get().getLocation() + " master 1.0 1.0"),
                "1.16 played the spark sound for the user on each barrage tick; notified " + holder.notified);
        player.tickCount = 3;
        UltimateLifeformCombat.tickHeld(player, holder.data, UltimateLifeformSpec.ULTIMATE_HAMON_BARRAGE, 3);
        helper.assertTrue(cow.getMaxHealth() - cow.getHealth() == first,
                "1.16 hit under the target's hurt cooldown: nothing more on the next tick; lost "
                        + (cow.getMaxHealth() - cow.getHealth()) + " after " + first);
        for (int ticksHeld = 4; ticksHeld <= 5; ticksHeld++) {
            player.tickCount = ticksHeld;
            UltimateLifeformCombat.tickHeld(player, holder.data, UltimateLifeformSpec.ULTIMATE_HAMON_BARRAGE,
                    ticksHeld);
            helper.assertTrue(!cow.hasEffect(UltimateLifeformAddon.HAMON_SPREAD),
                    "1.16 burned only on the fifth entity hit; burning after the hit of tick " + ticksHeld);
        }
        player.tickCount = 6;
        UltimateLifeformCombat.tickHeld(player, holder.data, UltimateLifeformSpec.ULTIMATE_HAMON_BARRAGE, 6);
        helper.assertTrue(cow.hasEffect(UltimateLifeformAddon.HAMON_SPREAD),
                "1.16 burned the target on its fifth entity hit (hurtCount), not on every fifth tick");
        cow.discard();
        BlockPos dirt = holder.aimBlock(helper, new BlockPos(2, 3, 4), Blocks.DIRT);
        player.tickCount = 7;
        UltimateLifeformCombat.tickHeld(player, holder.data, UltimateLifeformSpec.ULTIMATE_HAMON_BARRAGE, 7);
        helper.assertTrue(helper.getLevel().getBlockState(dirt).isAir(),
                "1.16 broke a soft crosshair block in the Ultimate Hamon Barrage");
        long swings = holder.notified.stream()
                .filter(line -> line.equals(ModSoundEvents.STAND_PUNCH_BARRAGE_SWING.get().getLocation()
                        + " master 1.0 1.0"))
                .count();
        helper.assertTrue(swings == 3,
                "1.16 played the barrage swing for the user every 2nd tick: on 3 of the ticks 2 to 7; got "
                        + swings);
        holder.close();
        helper.succeed();
    }

    /*
     * 1.16 UltimateLifeformHamonKick: swingHand(), ULTIMATE_LIFEFORM_KICK (ultimate_hamon_barrage, 1.0, 1.25) for the
     * kicker; the kick ended on the ground or in a fluid, not on a solid block at the feet.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "ulf_own_kick_swings_sounds_for_the_kicker_and_ends_i")
    public static void kickSwingsSoundsForTheKickerAndEndsInAFluid(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHamonRulesKick", "b2d1e3f4-0b1d-4c2e-8f3a-5b6c7d8e9f08");
        ServerPlayer player = holder.player;
        player.swinging = false;
        try (Sounds sounds = new Sounds(helper)) {
            UltimateLifeformCombat.started(player, holder.data, UltimateLifeformSpec.ULTIMATE_HAMON_KICK);
            SoundEvent kick = UltimateLifeformAddon.SOUNDS.get("ultimate_hamon_barrage").get();
            helper.assertTrue(player.swinging
                            && holder.notified.contains(kick.getLocation() + " player 1.0 1.25")
                            && !sounds.anyOf(kick),
                    "1.16 swung and played the kick sound (1.0, 1.25) for the kicker alone; swinging="
                            + player.swinging + " notified " + holder.notified + " heard " + sounds.heard);
        }
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 4, 5))));
        holder.data.beginHeld(player, UltimateLifeformSpec.ULTIMATE_HAMON_KICK);
        player.setOnGround(false);
        BlockPos feet = player.blockPosition();
        helper.getLevel().setBlockAndUpdate(feet, Blocks.STONE.defaultBlockState());
        UltimateLifeformCombat.tickHeld(player, holder.data, UltimateLifeformSpec.ULTIMATE_HAMON_KICK, 2);
        helper.assertTrue(holder.data.getHeldAbility() == UltimateLifeformSpec.ULTIMATE_HAMON_KICK,
                "1.16 went on kicking with a solid block at the feet off the ground");
        helper.getLevel().setBlockAndUpdate(feet, Blocks.WATER.defaultBlockState());
        UltimateLifeformCombat.tickHeld(player, holder.data, UltimateLifeformSpec.ULTIMATE_HAMON_KICK, 3);
        helper.assertTrue(holder.data.getHeldAbility() == null,
                "1.16 ended the kick in a fluid");
        // only the kick's own waves: a neighbouring test's stay its own
        helper.getLevel().getEntitiesOfClass(UltimateAreaEffectEntity.class,
                player.getBoundingBox().inflate(8.0D), wave -> wave.getOwner() == player).forEach(Entity::discard);
        holder.close();
        helper.succeed();
    }

    /*
     * 1.16 UltimateHamonTurquoiseBlueOverdriveEntity: KarsUtil's hit (Hamon spread into undead), hits with no owner,
     * no team rule, the projectile box as the hit area, and removal once its known owner died.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "ulf_own_turquoise_hits_as_kars_util_without_owner_or")
    public static void turquoiseHitsAsKarsUtilWithoutOwnerOrTeamRuleAndDiesWithItsOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // a walled pool, so no water leaves the test's box
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                for (int y = 1; y <= 4; y++) {
                    boolean wall = y == 1 || x == 0 || x == 7 || z == 0 || z == 7;
                    level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)),
                            wall ? Blocks.STONE.defaultBlockState() : Blocks.WATER.defaultBlockState());
                }
            }
        }
        Cow owner = mob(helper, EntityType.COW, new Vec3(1.5D, 2.0D, 1.5D));
        Zombie undead = mob(helper, EntityType.ZOMBIE, new Vec3(4.5D, 2.0D, 6.5D));
        PlayerTeam team = level.getScoreboard().addPlayerTeam("ulf_f2_turquoise");
        level.getScoreboard().addPlayerToTeam(owner.getScoreboardName(), team);
        level.getScoreboard().addPlayerToTeam(undead.getScoreboardName(), team);
        undead.baseTick();
        owner.baseTick();
        helper.assertTrue(undead.isInWaterOrBubble() && owner.isAlliedTo(undead),
                "Premise: the allied zombie is not in the water");
        UltimateAreaEffectEntity wave = turquoise(helper, owner, undead.position().add(0.0D, 1.0D, 0.0D));
        wave.tick();
        helper.assertTrue(undead.getHealth() < undead.getMaxHealth(),
                "1.16 had no team rule: the owner's team mate in the wave's box was hit");
        helper.assertTrue(undead.hasEffect(ModStatusEffects.HAMON_SPREAD),
                "1.16 KarsUtil spread the wave's Hamon into an undead target");
        wave.discard();
        level.getScoreboard().removePlayerTeam(team);
        Zombie second = mob(helper, EntityType.ZOMBIE, new Vec3(6.5D, 2.0D, 2.5D));
        second.baseTick();
        UltimateAreaEffectEntity ownerless = turquoise(helper, owner, second.position().add(0.0D, 1.0D, 0.0D));
        owner.discard();
        ownerless.tick();
        helper.assertTrue(!ownerless.isRemoved() && second.getHealth() < second.getMaxHealth(),
                "1.16 hit with no owner to be found; removed=" + ownerless.isRemoved() + " health="
                        + second.getHealth());
        ownerless.discard();
        Cow dying = mob(helper, EntityType.COW, new Vec3(1.5D, 2.0D, 6.5D));
        UltimateAreaEffectEntity orphan = turquoise(helper, dying, second.position().add(0.0D, 1.0D, 0.0D));
        dying.setHealth(0.0F);
        orphan.tick();
        helper.assertTrue(orphan.isRemoved(),
                "1.16 removed the wave once its owner was dead");
        undead.discard();
        second.discard();
        dying.discard();
        helper.succeed();
    }

    // 1.16 summonGlider: HamonUtil.emitHamonSparkParticles at the top of the glider (intensity 0.1, sound 0.2)
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lifeMagnetismSparksAtTheGlider(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHamonRulesGlider", "b2d1e3f4-0b1d-4c2e-8f3a-5b6c7d8e9f09");
        holder.data.addHamonCharge(1_000);
        holder.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.OAK_LEAVES));
        holder.aimNothing();
        try (Sounds sounds = new Sounds(helper)) {
            UltimateLifeformCombat.perform(holder.player, holder.data, UltimateLifeformSpec.HAMON_LIFE_MAGNETISM, 0);
            List<LeavesGliderEntity> gliders = helper.getLevel().getEntitiesOfClass(LeavesGliderEntity.class,
                    holder.player.getBoundingBox().inflate(4.0D));
            helper.assertTrue(gliders.size() == 1, "Premise: no glider; " + gliders.size());
            LeavesGliderEntity glider = gliders.get(0);
            Vec3 top = new Vec3(glider.getX(), glider.getY(1.0D), glider.getZ());
            helper.assertTrue(sounds.heard.stream().anyMatch(h ->
                            h.sound.equals(ModSoundEvents.HAMON_SPARK.get().getLocation())
                                    && h.position.distanceToSqr(top) < 1.0E-4D && Math.abs(h.volume - 0.2F) < 1.0E-4F),
                    "1.16 emitted core Hamon sparks with their sound (0.2) at the glider's top; heard " + sounds.heard);
            holder.player.stopRiding();
            glider.discard();
        }
        holder.close();
        helper.succeed();
    }

    /*
     * 1.16 UltimateLifeformData.writeNBT kept no hamonCooldown; setCurrentHamonCharge sent the data on every change,
     * so every breath tick reached the user's client; the add-on's effects were tracked effects.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void chargeLoadsWithoutItsTimerBreathSyncsEachTickAndEffectsAreTracked(GameTestHelper helper) {
        List<Packet<?>> sent = new ArrayList<>();
        ServerPlayer player = recordingPlayer(helper, "UltimateHamonRulesSync", "b2d1e3f4-0b1d-4c2e-8f3a-5b6c7d8e9f10",
                sent);
        PlayerPower power = PlayerPower.get(player);
        UltimateLifeformData data = power.getCurTypeData(UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        data.addHamonCharge(3_000);
        CompoundTag saved = data.serializeNBT(helper.getLevel().registryAccess());
        helper.assertTrue(!saved.contains("HamonCooldown"),
                "1.16 writeNBT kept no hamonCooldown; the save holds " + saved.get("HamonCooldown"));
        saved.putInt("HamonCooldown", 600);
        UltimateLifeformData loaded = new UltimateLifeformData();
        loaded.deserializeNBT(helper.getLevel().registryAccess(), saved);
        helper.assertTrue(loaded.getHamonCooldown() == 0 && loaded.getHamonCharge() == 3_000,
                "1.16 loaded a charge whose decay timer started again; timer=" + loaded.getHamonCooldown());
        sent.clear();
        for (int ticksHeld = 1; ticksHeld <= 4; ticksHeld++) {
            UltimateLifeformCombat.tickHeld(player, data, UltimateLifeformSpec.HAMON_BREATH, ticksHeld);
        }
        long syncs = sent.stream().filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                && custom.payload().getClass().getSimpleName().equals("TrPowerDataPacket")).count();
        helper.assertTrue(syncs >= 4,
                "1.16 sent the charge to the user on each of 4 breath ticks; sent " + syncs);
        helper.assertTrue(ModStatusEffects.TRACKED_EFFECTS.contains(UltimateLifeformAddon.HAMON_SPREAD)
                        && ModStatusEffects.TRACKED_EFFECTS.contains(UltimateLifeformAddon.ULTIMATE_ARMOR)
                        && ModStatusEffects.TRACKED_EFFECTS.contains(UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR)
                        && ModStatusEffects.TRACKED_EFFECTS.contains(UltimateLifeformAddon.ULTIMATE_HEALTH_BOOST),
                "1.16 InitEffects marked the burn, both armors and the health boost as tracked effects");
        power.setPowerType(null);
        player.discard();
        helper.succeed();
    }

    private static UltimateAreaEffectEntity turquoise(GameTestHelper helper, LivingEntity owner, Vec3 at) {
        UltimateAreaEffectEntity wave = UltimateLifeformAddon.ULTIMATE_TURQUOISE_BLUE_OVERDRIVE.get()
                .create(helper.getLevel());
        helper.assertTrue(wave != null, "fixture: no Turquoise entity");
        wave.initializeTurquoise(owner, 10.0F, 1.5F, 190, 0.0D);
        wave.setPos(at);
        helper.getLevel().addFreshEntity(wave);
        wave.baseTick();
        return wave;
    }

    private static <T extends LivingEntity> T mob(GameTestHelper helper, EntityType<T> type, Vec3 relative) {
        T mob = type.create(helper.getLevel());
        if (mob == null) {
            throw new IllegalStateException("Mob fixture failed: " + type);
        }
        mob.setPos(Vec3.atBottomCenterOf(helper.absolutePos(BlockPos.ZERO)).add(relative.x - 0.5D, relative.y,
                relative.z - 0.5D));
        if (mob instanceof net.minecraft.world.entity.Mob ai) {
            ai.setNoAi(true);
            ai.setPersistenceRequired();
        }
        mob.setNoGravity(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000.0D);
        mob.setHealth(1_000.0F);
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static boolean isWarning(ConditionCheck check, String postfix) {
        return check.getWarning() != null && check.getWarning().getContents() instanceof TranslatableContents contents
                && ("jojo.message.action_condition." + postfix).equals(contents.getKey());
    }

    private static ServerPlayer recordingPlayer(GameTestHelper helper, String name, String uuid,
            List<Packet<?>> sent) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.fromString(uuid), name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(server, connection, player, CommonListenerCookie.createInitial(profile,
                false)) {
            @Override
            public void send(Packet<?> packet) {
                sent.add(packet);
            }

            @Override
            public void send(Packet<?> packet, PacketSendListener listener) {
                sent.add(packet);
            }
        };
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setGameMode(GameType.SURVIVAL);
        level.addNewPlayer(player);
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
        if (!power.trySetPowerType(UltimateLifeformAddon.ULTIMATE_LIFEFORM.get())) {
            throw new IllegalStateException("Sync test power grant failed: " + name);
        }
        power.updateAvailableMoves();
        return player;
    }

    private static Holder holder(GameTestHelper helper, String name, String uuid) {
        List<Component> messages = new ArrayList<>();
        List<String> notified = new ArrayList<>();
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.fromString(uuid), name)) {
            @Override
            public void displayClientMessage(Component message, boolean overlay) {
                if (overlay) {
                    messages.add(message);
                }
            }
        };
        // the sounds sent to this player alone
        UserSoundRecorder.attach(player, heard -> notified.add(heard.line()));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setGameMode(GameType.SURVIVAL);
        if (!helper.getLevel().addFreshEntity(player)) {
            throw new IllegalStateException("Hamon rules test player could not be added: " + name);
        }
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
        if (!power.trySetPowerType(UltimateLifeformAddon.ULTIMATE_LIFEFORM.get())) {
            throw new IllegalStateException("Hamon rules test power grant failed: " + name);
        }
        power.updateAvailableMoves();
        UltimateLifeformData data = power.getCurTypeData(UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        return new Holder(player, power, data, messages, notified);
    }

    private record Holder(ServerPlayer player, PlayerPower power, UltimateLifeformData data,
            List<Component> messages, List<String> notified) {
        void press(GameTestHelper helper, UltimateLifeformSpec spec, short key) {
            Ability ability = power.getAbility(spec.id);
            BufferingState buffering = BufferingState.clickCanBuffer();
            FriendlyByteBuf extra = new FriendlyByteBuf(Unpooled.buffer());
            try {
                Object held = AbilityInput.keyPress(key, ability, player, extra, InputMethod.HOLD, 0.0F, buffering,
                        ability.getAbilityId());
                helper.assertTrue(held != null || buffering.isActionSuccess,
                        "Premise: the " + spec.id + " press was refused: " + messages);
            }
            finally {
                extra.release();
            }
        }

        void aim(Entity target) {
            lookAt(target);
            aimAt(target);
        }

        void aimAt(Entity target) {
            LivingComponentAction.getComponent(player).entityAim.setTarget(new ActionTarget(target));
        }

        void lookAt(Entity target) {
            player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
            player.setYBodyRot(player.getYRot());
            player.yBodyRotO = player.getYRot();
        }

        void aimNothing() {
            LivingComponentAction.getComponent(player).entityAim.setTarget(ActionTarget.EMPTY);
        }

        BlockPos aimBlock(GameTestHelper helper, BlockPos relative, Block block) {
            BlockPos pos = helper.absolutePos(relative);
            helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
            player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(pos));
            LivingComponentAction.getComponent(player).entityAim.setTarget(new ActionTarget(pos, Direction.NORTH));
            return pos;
        }

        boolean saw(String postfix) {
            String key = "jojo.message.action_condition." + postfix;
            return messages.stream().anyMatch(message -> message.getContents() instanceof TranslatableContents contents
                    && key.equals(contents.getKey()));
        }

        void close(short key) {
            AbilityInput.keyRelease(key, player);
            close();
        }

        void close() {
            power.setPowerType(null);
            player.discard();
        }
    }

    // a player outside the level that records what the server sends it; it tracks what is added to a seenBy
    private static final class SoundWatcher extends FakePlayer {
        private final List<Packet<?>> sent = new ArrayList<>();

        SoundWatcher(ServerLevel level, String name) {
            super(level, new GameProfile(UUID.randomUUID(), name));
            Connection channel = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(channel);
            this.connection = new ServerGamePacketListenerImpl(level.getServer(), channel, this,
                    CommonListenerCookie.createInitial(getGameProfile(), false)) {
                @Override
                public void send(Packet<?> packet) {
                    sent.add(packet);
                }

                @Override
                public void send(Packet<?> packet, PacketSendListener listener) {
                    sent.add(packet);
                }
            };
        }
    }

    // sounds played into the world (not those sent to one player) in this test's own box
    private static final class Sounds implements AutoCloseable {
        private final List<Heard> heard = new ArrayList<>();
        private final Consumer<PlayLevelSoundEvent.AtEntity> atEntity;
        private final Consumer<PlayLevelSoundEvent.AtPosition> atPosition;

        private record Heard(ResourceLocation sound, Entity entity, Vec3 position, float volume, float pitch) {}

        Sounds(GameTestHelper helper) {
            Vec3 origin = Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(4, 4, 4)));
            atEntity = event -> {
                if (event.getLevel() == helper.getLevel() && event.getSound() != null
                        && event.getEntity().position().distanceToSqr(origin) < 49.0D) {
                    heard.add(new Heard(event.getSound().value().getLocation(), event.getEntity(),
                            event.getEntity().position(), event.getOriginalVolume(), event.getOriginalPitch()));
                }
            };
            atPosition = event -> {
                if (event.getLevel() == helper.getLevel() && event.getSound() != null
                        && event.getPosition().distanceToSqr(origin) < 49.0D) {
                    heard.add(new Heard(event.getSound().value().getLocation(), null, event.getPosition(),
                            event.getOriginalVolume(), event.getOriginalPitch()));
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayLevelSoundEvent.AtEntity.class, atEntity);
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayLevelSoundEvent.AtPosition.class,
                    atPosition);
        }

        boolean atEntity(SoundEvent sound, Entity entity, float volume, float pitch) {
            return heard.stream().anyMatch(h -> h.sound.equals(sound.getLocation()) && h.entity == entity
                    && h.volume == volume && h.pitch == pitch);
        }

        boolean atPosition(SoundEvent sound, Vec3 position) {
            return heard.stream().anyMatch(h -> h.sound.equals(sound.getLocation()) && h.entity == null
                    && h.position.distanceToSqr(position) < 1.0E-4D);
        }

        boolean anyOf(SoundEvent sound) {
            return heard.stream().anyMatch(h -> h.sound.equals(sound.getLocation()));
        }

        @Override
        public void close() {
            NeoForge.EVENT_BUS.unregister(atEntity);
            NeoForge.EVENT_BUS.unregister(atPosition);
        }
    }
}
