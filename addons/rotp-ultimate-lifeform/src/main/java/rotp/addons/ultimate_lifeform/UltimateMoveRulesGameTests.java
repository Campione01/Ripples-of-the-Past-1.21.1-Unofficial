package rotp.addons.ultimate_lifeform;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformCombat;
import rotp.addons.ultimate_lifeform.entity.UltimateProjectileEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateScaleProjectileEntity;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformPowerType;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.network.s2c.TrPillarmanParticlesPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;

/**
 * 1.16 rules of the Ultimate Lifeform moves restored in wave 28: presses
 * during a move, hold-only cooldowns, the evade hook, target rules, sounds,
 * the tentacles' owner-bound motion and the passives.
 */
@GameTestHolder(UltimateLifeformAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimateMoveRulesGameTests {
    private static final double EPS = 1.0E-4D;

    private UltimateMoveRulesGameTests() {}

    // 1.16 PowerBaseImpl.checkRequirements refused another action while one
    // was held: the held Ultimate Barrage goes on, the Horn Attack waits.
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void otherPressesWaitWhileAHeldMoveRuns(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesWait",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f01", GameType.SURVIVAL,
                false);
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        short barrageKey = (short) 32_200;
        short hornKey = (short) 32_201;
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.ULTIMATE_BARRAGE, barrageKey));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(holder.data.getHeldAbility()
                            == UltimateLifeformSpec.ULTIMATE_BARRAGE,
                    "Premise: the Ultimate Barrage is not held");
            boolean accepted = holder.tryPress(
                    UltimateLifeformSpec.HORN_ATTACK, hornKey);
            helper.assertTrue(!accepted,
                    "1.16 refused the Horn Attack while the Ultimate Barrage"
                            + " was held; the press went through");
        });
        helper.runAfterDelay(7, () -> {
            helper.assertTrue(holder.data.getHeldAbility()
                            == UltimateLifeformSpec.ULTIMATE_BARRAGE
                            && !holder.data.isAbilityOnCooldown(
                                    UltimateLifeformSpec.HORN_ATTACK.id),
                    "1.16 kept the held Ultimate Barrage going and did not"
                            + " fire the Horn Attack: held="
                            + holder.data.getHeldAbility() + " horn cooldown="
                            + holder.data.getAbilityCooldownTicks(
                                    UltimateLifeformSpec.HORN_ATTACK.id));
            AbilityInput.keyRelease(hornKey, holder.player);
            holder.close(barrageKey);
            helper.succeed();
        });
    }

    // 1.16 PillarmanAbsorption.heldAllowsOtherAction: only Absorption let
    // another press through while it was held.
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void absorptionLetsAnotherPressThrough(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesAbsorbPress",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f02", GameType.SURVIVAL,
                false);
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 3.75D));
        short absorbKey = (short) 32_202;
        short hornKey = (short) 32_203;
        helper.runAfterDelay(1, () -> {
            holder.aim(cow);
            holder.press(helper, UltimateLifeformSpec.ABSORPTION, absorbKey);
        });
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(holder.data.getHeldAbility()
                            == UltimateLifeformSpec.ABSORPTION,
                    "Premise: Absorption is not held");
            helper.assertTrue(holder.tryPress(
                            UltimateLifeformSpec.HORN_ATTACK, hornKey),
                    "1.16 let another press through while Absorption was"
                            + " held; the Horn Attack was held back");
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(holder.data.isAbilityOnCooldown(
                            UltimateLifeformSpec.HORN_ATTACK.id),
                    "1.16 fired the Horn Attack pressed during Absorption");
            AbilityInput.keyRelease(absorbKey, holder.player);
            cow.discard();
            holder.close(hornKey);
            helper.succeed();
        });
    }

    // 1.16 PowerBaseImpl.stopHeldAction gave a hold-only action its cooldown
    // when the hold ended: Evasion the ticks held (cooldown * ticks / 50),
    // Scale Shot its 200.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void holdOnlyMovesTakeTheirCooldownWhenTheHoldEnds(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesHoldCooldown",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f03", GameType.SURVIVAL,
                false);
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        String evasion = UltimateLifeformSpec.EVASION.id;
        String scaleShot = UltimateLifeformSpec.SCALE_SHOT.id;
        short evasionKey = (short) 32_204;
        short scaleKey = (short) 32_205;
        int[] held = new int[1];
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.EVASION, evasionKey));
        helper.runAfterDelay(5, () -> helper.assertTrue(
                holder.data.getHeldAbility() == UltimateLifeformSpec.EVASION
                        && !holder.data.isAbilityOnCooldown(evasion),
                "1.16 set no Evasion cooldown while it was held; cooldown="
                        + holder.data.getAbilityCooldownTicks(evasion)));
        helper.runAfterDelay(12, () -> {
            held[0] = holder.data.getHeldTicks();
            AbilityInput.keyRelease(evasionKey, holder.player);
        });
        helper.runAfterDelay(13, () -> {
            int cooldown = holder.data.getAbilityCooldownTicks(evasion);
            helper.assertTrue(held[0] > 0
                            && (cooldown == held[0] || cooldown == held[0] - 1),
                    "1.16 gave Evasion a cooldown of the ticks it was held ("
                            + held[0] + "); got " + cooldown);
            holder.press(helper, UltimateLifeformSpec.SCALE_SHOT, scaleKey);
        });
        helper.runAfterDelay(17, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility()
                                    == UltimateLifeformSpec.SCALE_SHOT
                            && !holder.data.isAbilityOnCooldown(scaleShot),
                    "1.16 set no Scale Shot cooldown while it was held;"
                            + " cooldown="
                            + holder.data.getAbilityCooldownTicks(scaleShot));
            AbilityInput.keyRelease(scaleKey, holder.player);
        });
        helper.runAfterDelay(18, () -> {
            int cooldown = holder.data.getAbilityCooldownTicks(scaleShot);
            helper.assertTrue(cooldown >= 199 && cooldown <= 200,
                    "1.16 gave the Scale Shot its 200 when the hold ended;"
                            + " got " + cooldown);
            discardScales(helper, holder.player);
            holder.close(scaleKey);
            helper.succeed();
        });
    }

    // 1.16 Action.getCooldown: the Tentacles' technical cooldown (80) was set
    // in creative too; Evasion's additional one was not.
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void creativeTentaclesTakeTheirTechnicalCooldown(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesCreativeCooldown",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f04", GameType.CREATIVE,
                false);
        short tentacleKey = (short) 32_206;
        short evasionKey = (short) 32_207;
        String tentacles = UltimateLifeformSpec.TENTACLES.id;
        helper.runAfterDelay(1, () -> holder.click(
                helper, UltimateLifeformSpec.TENTACLES, tentacleKey));
        helper.runAfterDelay(4, () -> {
            int cooldown = holder.data.getAbilityCooldownTicks(tentacles);
            helper.assertTrue(cooldown >= 76 && cooldown <= 80,
                    "1.16 set the Tentacles' 80 tick technical cooldown in"
                            + " creative; got " + cooldown);
        });
        helper.runAfterDelay(6, () -> holder.press(
                helper, UltimateLifeformSpec.EVASION, evasionKey));
        helper.runAfterDelay(11, () -> AbilityInput.keyRelease(
                evasionKey, holder.player));
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(!holder.data.isAbilityOnCooldown(
                            UltimateLifeformSpec.EVASION.id),
                    "1.16 gave a creative Evasion no cooldown; got "
                            + holder.data.getAbilityCooldownTicks(
                                    UltimateLifeformSpec.EVASION.id));
            holder.close(evasionKey);
            helper.succeed();
        });
    }

    /*
     * 1.16 UltimateLifeformUltimateAgility.onUserAttacked ran on
     * LivingAttackEvent at the head of PlayerEntity.hurt, so a creative user
     * countered too; the counter hit only a living direct attacker (never an
     * arrow's shooter) as a player attack of the attack damage without the
     * held item's modifiers, times 0.75.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void agilityCountersAtTheHeadOfPlayerHurt(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesAgility",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f05", GameType.CREATIVE,
                true);
        ServerPlayer player = holder.player;
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        sword.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null && !instance.hasModifier(modifier.id())) {
                instance.addTransientModifier(modifier);
            }
        });
        float expected = DamageUtil.getDamageWithoutHeldItem(player) * 0.75F;
        float withSword = (float) player.getAttributeValue(
                Attributes.ATTACK_DAMAGE) * 0.75F;
        helper.assertTrue(Math.abs(expected - withSword) > 0.5F,
                "Premise: the sword does not change the attack damage");
        holder.data.beginHeld(player, UltimateLifeformSpec.ULTIMATE_AGILITY);
        Cow attacker = cow(helper, new Vec3(2.5D, 2.0D, 3.5D));
        Cow shooter = cow(helper, new Vec3(4.5D, 2.0D, 3.5D));
        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        arrow.setOwner(shooter);
        arrow.setPos(shooter.position().add(0.0D, 1.0D, 0.0D));
        int counters = 0;
        for (int hit = 0; hit < 60; hit++) {
            attacker.invulnerableTime = 0;
            float before = attacker.getHealth();
            player.hurt(player.damageSources().mobAttack(attacker), 1.0F);
            float dealt = before - attacker.getHealth();
            if (dealt > 0.0F) {
                counters++;
                DamageSource counter = attacker.getLastDamageSource();
                helper.assertTrue(Math.abs(dealt - expected) < EPS
                                && counter != null
                                && counter.is(DamageTypes.PLAYER_ATTACK)
                                && counter.getEntity() == player,
                        "1.16 countered with a player attack of " + expected
                                + " (no held item); got " + dealt + " from "
                                + counter);
            }
        }
        helper.assertTrue(counters > 0,
                "1.16 countered (30%) at the head of PlayerEntity.hurt, also"
                        + " for a creative user; 60 hits gave no counter");
        for (int hit = 0; hit < 60; hit++) {
            shooter.invulnerableTime = 0;
            player.hurt(player.damageSources().arrow(arrow, shooter), 1.0F);
        }
        helper.assertTrue(shooter.getHealth() == 1_000.0F,
                "1.16 countered only a living direct attacker, never the"
                        + " shooter of an evaded arrow; the shooter has "
                        + shooter.getHealth());
        holder.data.releaseHeld(player);
        attacker.discard();
        shooter.discard();
        holder.close((short) 32_208);
        helper.succeed();
    }

    // 1.16 onUserAttacked skipped explosions and played PILLAR_MAN_EVASION at
    // the attacker for every evaded hit.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void evasionRingsAtTheAttackerAndLetsExplosionsThrough(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesEvasion",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f06", GameType.SURVIVAL,
                true);
        ServerPlayer player = holder.player;
        holder.data.setCommandEnergy(player, UltimateLifeformData.MAX_ENERGY);
        holder.data.beginHeld(player, UltimateLifeformSpec.EVASION);
        Cow attacker = cow(helper, new Vec3(2.5D, 2.0D, 3.5D));
        Creeper creeper = EntityType.CREEPER.create(helper.getLevel());
        helper.assertTrue(creeper != null, "Premise: no creeper");
        creeper.setPos(attacker.position());
        try (Sounds sounds = new Sounds(helper)) {
            float health = player.getHealth();
            boolean hurt = player.hurt(
                    player.damageSources().mobAttack(attacker), 2.0F);
            helper.assertTrue(!hurt && player.getHealth() == health,
                    "Premise: Evasion did not evade a mob's hit");
            helper.assertTrue(sounds.atEntity(
                            ModSoundEvents.PILLAR_MAN_EVASION.get(), attacker,
                            1.0F, 1.0F),
                    "1.16 played the evasion sound at the attacker; heard "
                            + sounds.heard);
            boolean blast = player.hurt(
                    player.damageSources().explosion(creeper, creeper), 2.0F);
            helper.assertTrue(blast && player.getHealth() < health,
                    "1.16 did not evade an explosion; hurt=" + blast
                            + " health " + health + " -> "
                            + player.getHealth());
        }
        holder.data.releaseHeld(player);
        attacker.discard();
        holder.close((short) 32_209);
        helper.succeed();
    }

    /*
     * 1.16 UltimateLifeformBladeBarrage.onUserAttacked: a vanilla projectile
     * hit was cancelled only while a projectile within reach came from the
     * front (48.75 degrees around the look), which rang the anvil; one from
     * behind hit.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bladeBarrageDeflectsOnlyProjectilesFromTheFront(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesDeflect",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f07", GameType.SURVIVAL,
                true);
        ServerPlayer player = holder.player;
        holder.data.setCommandEnergy(player, UltimateLifeformData.MAX_ENERGY);
        face(player, 0.0F, 0.0F);
        holder.data.beginHeld(player, UltimateLifeformSpec.BLADE_BARRAGE);
        Vec3 eye = player.getEyePosition();
        try (Sounds sounds = new Sounds(helper)) {
            Arrow front = arrow(helper, eye.add(0.0D, 0.0D, 2.0D),
                    new Vec3(0.0D, 0.0D, -1.5D));
            float health = player.getHealth();
            boolean hurt = player.hurt(
                    player.damageSources().arrow(front, null), 3.0F);
            helper.assertTrue(!hurt && player.getHealth() == health,
                    "1.16 deflected an arrow coming from the front; hurt="
                            + hurt);
            helper.assertTrue(sounds.atPosition(SoundEvents.ANVIL_LAND,
                            front.position(), 0.4F, 1.35F),
                    "1.16 rang the anvil at a deflected arrow; heard "
                            + sounds.heard);
            front.discard();
            Arrow behind = arrow(helper, eye.add(0.0D, 0.0D, -2.0D),
                    new Vec3(0.0D, 0.0D, 1.5D));
            boolean back = player.hurt(
                    player.damageSources().arrow(behind, null), 3.0F);
            helper.assertTrue(back && player.getHealth() < health,
                    "1.16 let an arrow from behind hit during the Blade"
                            + " Barrage; hurt=" + back);
            behind.discard();
        }
        holder.data.releaseHeld(player);
        holder.close((short) 32_210);
        helper.succeed();
    }

    // 1.16 heldWalkSpeed / getWalkSpeed: the heavy punch and blade slash
    // halved the walk speed, Evasion's 5 slowed nothing (only below 1).
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void movesSlowTheirUserAs116(GameTestHelper helper) {
        helper.assertTrue(
                UltimateLifeformSpec.HEAVY_PUNCH.heldWalkSpeed() == 0.5F
                        && UltimateLifeformSpec.BLADE_SLASH.heldWalkSpeed()
                                == 0.5F
                        && UltimateLifeformSpec.EVASION.heldWalkSpeed()
                                == 1.0F,
                "1.16 walk speeds: heavy punch 0.5, blade slash 0.5, Evasion"
                        + " no change; got "
                        + UltimateLifeformSpec.HEAVY_PUNCH.heldWalkSpeed()
                        + " / "
                        + UltimateLifeformSpec.BLADE_SLASH.heldWalkSpeed()
                        + " / "
                        + UltimateLifeformSpec.EVASION.heldWalkSpeed());
        Holder holder = holder(helper, "UltimateRulesWalk",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f08", GameType.SURVIVAL,
                false);
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        short key = (short) 32_211;
        helper.runAfterDelay(1, () -> holder.click(
                helper, UltimateLifeformSpec.HEAVY_PUNCH, key));
        helper.runAfterDelay(3, () -> {
            EntityActionInstance action =
                    LivingComponentAction.getCurEntityAction(holder.player);
            helper.assertTrue(action != null
                            && action.userWalkSpeed == 0.5F,
                    "1.16 halved the walk speed during the heavy punch; got "
                            + (action == null ? "no action"
                                    : action.userWalkSpeed));
            holder.close(key);
            helper.succeed();
        });
    }

    // 1.16 UltimateLifeformHeavyPunch.Instance tick 5: the mouse target of
    // that tick only, and a punched mob got the knockback impact.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void heavyPunchStrikesTheTickFiveCrosshairAndArmsTheImpact(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesHeavyPunch",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f09", GameType.SURVIVAL,
                false);
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 4.5D));
        holder.lookAt(cow);
        holder.aimNothing();
        UltimateLifeformCombat.tickHeld(holder.player, holder.data,
                UltimateLifeformSpec.HEAVY_PUNCH, 5);
        helper.assertTrue(cow.getHealth() == 1_000.0F,
                "1.16 punched only the tick 5 mouse target; a mob in front"
                        + " with nothing under the crosshair took "
                        + (1_000.0F - cow.getHealth()));
        holder.aim(cow);
        UltimateLifeformCombat.tickHeld(holder.player, holder.data,
                UltimateLifeformSpec.HEAVY_PUNCH, 5);
        KnockbackCollisionImpact impact =
                KnockbackCollisionImpact.getExistingHandler(cow);
        helper.assertTrue(Math.abs(1_000.0F - cow.getHealth() - 12.0F) < EPS
                        && impact != null && impact.isActive(),
                "1.16 punched the crosshair mob for 12 and armed its"
                        + " knockback impact; damage="
                        + (1_000.0F - cow.getHealth()) + " impact="
                        + (impact != null && impact.isActive()));
        cow.discard();
        holder.close((short) 32_212);
        helper.succeed();
    }

    /*
     * 1.16 UltimateLifeformActions registered heavy_punch (:76) and
     * small_sandstorm (:90) with swingHand(); with no hold duration the
     * client swung the main arm when the press went off (core InputHandler
     * .actionSwingsHand :995-998, mc.player.swing :531-533), and every viewer
     * saw it. The heavy punch's Instance swung again on tick 3.
     */
    @GameTest(template = "empty", timeoutTicks = 30, batch = "ulf_own_small_sandstorm_and_heavy_punch_swing_the_ma")
    public static void smallSandstormAndHeavyPunchSwingTheMainArmAtThePress(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesSwingPress",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f22", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        holder.data.setCommandEnergy(player, UltimateLifeformData.MAX_ENERGY);
        holder.data.setPreviousMode(player,
                rotp.core.impl.powers.pillarman.PillarmanMode.WIND);
        short key = (short) 32_225;
        // the wave would fly 60 blocks through the other tests' boxes: it is
        // counted and kept out of the level
        int[] waves = {0};
        Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent>
                keepWaveOut = event -> {
                    if (event.getEntity() instanceof rotp.core.impl.powers
                                    .pillarman.PillarmanDivineSandstormEntity wave
                            && wave.getOwner() == player) {
                        waves[0]++;
                        event.setCanceled(true);
                    }
                };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false,
                net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class,
                keepWaveOut);
        helper.runAfterDelay(1, () -> {
            player.swinging = false;
            holder.click(helper, UltimateLifeformSpec.SMALL_SANDSTORM, key);
        });
        helper.runAfterDelay(4, () -> {
            NeoForge.EVENT_BUS.unregister(keepWaveOut);
            helper.assertTrue(waves[0] == 1,
                    "Premise: the Small Sandstorm did not go off; waves="
                            + waves[0]);
            helper.assertTrue(player.swinging
                            && player.swingingArm == InteractionHand.MAIN_HAND,
                    "1.16 swung the main arm when the Small Sandstorm went off"
                            + " (swingHand); swinging=" + player.swinging
                            + " arm=" + player.swingingArm);
            player.swinging = false;
            player.swingingArm = InteractionHand.OFF_HAND;
            UltimateLifeformCombat.started(player, holder.data,
                    UltimateLifeformSpec.HEAVY_PUNCH);
            helper.assertTrue(player.swinging
                            && player.swingingArm == InteractionHand.MAIN_HAND,
                    "1.16 swung the main arm when the heavy punch went off,"
                            + " before its tick-3 swing (swingHand); swinging="
                            + player.swinging + " arm=" + player.swingingArm);
            player.swinging = false;
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.HEAVY_PUNCH, 3);
            helper.assertTrue(player.swinging,
                    "Premise: the heavy punch's tick-3 swing is gone");
            holder.close(key);
            helper.succeed();
        });
    }

    /*
     * 1.16 UltimateLifeformBladeSlash.Instance: tick 6 the main hand swing and
     * HAMON_SYO_SWING (1.0, 1.25); tick 8 the crosshair living entity only,
     * THE_WORLD_PUNCH_HEAVY_ENTITY at it and the knockback impact.
     * UltimateLifeformBladeDash.Instance tick 1: swing and HAMON_SYO_SWING
     * (1.0, 1.0).
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bladeSlashAndDashSwingRingAndHitTheCrosshair(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesBladeSlash",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f10", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 4.5D));
        try (Sounds sounds = new Sounds(helper)) {
            holder.lookAt(cow);
            holder.aimNothing();
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.BLADE_SLASH, 6);
            helper.assertTrue(player.swinging
                            && sounds.atEntity(
                                    ModSoundEvents.HAMON_SYO_SWING.get(),
                                    player, 1.0F, 1.25F),
                    "1.16 swung and played HAMON_SYO_SWING (1.0, 1.25) on"
                            + " the slash's tick 6; swinging=" + player.swinging
                            + " heard " + sounds.heard);
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.BLADE_SLASH, 8);
            helper.assertTrue(cow.getHealth() == 1_000.0F,
                    "1.16 slashed only the crosshair entity; a mob in front"
                            + " with nothing under the crosshair took "
                            + (1_000.0F - cow.getHealth()));
            holder.aim(cow);
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.BLADE_SLASH, 8);
            KnockbackCollisionImpact impact =
                    KnockbackCollisionImpact.getExistingHandler(cow);
            helper.assertTrue(cow.getHealth() < 1_000.0F
                            && sounds.atEntity(ModSoundEvents
                                            .THE_WORLD_PUNCH_HEAVY_ENTITY.get(),
                                    cow, 1.2F, 0.8F)
                            && impact != null && impact.isActive(),
                    "1.16 slashed the crosshair mob with the heavy hit sound"
                            + " and the knockback impact; health="
                            + cow.getHealth() + " impact="
                            + (impact != null && impact.isActive())
                            + " heard " + sounds.heard);
            sounds.heard.clear();
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.BLADE_DASH_ATTACK, 1);
            helper.assertTrue(sounds.atEntity(
                            ModSoundEvents.HAMON_SYO_SWING.get(),
                            player, 1.0F, 1.0F),
                    "1.16 played HAMON_SYO_SWING (1.0, 1.0) on the dash's"
                            + " tick 1; heard " + sounds.heard);
        }
        cow.discard();
        holder.close((short) 32_213);
        helper.succeed();
    }

    /*
     * 1.16 UltimateLifeformBladeBarrage.holdTick: the crosshair target of the
     * tick; a block broken by the dig rule or its hit sound; a living entity
     * only under the crosshair; SILVER_CHARIOT_BARRAGE_SWIPE (0.5, 1.0) on
     * every held tick.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bladeBarrageBreaksBlocksSwipesAndHitsTheCrosshair(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesBladeBarrage",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f11", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        BlockPos relative = new BlockPos(2, 3, 4);
        try (Sounds sounds = new Sounds(helper)) {
            BlockPos dirt = holder.aimBlock(helper, relative, Blocks.DIRT);
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.BLADE_BARRAGE, 1);
            helper.assertTrue(helper.getLevel().getBlockState(dirt).isAir(),
                    "1.16 broke a soft crosshair block in the Blade Barrage");
            helper.assertTrue(sounds.atPosition(
                            ModSoundEvents.SILVER_CHARIOT_BARRAGE_SWIPE.get(),
                            new Vec3(player.getX(), player.getY(),
                                    player.getZ()), 0.5F, 1.0F),
                    "1.16 played the barrage swipe (0.5, 1.0) on each held"
                            + " tick; heard " + sounds.heard);
            BlockPos obsidian = holder.aimBlock(helper, relative,
                    Blocks.OBSIDIAN);
            sounds.heard.clear();
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.BLADE_BARRAGE, 2);
            helper.assertTrue(helper.getLevel().getBlockState(obsidian)
                            .is(Blocks.OBSIDIAN)
                            && sounds.blockHit(obsidian,
                                    Blocks.OBSIDIAN.defaultBlockState()
                                            .getSoundType()),
                    "1.16 rang the hit sound of a block the Blade Barrage"
                            + " could not break; heard " + sounds.heard);
            helper.getLevel().setBlockAndUpdate(obsidian,
                    Blocks.AIR.defaultBlockState());
        }
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 4.5D));
        holder.lookAt(cow);
        holder.aimNothing();
        UltimateLifeformCombat.tickHeld(player, holder.data,
                UltimateLifeformSpec.BLADE_BARRAGE, 3);
        helper.assertTrue(cow.getHealth() == 1_000.0F,
                "1.16 hit only the crosshair entity; a mob in front with"
                        + " nothing under the crosshair took "
                        + (1_000.0F - cow.getHealth()));
        holder.aim(cow);
        UltimateLifeformCombat.tickHeld(player, holder.data,
                UltimateLifeformSpec.BLADE_BARRAGE, 4);
        helper.assertTrue(cow.getHealth() < 1_000.0F,
                "Premise: the Blade Barrage did not hit the crosshair mob");
        cow.discard();
        holder.close((short) 32_214);
        helper.succeed();
    }

    /*
     * 1.16 PillarmanUtil.sparkEffect on a landed blade hit, on the server:
     * TrPillarmanParticlesPacket.emitter(LIGHT_SPARK) to everyone tracking the
     * target and to the target. UltimateLifeformBladeSlash.punchPerform
     * (:138) 9 when the hurt landed, UltimateLifeformBladeBarrage.holdTick
     * (:192) 12 for each hit hurtThroughInvulTicks let through,
     * UltimateLifeformBladeDash.Instance.playerTick (:182) 60 once for each
     * newly hit target. The packets are read from a player tracking the
     * target, where PacketDistributor delivers them.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bladeHitsSendTheirLightSparksToTheTargetsTrackers(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesBladeSparks",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f23", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        SparkWatcher watcher = new SparkWatcher(helper.getLevel());
        List<Cow> cows = new ArrayList<>();
        try {
            Cow slashed = cow(helper, new Vec3(2.5D, 2.0D, 4.5D));
            cows.add(slashed);
            watcher.watch(helper, slashed);
            holder.lookAt(slashed);
            holder.aimNothing();
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.BLADE_SLASH, 8);
            holder.aim(slashed);
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.BLADE_SLASH, 8);
            float afterSlash = slashed.getHealth();
            // inside the hurt cooldown the same slash does not land
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.BLADE_SLASH, 8);
            helper.assertTrue(afterSlash < 1_000.0F
                            && slashed.getHealth() == afterSlash,
                    "Premise: one Blade Slash should land, then one not;"
                            + " health " + afterSlash + " then "
                            + slashed.getHealth());
            helper.assertTrue(watcher.sparks.equals(List.of(
                            new TrPillarmanParticlesPacket(
                                    slashed.getId(), 9))),
                    "1.16 sent 9 light sparks for the landed Blade Slash"
                            + " only; the cow's tracker got " + watcher.sparks);
            watcher.sparks.clear();
            slashed.discard();
            Cow barraged = cow(helper, new Vec3(2.5D, 2.0D, 4.5D));
            cows.add(barraged);
            watcher.watch(helper, barraged);
            holder.aim(barraged);
            barraged.setInvulnerable(true);
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.BLADE_BARRAGE, 1);
            barraged.setInvulnerable(false);
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.BLADE_BARRAGE, 2);
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.BLADE_BARRAGE, 3);
            helper.assertTrue(barraged.getHealth() < 1_000.0F,
                    "Premise: the Blade Barrage did not hit the crosshair cow");
            TrPillarmanParticlesPacket barrageSparks =
                    new TrPillarmanParticlesPacket(barraged.getId(), 12);
            helper.assertTrue(watcher.sparks.equals(
                            List.of(barrageSparks, barrageSparks)),
                    "1.16 sent 12 light sparks for each landed Blade Barrage"
                            + " hit (2 of 3 held ticks landed); the cow's"
                            + " tracker got " + watcher.sparks);
            watcher.sparks.clear();
            barraged.discard();
            Cow dashed = cow(helper, new Vec3(2.5D, 2.0D, 3.6D));
            cows.add(dashed);
            watcher.watch(helper, dashed);
            holder.lookAt(dashed);
            holder.aimNothing();
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.BLADE_DASH_ATTACK, 2);
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.BLADE_DASH_ATTACK, 3);
            helper.assertTrue(dashed.getHealth() < 1_000.0F,
                    "Premise: the Blade Dash did not hit the cow in front");
            helper.assertTrue(watcher.sparks.equals(List.of(
                            new TrPillarmanParticlesPacket(
                                    dashed.getId(), 60))),
                    "1.16 sent 60 light sparks once for each target the Blade"
                            + " Dash hit; the cow's tracker got "
                            + watcher.sparks);
        }
        finally {
            watcher.stopWatching();
            cows.forEach(Entity::discard);
        }
        holder.close((short) 32_226);
        helper.succeed();
    }

    /*
     * 1.16 UltimateLifeformBarrage.holdTick: the player's own attack with the
     * target's hurt cooldown put back, then a 3 damage hit under that
     * cooldown, so about one hit in ten ticks; the swing sound for the user on
     * each tick and no start sound; a block broken by the dig rule rang its
     * hit sound, one it could not break stayed silent.
     */
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void ultimateBarrageHitsUnderTheHurtCooldown(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesBarrage",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f12", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 4.5D));
        Sounds sounds = new Sounds(helper);
        float[] afterFirst = new float[1];
        helper.runAfterDelay(1, () -> {
            UltimateLifeformCombat.started(player, holder.data,
                    UltimateLifeformSpec.ULTIMATE_BARRAGE);
            helper.assertTrue(!sounds.anyOf(UltimateLifeformAddon.SOUNDS
                            .get("ultimate_hamon_barrage").get()),
                    "1.16 played no start sound for the Ultimate Barrage;"
                            + " heard " + sounds.heard);
            holder.aim(cow);
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.ULTIMATE_BARRAGE, 1);
            afterFirst[0] = cow.getHealth();
            helper.assertTrue(1_000.0F - afterFirst[0] >= 3.0F,
                    "Premise: the first barrage tick did not hit for 3");
        });
        for (int tick = 2; tick <= 10; tick++) {
            int ticksHeld = tick;
            helper.runAfterDelay(tick, () -> UltimateLifeformCombat.tickHeld(
                    player, holder.data,
                    UltimateLifeformSpec.ULTIMATE_BARRAGE, ticksHeld));
        }
        helper.runAfterDelay(11, () -> {
            try {
                helper.assertTrue(cow.getHealth() == afterFirst[0],
                        "1.16 hit under the target's hurt cooldown: nothing"
                                + " more within ten ticks; the cow went from "
                                + afterFirst[0] + " to " + cow.getHealth());
                long swings = holder.notified.stream()
                        .filter(line -> line.equals(ModSoundEvents
                                .STAND_PUNCH_BARRAGE_SWING.get().getLocation()
                                + " master 1.0 1.0"))
                        .count();
                helper.assertTrue(swings == 10,
                        "1.16 played the barrage swing for the user on each of"
                                + " the 10 held ticks; got " + swings + " in "
                                + holder.notified);
                BlockPos relative = new BlockPos(2, 3, 4);
                cow.discard();
                BlockPos obsidian = holder.aimBlock(helper, relative,
                        Blocks.OBSIDIAN);
                sounds.heard.clear();
                UltimateLifeformCombat.tickHeld(player, holder.data,
                        UltimateLifeformSpec.ULTIMATE_BARRAGE, 11);
                helper.assertTrue(helper.getLevel().getBlockState(obsidian)
                                .is(Blocks.OBSIDIAN)
                                && !sounds.blockHit(obsidian,
                                        Blocks.OBSIDIAN.defaultBlockState()
                                                .getSoundType()),
                        "1.16 left a block the Ultimate Barrage could not"
                                + " break silent; heard " + sounds.heard);
                BlockPos dirt = holder.aimBlock(helper, relative,
                        Blocks.DIRT);
                UltimateLifeformCombat.tickHeld(player, holder.data,
                        UltimateLifeformSpec.ULTIMATE_BARRAGE, 12);
                helper.assertTrue(helper.getLevel().getBlockState(dirt)
                                .isAir()
                                && sounds.blockHit(dirt,
                                        Blocks.DIRT.defaultBlockState()
                                                .getSoundType()),
                        "1.16 broke a soft crosshair block and rang its hit"
                                + " sound; heard " + sounds.heard);
            }
            finally {
                sounds.close();
            }
            Cow inFront = cow(helper, new Vec3(2.5D, 2.0D, 4.5D));
            holder.lookAt(inFront);
            holder.aimNothing();
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.ULTIMATE_BARRAGE, 13);
            helper.assertTrue(inFront.getHealth() == 1_000.0F,
                    "1.16 hit only the crosshair entity; a mob in front with"
                            + " nothing under the crosshair took "
                            + (1_000.0F - inFront.getHealth()));
            inFront.discard();
            holder.close((short) 32_215);
            helper.succeed();
        });
    }

    // 1.16 UltimateLifeformScaleShot.holdTick: every fifth held tick a main
    // hand swing and the shot sound for the user, no sound at the start.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void scaleShotSwingsAndSoundsWithEachScale(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesScaleShot",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f13", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        SoundEvent shot = UltimateLifeformAddon.SOUNDS
                .get("ultimate_lifeform_scale_shot").get();
        try (Sounds sounds = new Sounds(helper)) {
            UltimateLifeformCombat.started(player, holder.data,
                    UltimateLifeformSpec.SCALE_SHOT);
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.SCALE_SHOT, 4);
            helper.assertTrue(!sounds.anyOf(shot)
                            && holder.notified.isEmpty()
                            && !player.swinging,
                    "1.16 played no Scale Shot sound and swung no hand before"
                            + " the first scale; heard " + sounds.heard
                            + " notified " + holder.notified
                            + " swinging=" + player.swinging);
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.SCALE_SHOT, 5);
            helper.assertTrue(player.swinging
                            && holder.notified.contains(
                                    shot.getLocation() + " master 1.0 1.0")
                            && !sounds.anyOf(shot),
                    "1.16 swung the main hand and played the shot sound for"
                            + " the user with each scale; swinging="
                            + player.swinging + " notified " + holder.notified
                            + " heard " + sounds.heard);
        }
        discardScales(helper, player);
        holder.close((short) 32_216);
        helper.succeed();
    }

    // 1.16 played the armor, split shot and teleport sounds on the user's
    // client only (the armors and split shot MASTER 1.0 1.0, the teleport
    // the player's PLAYERS 2.0 1.0).
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void userOnlySoundsReachOnlyTheUser(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesUserSounds",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f14", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        SoundEvent armor = UltimateLifeformAddon.SOUNDS
                .get("ultimate_lifeform_armor").get();
        SoundEvent shot = UltimateLifeformAddon.SOUNDS
                .get("ultimate_lifeform_scale_shot").get();
        try (Sounds sounds = new Sounds(helper)) {
            UltimateLifeformCombat.perform(player, holder.data,
                    UltimateLifeformSpec.ARMOR_PROTECTION, 0);
            UltimateLifeformCombat.perform(player, holder.data,
                    UltimateLifeformSpec.SCALE_SPLIT_SHOT, 0);
            face(player, 0.0F, 0.0F);
            UltimateLifeformCombat.perform(player, holder.data,
                    UltimateLifeformSpec.ENDERMAN_TELEPORT, 0);
            helper.assertTrue(
                    holder.notified.contains(
                                    armor.getLocation() + " master 1.0 1.0")
                            && holder.notified.contains(
                                    shot.getLocation() + " master 1.0 1.0")
                            && holder.notified.contains(
                                    SoundEvents.ENDERMAN_TELEPORT.getLocation()
                                            + " player 2.0 1.0"),
                    "1.16 played the armor, split shot and teleport sounds"
                            + " for the user; notified " + holder.notified);
            helper.assertTrue(!sounds.anyOf(armor) && !sounds.anyOf(shot)
                            && !sounds.anyOf(SoundEvents.ENDERMAN_TELEPORT),
                    "1.16 played them for the user alone; the world heard "
                            + sounds.heard);
        }
        discardScales(helper, player);
        holder.close((short) 32_217);
        helper.succeed();
    }

    // 1.16 UltimateLifeformFlightDash.perform ran on the user's client:
    // (the client's motion + look) * 0.9 and the wing flap for the user.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void flightDashSendsTheClientMotion(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesFlightDash",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f15", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        UltimateLifeformPowerType.enableFlight(player);
        player.startFallFlying();
        face(player, 0.0F, 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = false;
        player.setKnownMovement(new Vec3(0.4D, 0.0D, 0.0D));
        UltimateLifeformCombat.perform(player, holder.data,
                UltimateLifeformSpec.ULTIMATE_FLIGHT_DASH, 0);
        Vec3 expected = new Vec3(0.4D, 0.0D, 1.0D).scale(0.9D);
        helper.assertTrue(player.getDeltaMovement().distanceTo(expected) < EPS
                        && player.hurtMarked,
                "1.16 dashed from the client's motion and the client got it:"
                        + " motion=" + player.getDeltaMovement() + " expected "
                        + expected + " sent=" + player.hurtMarked);
        helper.assertTrue(holder.notified.contains(UltimateLifeformAddon.SOUNDS
                        .get("ultimate_lifeform_wing_flap").get().getLocation()
                        + " player 1.0 1.0"),
                "1.16 played the wing flap for the user; notified "
                        + holder.notified);
        player.stopFallFlying();
        holder.close((short) 32_218);
        helper.succeed();
    }

    /*
     * 1.16 PillarmanAbsorption's constructor called holdType() after the
     * registration's holdType(400), so Absorption had no hold limit; a hurt
     * target farther than 1.5 had its motion replaced by a quarter of the way
     * to the user.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void absorptionHasNoHoldLimitAndSetsThePull(
            GameTestHelper helper) {
        helper.assertTrue(
                UltimateLifeformSpec.ABSORPTION.maxActionTicks() == 0,
                "1.16 held Absorption as long as the key; limit "
                        + UltimateLifeformSpec.ABSORPTION.maxActionTicks());
        Holder holder = holder(helper, "UltimateRulesAbsorbPull",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f16", GameType.SURVIVAL,
                false);
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 4.3D));
        holder.aim(cow);
        cow.setDeltaMovement(0.0D, 0.0D, 3.0D);
        Vec3 pull = holder.player.position().subtract(cow.position())
                .scale(0.25D);
        UltimateLifeformCombat.tickHeld(holder.player, holder.data,
                UltimateLifeformSpec.ABSORPTION, 1);
        helper.assertTrue(cow.getHealth() < 1_000.0F,
                "Premise: Absorption did not drain the cow in reach");
        helper.assertTrue(cow.getDeltaMovement().distanceTo(pull) < EPS,
                "1.16 replaced the absorbed target's motion by " + pull
                        + "; got " + cow.getDeltaMovement());
        cow.discard();
        holder.close((short) 32_219);
        helper.succeed();
    }

    /*
     * 1.16 UltimateLifeformPowerType.tickUser: Regeneration III with its icon
     * while not burning below Y 400, no jump boost; the Caelus flight went
     * away on every tick without a glide, so a glide that ended in the air
     * needed a new leap.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void passivesGiveRegenerationAndAGlideNeedsALeap(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesPassives",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f17", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        player.setOnGround(false);
        player.setRemainingFireTicks(-20);
        holder.data.tick(holder.power);
        MobEffectInstance regeneration =
                player.getEffect(MobEffects.REGENERATION);
        helper.assertTrue(regeneration != null
                        && regeneration.getAmplifier() == 2
                        && regeneration.showIcon()
                        && !player.hasEffect(MobEffects.JUMP),
                "1.16 gave a shown Regeneration III and no jump boost; got "
                        + regeneration + " jump=" + player.hasEffect(
                                MobEffects.JUMP));
        player.removeEffect(MobEffects.REGENERATION);
        player.setRemainingFireTicks(100);
        holder.data.tick(holder.power);
        helper.assertTrue(!player.hasEffect(MobEffects.REGENERATION),
                "1.16 gave no Regeneration while burning");
        player.setRemainingFireTicks(-20);
        holder.power.onLeap();
        holder.data.tick(holder.power);
        helper.assertTrue(player.isFallFlying() && hasUltimateFlight(player),
                "Premise: the leap did not glide");
        player.stopFallFlying();
        player.setOnGround(false);
        holder.data.tick(holder.power);
        helper.assertTrue(!hasUltimateFlight(player)
                        && !holder.data.isFlying(),
                "1.16 took the flight away once the glide ended in the air;"
                        + " flight=" + hasUltimateFlight(player) + " flying="
                        + holder.data.isFlying());
        holder.close((short) 32_220);
        helper.succeed();
    }

    // 1.16 UltimateLifeformData.writeNBT kept no NightVision: a relog found
    // it off, while a death (the data object moved) kept it.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nightVisionIsOffAfterALogin(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesNightVision",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f18", GameType.SURVIVAL,
                false);
        holder.data.toggleNightVision(holder.player);
        CompoundTag saved = holder.data.serializeNBT(
                helper.getLevel().registryAccess());
        UltimateLifeformData respawned = new UltimateLifeformData();
        respawned.deserializeNBT(helper.getLevel().registryAccess(), saved);
        helper.assertTrue(respawned.isNightVision(),
                "1.16 kept Night Vision across a death");
        UltimateLifeformGameEvents.onPlayerLoggedIn(
                new PlayerEvent.PlayerLoggedInEvent(holder.player));
        helper.assertTrue(!holder.data.isNightVision(),
                "1.16 found Night Vision off after a relog");
        holder.close((short) 32_221);
        helper.succeed();
    }

    /*
     * 1.16 SquidTentacleProjectileEntity (OwnerBoundProjectileEntity): bound
     * to the owner, 16 / 42 a tick along the owner's current look for 21
     * ticks, then back to the owner and gone.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void tentaclesFollowTheirOwnerAndRetract(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesTentacles",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f19", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        // a corner of the 8 x 8 test box, looking across it: the full 8
        // blocks stay clear of its barrier walls
        player.setPos(Vec3.atBottomCenterOf(
                helper.absolutePos(new BlockPos(0, 2, 0))));
        face(player, -45.0F, 0.0F);
        UltimateLifeformCombat.perform(player, holder.data,
                UltimateLifeformSpec.TENTACLES, 0);
        List<UltimateProjectileEntity> tentacles = tentacles(helper, player);
        helper.assertTrue(tentacles.size() == 8,
                "Premise: " + tentacles.size() + " tentacles");
        helper.runAfterDelay(10, () -> {
            for (UltimateProjectileEntity tentacle : tentacles) {
                helper.assertTrue(!tentacle.isRemoved()
                                && Math.abs(tentacle.getTentacleDistance()
                                        - 10.0D * 16.0D / 42.0D) < 0.4D
                                && along(tentacle, player) > 0.99D,
                        "1.16 extended a tentacle 16 / 42 a tick along the"
                                + " owner's look: distance="
                                + tentacle.getTentacleDistance() + " along="
                                + along(tentacle, player));
            }
            face(player, -30.0F, 0.0F);
        });
        helper.runAfterDelay(12, () -> {
            for (UltimateProjectileEntity tentacle : tentacles) {
                helper.assertTrue(along(tentacle, player) > 0.99D,
                        "1.16 kept a tentacle bound to its owner's look; it"
                                + " did not follow the turn: along="
                                + along(tentacle, player));
            }
        });
        helper.runAfterDelay(19, () -> {
            for (UltimateProjectileEntity tentacle : tentacles) {
                helper.assertTrue(!tentacle.isTentacleRetracting()
                                && tentacle.getTentacleDistance() > 6.5D,
                        "Premise: a tentacle met something before its full"
                                + " length: retracting="
                                + tentacle.isTentacleRetracting()
                                + " distance=" + tentacle.getTentacleDistance());
            }
        });
        helper.runAfterDelay(25, () -> {
            for (UltimateProjectileEntity tentacle : tentacles) {
                helper.assertTrue(tentacle.isTentacleRetracting()
                                && tentacle.getTentacleDistance() < 7.9D,
                        "1.16 retracted a tentacle after 21 ticks: retracting="
                                + tentacle.isTentacleRetracting()
                                + " distance=" + tentacle.getTentacleDistance());
            }
        });
        helper.runAfterDelay(48, () -> {
            helper.assertTrue(tentacles.stream()
                            .allMatch(Entity::isRemoved),
                    "1.16 removed the tentacles back at their owner");
            holder.close((short) 32_222);
            helper.succeed();
        });
    }

    // 1.16 SquidTentacleProjectileEntity: knockbackMultiplier 0, and the
    // stun on an attached target with its particles (new EffectInstance).
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void tentacleHitStunsWithoutKnockback(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesTentacleHit",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f20", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        face(player, 0.0F, 0.0F);
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 5.5D));
        // the stun is no effect for a mob without AI (StunEffect.isApplicable)
        cow.setNoAi(false);
        cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
        cow.setDeltaMovement(Vec3.ZERO);
        // the stun stops the target at once, so the push is read where the
        // knockback is applied
        List<Float> pushes = new ArrayList<>();
        Consumer<LivingKnockBackEvent> knockback = event -> {
            if (event.getEntity() == cow && !event.isCanceled()
                    && event.getStrength() > 0.0F) {
                pushes.add(event.getStrength());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                LivingKnockBackEvent.class, knockback);
        UltimateLifeformCombat.perform(player, holder.data,
                UltimateLifeformSpec.TENTACLES, 0);
        List<UltimateProjectileEntity> tentacles = tentacles(helper, player);
        helper.runAfterDelay(14, () -> {
            NeoForge.EVENT_BUS.unregister(knockback);
            MobEffectInstance stun = cow.getEffect(ModStatusEffects.STUN);
            helper.assertTrue(cow.getHealth() < 1_000.0F,
                    "Premise: no tentacle hit the cow in front");
            helper.assertTrue(pushes.isEmpty(),
                    "1.16 tentacles pushed nothing; knockbacks " + pushes);
            helper.assertTrue(stun != null && stun.isVisible(),
                    "1.16 stunned the attached target with particles; got "
                            + stun + " effects " + cow.getActiveEffects()
                            + " attached " + tentacles.stream()
                                    .filter(UltimateProjectileEntity::isAttached)
                                    .count());
            helper.assertTrue(tentacles.stream().anyMatch(tentacle ->
                            tentacle.isAttached()
                                    && tentacle.getLifeTicks() == 62),
                    "1.16 attached a hitting tentacle for 20 more ticks");
            tentacles.forEach(Entity::discard);
            cow.discard();
            holder.close((short) 32_223);
            helper.succeed();
        });
    }

    // 1.16 UltimateLifeformWallClimb: the press and the perform took the
    // crosshair block, no longer ray behind it.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void wallClimbTakesTheCrosshairBlock(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateRulesWallClimb",
                "a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f21", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        for (int y = 2; y <= 4; y++) {
            helper.getLevel().setBlockAndUpdate(
                    helper.absolutePos(new BlockPos(3, y, 2)),
                    Blocks.STONE.defaultBlockState());
        }
        face(player, -90.0F, 0.0F);
        holder.aimNothing();
        Ability ability = holder.power.getAbility(
                UltimateLifeformSpec.WALL_CLIMBING.id);
        helper.assertTrue(ability != null, "Premise: no wall climbing");
        ConditionCheck press = ability.checkConditions(holder.power);
        helper.assertTrue(!press.isPositive() && isWarning(press, "wall"),
                "1.16 asked for the crosshair block: nothing under it is no"
                        + " wall; got " + (press.isPositive() ? "admitted"
                                : String.valueOf(press.getWarning())));
        UltimateLifeformCombat.perform(player, holder.data,
                UltimateLifeformSpec.WALL_CLIMBING, 0);
        helper.assertTrue(!holder.data.isWallClimbing(),
                "1.16 climbed only the crosshair block; it gripped a wall"
                        + " with nothing under the crosshair");
        LivingComponentAction.getComponent(player).entityAim.setTarget(
                new ActionTarget(helper.absolutePos(new BlockPos(3, 3, 2)),
                        Direction.WEST));
        UltimateLifeformCombat.perform(player, holder.data,
                UltimateLifeformSpec.WALL_CLIMBING, 0);
        helper.assertTrue(holder.data.isWallClimbing()
                        && holder.data.getWallFace() == Direction.WEST,
                "Premise: the aimed wall was not gripped");
        holder.data.stopWallClimbing(player);
        holder.close((short) 32_224);
        helper.succeed();
    }

    private static double along(UltimateProjectileEntity tentacle,
            ServerPlayer owner) {
        Vec3 reach = tentacle.position().subtract(
                tentacle.getTentacleOrigin(1.0F));
        return reach.lengthSqr() < 1.0E-6D
                ? 0.0D
                : reach.normalize().dot(owner.getLookAngle());
    }

    private static List<UltimateProjectileEntity> tentacles(
            GameTestHelper helper, ServerPlayer owner) {
        return helper.getLevel().getEntitiesOfClass(
                UltimateProjectileEntity.class,
                owner.getBoundingBox().inflate(16.0D),
                tentacle -> tentacle.isTentacle()
                        && tentacle.getOwner() == owner);
    }

    private static void discardScales(GameTestHelper helper,
            ServerPlayer owner) {
        helper.getLevel().getEntitiesOfClass(
                        UltimateScaleProjectileEntity.class,
                        owner.getBoundingBox().inflate(64.0D))
                .forEach(Entity::discard);
    }

    private static boolean hasUltimateFlight(ServerPlayer player) {
        AttributeInstance flight = player.getAttribute(
                com.illusivesoulworks.caelus.api.CaelusApi.getInstance()
                        .getFallFlyingAttribute());
        return flight != null && flight.hasModifier(
                UltimateLifeformPowerType.FLIGHT_MODIFIER_ID);
    }

    private static void face(ServerPlayer player, float yRot, float xRot) {
        player.setYRot(yRot);
        player.yRotO = yRot;
        player.setYHeadRot(yRot);
        player.yHeadRotO = yRot;
        player.setYBodyRot(yRot);
        player.yBodyRotO = yRot;
        player.setXRot(xRot);
        player.xRotO = xRot;
    }

    private static Arrow arrow(GameTestHelper helper, Vec3 at, Vec3 motion) {
        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        arrow.setNoGravity(true);
        arrow.setPos(at);
        arrow.setDeltaMovement(motion);
        helper.assertTrue(helper.getLevel().addFreshEntity(arrow),
                "Premise: the arrow could not be added");
        return arrow;
    }

    private static Cow cow(GameTestHelper helper, Vec3 relative) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        if (cow == null) {
            throw new IllegalStateException("Cow fixture failed");
        }
        Vec3 position = Vec3.atBottomCenterOf(helper.absolutePos(
                BlockPos.ZERO)).add(
                        relative.x - 0.5D, relative.y, relative.z - 0.5D);
        cow.setPos(position);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000.0D);
        cow.setHealth(1_000.0F);
        helper.getLevel().addFreshEntity(cow);
        return cow;
    }

    private static boolean isWarning(ConditionCheck check, String postfix) {
        return check.getWarning() != null
                && check.getWarning().getContents()
                        instanceof TranslatableContents contents
                && ("jojo.message.action_condition." + postfix)
                        .equals(contents.getKey());
    }

    private static Holder holder(GameTestHelper helper, String name,
            String uuid, GameType gameType, boolean hurtable) {
        List<Component> messages = new ArrayList<>();
        List<String> notified = new ArrayList<>();
        ServerPlayer player = new FakePlayer(
                helper.getLevel(),
                new GameProfile(UUID.fromString(uuid), name)) {
            @Override
            public void displayClientMessage(
                    Component message, boolean overlay) {
                if (overlay) {
                    messages.add(message);
                }
            }

            // reach Player.hurt the way a real player does
            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return !hurtable && super.isInvulnerableTo(source);
            }
        };
        // the sounds sent to this player alone
        UserSoundRecorder.attach(player, heard -> notified.add(heard.line()));
        if (hurtable) {
            try {
                java.lang.reflect.Field spawnInvulnerable = ServerPlayer.class
                        .getDeclaredField("spawnInvulnerableTime");
                spawnInvulnerable.setAccessible(true);
                spawnInvulnerable.setInt(player, 0);
            }
            catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(
                        "Premise: no ServerPlayer.spawnInvulnerableTime",
                        exception);
            }
        }
        player.setPos(Vec3.atBottomCenterOf(
                helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setGameMode(gameType);
        if (!helper.getLevel().addFreshEntity(player)) {
            throw new IllegalStateException(
                    "Rules test player could not be added: " + name);
        }
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
        if (!power.trySetPowerType(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM.get())) {
            throw new IllegalStateException(
                    "Rules test power grant failed: " + name);
        }
        power.updateAvailableMoves();
        UltimateLifeformData data = power.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        return new Holder(player, power, data, messages, notified);
    }

    private record Holder(
            ServerPlayer player,
            PlayerPower power,
            UltimateLifeformData data,
            List<Component> messages,
            List<String> notified) {
        // whether the click went through at once (not refused, not
        // waiting in the input buffer)
        boolean tryPress(UltimateLifeformSpec spec, short key) {
            return input(spec, key, InputMethod.CLICK);
        }

        void press(GameTestHelper helper, UltimateLifeformSpec spec,
                short key) {
            helper.assertTrue(input(spec, key, InputMethod.HOLD),
                    "Premise: the " + spec.id + " press was refused: "
                            + messages);
        }

        void click(GameTestHelper helper, UltimateLifeformSpec spec,
                short key) {
            helper.assertTrue(input(spec, key, InputMethod.CLICK),
                    "Premise: the " + spec.id + " click was refused: "
                            + messages);
            AbilityInput.keyRelease(key, player);
        }

        private boolean input(UltimateLifeformSpec spec, short key,
                InputMethod method) {
            Ability ability = power.getAbility(spec.id);
            if (ability == null) {
                throw new IllegalStateException("No " + spec.id);
            }
            BufferingState buffering = BufferingState.clickCanBuffer();
            FriendlyByteBuf extra = new FriendlyByteBuf(Unpooled.buffer());
            try {
                Object held = AbilityInput.keyPress(key, ability, player,
                        extra, method, 0.0F, buffering,
                        ability.getAbilityId());
                return !buffering.isBuffered()
                        && (held != null || buffering.isActionSuccess);
            }
            finally {
                extra.release();
            }
        }

        void aim(Entity target) {
            lookAt(target);
            LivingComponentAction.getComponent(player)
                    .entityAim.setTarget(new ActionTarget(target));
        }

        void lookAt(Entity target) {
            player.lookAt(EntityAnchorArgument.Anchor.EYES,
                    target.getBoundingBox().getCenter());
            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
            player.setYBodyRot(player.getYRot());
            player.yBodyRotO = player.getYRot();
        }

        void aimNothing() {
            LivingComponentAction.getComponent(player)
                    .entityAim.setTarget(ActionTarget.EMPTY);
        }

        // a block at the relative position, aimed at from its north face
        BlockPos aimBlock(GameTestHelper helper, BlockPos relative,
                Block block) {
            BlockPos pos = helper.absolutePos(relative);
            helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
            player.lookAt(EntityAnchorArgument.Anchor.EYES,
                    Vec3.atCenterOf(pos));
            LivingComponentAction.getComponent(player).entityAim.setTarget(
                    new ActionTarget(pos, Direction.NORTH));
            return pos;
        }

        void close(short key) {
            AbilityInput.keyRelease(key, player);
            power.setPowerType(null);
            player.discard();
        }
    }

    // a player that only tracks the entities given to watch() and records the
    // Pillar Man spark packets the server sends it
    private static final class SparkWatcher extends FakePlayer {
        private final List<TrPillarmanParticlesPacket> sparks =
                new ArrayList<>();
        private final List<ChunkMap.TrackedEntity> tracking =
                new ArrayList<>();

        SparkWatcher(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(),
                    "UltimateSparkWatcher"));
            Connection channel = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(channel);
            this.connection = new ServerGamePacketListenerImpl(
                    level.getServer(), channel, this,
                    CommonListenerCookie.createInitial(
                            getGameProfile(), false)) {
                @Override
                public void send(Packet<?> packet) {
                    record(packet);
                }

                @Override
                public void send(Packet<?> packet,
                        @Nullable PacketSendListener listener) {
                    record(packet);
                }
            };
        }

        private void record(Packet<?> packet) {
            if (packet instanceof ClientboundBundlePacket bundle) {
                bundle.subPackets().forEach(this::record);
            }
            else if (packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload()
                            instanceof TrPillarmanParticlesPacket spark) {
                sparks.add(spark);
            }
        }

        void watch(GameTestHelper helper, Entity entity) {
            ChunkMap.TrackedEntity tracked = helper.getLevel()
                    .getChunkSource().chunkMap.entityMap.get(entity.getId());
            helper.assertTrue(tracked != null,
                    "Premise: the entity is not tracked: " + entity);
            tracked.seenBy.add(connection);
            tracking.add(tracked);
        }

        void stopWatching() {
            tracking.forEach(tracked -> tracked.seenBy.remove(connection));
            tracking.clear();
        }
    }

    // sounds played into the world (not those sent to one player)
    private static final class Sounds implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<Heard> heard = new ArrayList<>();
        private final Consumer<PlayLevelSoundEvent.AtEntity> atEntity;
        private final Consumer<PlayLevelSoundEvent.AtPosition> atPosition;

        private record Heard(ResourceLocation sound, Entity entity,
                Vec3 position, float volume, float pitch) {}

        Sounds(GameTestHelper helper) {
            this.helper = helper;
            // only this test's own 8 x 8 x 8 box of the shared test level
            Vec3 origin = Vec3.atLowerCornerOf(
                    helper.absolutePos(new BlockPos(4, 4, 4)));
            atEntity = event -> {
                if (event.getLevel() == helper.getLevel()
                        && event.getSound() != null
                        && event.getEntity().position()
                                .distanceToSqr(origin) < 49.0D) {
                    heard.add(new Heard(
                            event.getSound().value().getLocation(),
                            event.getEntity(), event.getEntity().position(),
                            event.getOriginalVolume(),
                            event.getOriginalPitch()));
                }
            };
            atPosition = event -> {
                if (event.getLevel() == helper.getLevel()
                        && event.getSound() != null
                        && event.getPosition().distanceToSqr(origin)
                                < 49.0D) {
                    heard.add(new Heard(
                            event.getSound().value().getLocation(), null,
                            event.getPosition(), event.getOriginalVolume(),
                            event.getOriginalPitch()));
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                    PlayLevelSoundEvent.AtEntity.class, atEntity);
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                    PlayLevelSoundEvent.AtPosition.class, atPosition);
        }

        boolean atEntity(SoundEvent sound, Entity entity, float volume,
                float pitch) {
            return heard.stream().anyMatch(h ->
                    h.sound.equals(sound.getLocation()) && h.entity == entity
                            && h.volume == volume && h.pitch == pitch);
        }

        boolean atPosition(SoundEvent sound, Vec3 position, float volume,
                float pitch) {
            return heard.stream().anyMatch(h ->
                    h.sound.equals(sound.getLocation()) && h.entity == null
                            && h.position.distanceToSqr(position) < 1.0E-4D
                            && h.volume == volume && h.pitch == pitch);
        }

        boolean blockHit(BlockPos pos, SoundType type) {
            return heard.stream().anyMatch(h ->
                    h.sound.equals(type.getHitSound().getLocation())
                            && h.entity == null
                            && h.position.distanceToSqr(Vec3.atCenterOf(pos))
                                    < 1.0E-4D);
        }

        boolean anyOf(SoundEvent sound) {
            return heard.stream().anyMatch(h ->
                    h.sound.equals(sound.getLocation()));
        }

        @Override
        public void close() {
            NeoForge.EVENT_BUS.unregister(atEntity);
            NeoForge.EVENT_BUS.unregister(atPosition);
        }
    }
}
