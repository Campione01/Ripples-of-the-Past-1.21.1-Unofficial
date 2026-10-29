package rotp.addons.ultimate_lifeform;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAbility;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformCombat;
import rotp.addons.ultimate_lifeform.entity.UltimatePhantomEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateRabbitEntity;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.playerpower.PlayerPower;

/**
 * 1.16 rules of the Pillar Man modes, the summons, the passive and power
 * rules of the Ultimate Lifeform restored in wave 28 (UC3 compare).
 */
@GameTestHolder(UltimateLifeformAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimatePillarRulesGameTests {
    private UltimatePillarRulesGameTests() {}

    // 1.16 UltimateLifeformSmallSandstorm.perform, DivineSandstorm and
    // AtmosphericRift.holdTick: MAGICIANS_RED_FIRE_BLAST (AMBIENT) at the
    // user with each wave, 0.2 for the sandstorms, 0.1 for the rift.
    @GameTest(template = "empty", timeoutTicks = 30, batch = "ulf_own_sandstorm_waves_play_the_fire_blast")
    public static void sandstormWavesPlayTheFireBlast(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimatePillarWaves",
                "b2d1e3f5-1c2e-4d3f-9a4b-6c7d8e9f0a01", GameType.CREATIVE,
                false);
        holder.data.setPreviousMode(holder.player, PillarmanMode.WIND);
        Sounds sounds = new Sounds(helper);
        helper.runAfterDelay(1, () -> {
            Vec3 at = holder.player.position();
            UltimateLifeformCombat.perform(holder.player, holder.data,
                    UltimateLifeformSpec.SMALL_SANDSTORM, 0);
            boolean small = sounds.atPosition(
                    ModSoundEvents.MAGICIANS_RED_FIRE_BLAST.get(), at,
                    0.2F, 1.0F);
            int before = sounds.count(
                    ModSoundEvents.MAGICIANS_RED_FIRE_BLAST.get());
            UltimateLifeformCombat.tickHeld(holder.player, holder.data,
                    UltimateLifeformSpec.DIVINE_SANDSTORM, 42);
            boolean divine = sounds.count(
                            ModSoundEvents.MAGICIANS_RED_FIRE_BLAST.get())
                    == before + 1
                    && sounds.atPosition(
                            ModSoundEvents.MAGICIANS_RED_FIRE_BLAST.get(),
                            at, 0.2F, 1.0F);
            UltimateLifeformCombat.tickHeld(holder.player, holder.data,
                    UltimateLifeformSpec.ATMOSPHERIC_RIFT, 42);
            boolean rift = sounds.atPosition(
                    ModSoundEvents.MAGICIANS_RED_FIRE_BLAST.get(), at,
                    0.1F, 1.0F);
            sounds.close();
            helper.assertTrue(small && divine && rift,
                    "1.16 played the fire blast with each sandstorm wave"
                            + " (0.2) and rift wave (0.1); small=" + small
                            + " divine=" + divine + " rift=" + rift + " heard "
                            + sounds.heard);
            holder.close();
            helper.succeed();
        });
    }

    // 1.16 UltimateLifeformOverwhelmingDespair.perform: the awakening for
    // everyone (3.0, 1.0); the user's own summons get Speed II, Strength and
    // Resistance; its tamed animals are spared, teammates are not; another
    // user's killer rabbit is no passive mob, a cow is (600 ticks and Stun).
    // Its own batch: the 32-block cube reaches the mobs of the plots run
    // beside it (scaleHitPoisonsOnlyAboveTheDonorRoll's cows).
    @GameTest(template = "empty", timeoutTicks = 30,
            batch = "ulf_overwhelming_despair")
    public static void overwhelmingDespairSparesOwnPetsNotTeammates(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimatePillarDespair",
                "b2d1e3f5-1c2e-4d3f-9a4b-6c7d8e9f0a02", GameType.SURVIVAL,
                false);
        Holder mate = holder(helper, "UltimatePillarMate",
                "b2d1e3f5-1c2e-4d3f-9a4b-6c7d8e9f0a03", GameType.SURVIVAL,
                false);
        Holder other = holder(helper, "UltimatePillarOther",
                "b2d1e3f5-1c2e-4d3f-9a4b-6c7d8e9f0a04", GameType.SURVIVAL,
                false);
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        String teamName = "ulfDespair" + UUID.randomUUID()
                .toString().substring(0, 8);
        PlayerTeam team = scoreboard.addPlayerTeam(teamName);
        scoreboard.addPlayerToTeam(
                holder.player.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(mate.player.getScoreboardName(), team);
        UltimateRabbitEntity own = rabbit(helper, holder.player,
                new Vec3(1.5D, 2.0D, 1.5D));
        UltimateRabbitEntity foreign = rabbit(helper, other.player,
                new Vec3(3.5D, 2.0D, 1.5D));
        Wolf wolf = EntityType.WOLF.create(helper.getLevel());
        wolf.setPos(at(helper, new Vec3(1.5D, 2.0D, 3.5D)));
        wolf.tame(holder.player);
        helper.getLevel().addFreshEntity(wolf);
        Cow cow = cow(helper, new Vec3(3.5D, 2.0D, 3.5D));
        Sounds sounds = new Sounds(helper);
        helper.runAfterDelay(1, () -> {
            UltimateLifeformCombat.perform(holder.player, holder.data,
                    UltimateLifeformSpec.OVERWHELMING_DESPAIR, 0);
            sounds.close();
            scoreboard.removePlayerTeam(team);
            helper.assertTrue(sounds.atEntity(
                            ModSoundEvents.PILLAR_MAN_AWAKENING.get(),
                            holder.player, 3.0F, 1.0F),
                    "1.16 played the awakening (3.0, 1.0) for everyone;"
                            + " heard " + sounds.heard);
            helper.assertTrue(own.hasEffect(MobEffects.DAMAGE_RESISTANCE)
                            && !own.hasEffect(MobEffects.JUMP)
                            && amplifier(own, MobEffects.MOVEMENT_SPEED) == 1
                            && amplifier(own, MobEffects.DAMAGE_BOOST) == 0
                            && !own.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "1.16 gave the user's own rabbit Speed II, Strength and"
                            + " Resistance (no Jump Boost); it has "
                            + own.getActiveEffects());
            helper.assertTrue(!wolf.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "1.16 spared the user's own tamed wolf; it has "
                            + wolf.getActiveEffects());
            helper.assertTrue(duration(mate.player,
                            MobEffects.MOVEMENT_SLOWDOWN) == 200
                            && amplifier(mate.player,
                                    MobEffects.MOVEMENT_SLOWDOWN) == 2,
                    "1.16 had no team rule: the teammate was slowed for 200"
                            + " ticks; it has "
                            + mate.player.getActiveEffects());
            helper.assertTrue(duration(foreign,
                            MobEffects.MOVEMENT_SLOWDOWN) == 200
                            && !ModStatusEffects.isStunned(foreign),
                    "1.16 counted another user's killer rabbit as no passive"
                            + " mob (200 ticks, no Stun); it has "
                            + foreign.getActiveEffects());
            helper.assertTrue(duration(cow,
                            MobEffects.MOVEMENT_SLOWDOWN) == 600
                            && ModStatusEffects.isStunned(cow),
                    "1.16 slowed and stunned a passive cow for 600 ticks;"
                            + " it has " + cow.getActiveEffects());
            own.discard();
            foreign.discard();
            wolf.discard();
            cow.discard();
            holder.close();
            mate.close();
            other.close();
            helper.succeed();
        });
    }

    // 1.16 UltimateLifeformPhantomEntity.isSunBurnTick returned false: the
    // summoned phantoms never burned by day, where a vanilla one does.
    @GameTest(template = "empty", timeoutTicks = 20, skyAccess = true)
    public static void summonedPhantomNeverBurnsByDay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 sky = at(helper, new Vec3(2.5D, 3.0D, 2.5D));
        Phantom vanilla = EntityType.PHANTOM.create(level);
        UltimatePhantomEntity summoned =
                UltimateLifeformAddon.ULTIMATE_PHANTOM.get().create(level);
        vanilla.setPos(sky);
        summoned.setPos(sky);
        boolean vanillaBurns = false;
        boolean summonedBurns = false;
        try {
            Method burn = Mob.class.getDeclaredMethod("isSunBurnTick");
            burn.setAccessible(true);
            for (int i = 0; i < 500; i++) {
                vanillaBurns |= (Boolean) burn.invoke(vanilla);
                summonedBurns |= (Boolean) burn.invoke(summoned);
            }
        }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Premise: no Mob.isSunBurnTick", exception);
        }
        helper.assertTrue(vanillaBurns,
                "Premise: a vanilla phantom under the sky did not burn"
                        + " (day=" + level.isDay() + ")");
        helper.assertTrue(!summonedBurns,
                "1.16 summoned phantoms never burned by day; this one"
                        + " burned where a vanilla one did");
        helper.succeed();
    }

    // 1.16 NonStandPower.onNewPowerGiven left a new Ultimate Lifeform with no
    // energy (full in creative); UltimateLifeformData.onPowerGiven kept the
    // mode of a Pillar Man; keepOnDeath never looked at the dimension.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void newPowerStartsEmptyKeepsThePillarModeAndTheEnd(
            GameTestHelper helper) {
        ServerPlayer survivor = player(helper, "UltimatePillarGrant",
                "b2d1e3f5-1c2e-4d3f-9a4b-6c7d8e9f0a05", GameType.SURVIVAL,
                false);
        ServerPlayer creative = player(helper, "UltimatePillarCreative",
                "b2d1e3f5-1c2e-4d3f-9a4b-6c7d8e9f0a06", GameType.CREATIVE,
                false);
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(survivor);
        helper.assertTrue(power.trySetPowerType(
                        ModPlayerPowers.PILLAR_MAN.get()),
                "Premise: the Pillar Man grant failed");
        PillarmanData pillarman = PlayerPower.getPowerData(survivor,
                ModPlayerPowers.PILLAR_MAN).orElseThrow();
        pillarman.setMode(PillarmanMode.HEAT, survivor);
        power.setPowerType(UltimateLifeformAddon.ULTIMATE_LIFEFORM.get());
        UltimateLifeformData data = power.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        PlayerPower creativePower =
                PowerClass.PLAYER_POWER.attachGet(creative);
        helper.assertTrue(creativePower.trySetPowerType(
                        UltimateLifeformAddon.ULTIMATE_LIFEFORM.get()),
                "Premise: the creative grant failed");
        UltimateLifeformData creativeData = creativePower.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        helper.assertTrue(data.getEnergy() == 0.0F
                        && creativeData.getEnergy()
                                == UltimateLifeformData.MAX_ENERGY,
                "1.16 gave a new Ultimate Lifeform no energy (full in"
                        + " creative); survival " + data.getEnergy()
                        + " creative " + creativeData.getEnergy());
        helper.assertTrue(data.getPreviousMode() == PillarmanMode.HEAT,
                "1.16 kept the Pillar Man's mode when the power changed; got "
                        + data.getPreviousMode());
        ServerLevel end = level(helper, Level.END);
        FakePlayer inTheEnd = new FakePlayer(end, new GameProfile(
                UUID.fromString("b2d1e3f5-1c2e-4d3f-9a4b-6c7d8e9f0a07"),
                "UltimatePillarEnd"));
        inTheEnd.setPos(0.5D, 64.0D, 0.5D);
        PlayerPower endPower = PowerClass.PLAYER_POWER.attachGet(inTheEnd);
        helper.assertTrue(UltimateLifeformAddon.ULTIMATE_LIFEFORM.get()
                        .keepOnDeath(endPower),
                "1.16 kept the power on a death in the End (its dimension"
                        + " test was never true)");
        power.setPowerType(null);
        creativePower.setPowerType(null);
        survivor.discard();
        creative.discard();
        helper.succeed();
    }

    // 1.16 UltimateLifeformAbsoluteImmortality.effectLevel: its own step
    // counter, not the Absorption the user has: an Absorption from elsewhere
    // is replaced by the first step, a full stack keeps refusing at full
    // health after the Absorption is gone, a healing tick starts over.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void absoluteImmortalityCountsItsOwnSteps(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimatePillarImmortal",
                "b2d1e3f5-1c2e-4d3f-9a4b-6c7d8e9f0a08", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        UltimateLifeformAbility ability = (UltimateLifeformAbility)
                holder.power.getAbility(
                        UltimateLifeformSpec.ABSOLUTE_IMMORTALITY.id);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0D);
        player.setHealth(player.getMaxHealth());
        player.addEffect(new MobEffectInstance(
                MobEffects.ABSORPTION, 2400, 3));
        UltimateLifeformCombat.tickHeld(player, holder.data,
                UltimateLifeformSpec.ABSOLUTE_IMMORTALITY, 5);
        int first = amplifier(player, MobEffects.ABSORPTION);
        for (int step = 2; step <= 5; step++) {
            player.setHealth(player.getMaxHealth());
            UltimateLifeformCombat.tickHeld(player, holder.data,
                    UltimateLifeformSpec.ABSOLUTE_IMMORTALITY, step * 5);
        }
        int fifth = amplifier(player, MobEffects.ABSORPTION);
        player.removeEffect(MobEffects.ABSORPTION);
        player.setHealth(player.getMaxHealth());
        ConditionCheck stacked = ability.checkSpecificConditions(
                holder.power);
        player.setHealth(player.getMaxHealth() - 10.0F);
        UltimateLifeformCombat.tickHeld(player, holder.data,
                UltimateLifeformSpec.ABSOLUTE_IMMORTALITY, 30);
        player.setHealth(player.getMaxHealth());
        UltimateLifeformCombat.tickHeld(player, holder.data,
                UltimateLifeformSpec.ABSOLUTE_IMMORTALITY, 35);
        int restarted = amplifier(player, MobEffects.ABSORPTION);
        ConditionCheck afterHeal = ability.checkSpecificConditions(
                holder.power);
        helper.assertTrue(first == 1,
                "1.16 replaced an Absorption IV from elsewhere with its first"
                        + " step (II); got amplifier " + first);
        helper.assertTrue(fifth == 5 && isWarning(stacked,
                        "absolute_immortality"),
                "1.16 refused a press at full health after a full stack even"
                        + " with the Absorption gone; fifth=" + fifth
                        + " check=" + stacked.getWarning());
        helper.assertTrue(restarted == 1 && afterHeal.isPositive(),
                "1.16 started the steps over after a healing tick; got"
                        + " amplifier " + restarted + " check "
                        + afterHeal.getWarning());
        holder.close();
        helper.succeed();
    }

    // 1.16 GameplayEventHandler.onPlayerTick above Y 256: Freeze IV and 25
    // damage of ULF_FLY_TOO_HIGH (bypassArmor, bypassMagic: Resistance does
    // not lower it, no source entity, no knockback, its death message).
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void heightLimitHurtsWithItsOwnDamageType(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimatePillarHeight",
                "b2d1e3f5-1c2e-4d3f-9a4b-6c7d8e9f0a09", GameType.SURVIVAL,
                true);
        ServerPlayer player = holder.player;
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000.0D);
        player.setHealth(1_000.0F);
        player.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE, 200, 3));
        Vec3 start = player.position();
        player.setPos(start.x, 300.0D, start.z);
        UltimateLifeformGameEvents.applyHeightLimit(player);
        float lost = 1_000.0F - player.getHealth();
        DamageSource source = player.getLastDamageSource();
        player.setPos(start);
        helper.assertTrue(source != null
                        && source.typeHolder().is(
                                UltimateLifeformGameEvents.ULF_FLY_TOO_HIGH)
                        && source.getEntity() == null
                        && source.is(DamageTypeTags.BYPASSES_ARMOR)
                        && source.is(DamageTypeTags.BYPASSES_EFFECTS)
                        && source.is(DamageTypeTags.NO_KNOCKBACK)
                        && !source.is(
                                DamageTypeTags.BYPASSES_INVULNERABILITY),
                "1.16 hurt with ULF_FLY_TOO_HIGH (armor and effects"
                        + " bypassed, no knockback, not past creative); got "
                        + (source == null ? null : source.typeHolder()));
        helper.assertTrue(Math.abs(lost - 25.0F) < 1.0E-3F,
                "1.16 dealt the full 25 through Resistance IV; lost " + lost);
        helper.assertTrue(source.getLocalizedDeathMessage(player)
                                .getContents()
                        instanceof TranslatableContents contents
                        && "death.attack.ulf_fly_too_high".equals(
                                contents.getKey()),
                "1.16 died 'turned to stone and stopped thinking'; got "
                        + source.getLocalizedDeathMessage(player));
        helper.assertTrue(amplifier(player, ModStatusEffects.FREEZE) == 3,
                "1.16 froze the user (Freeze IV)");
        holder.close();
        helper.succeed();
    }

    // 1.16 UltimateLifeformData.writeNBT kept no healthDebuff: after a relog
    // the maximum health lost to dead summons was back (a dimension change
    // moved the data and kept it).
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void healthDebuffIsGoneAfterARelog(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimatePillarRelog",
                "b2d1e3f5-1c2e-4d3f-9a4b-6c7d8e9f0a10", GameType.SURVIVAL,
                false);
        holder.data.summonedRabbitDied();
        holder.data.summonedPhantomDied();
        CompoundTag saved = holder.data.serializeNBT(
                helper.getLevel().registryAccess());
        UltimateLifeformData moved = new UltimateLifeformData();
        moved.deserializeNBT(helper.getLevel().registryAccess(), saved);
        helper.assertTrue(holder.data.getHealthDebuff() == 3
                        && moved.getHealthDebuff() == 3,
                "Premise: two dead summons left a debuff of 3 that a"
                        + " dimension clone carries; got "
                        + holder.data.getHealthDebuff() + " / "
                        + moved.getHealthDebuff());
        UltimateLifeformGameEvents.onPlayerLoggedIn(
                new PlayerEvent.PlayerLoggedInEvent(holder.player));
        helper.assertTrue(holder.data.getHealthDebuff() == 0,
                "1.16 lost the health debuff on a relog; it is "
                        + holder.data.getHealthDebuff());
        holder.close();
        helper.succeed();
    }

    // 1.16 PillarmanLightFlash / PillarmanLightFlashDecoy.perform:
    // user.playSound(AJA_STONE_BEAM, (16 + 16) / 16 = 2.0, 1.0) on both
    // sides, heard by everyone
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lightFlashAndDecoySoundTheStoneBeam(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimatePillarFlash",
                "b2d1e3f5-1c2e-4d3f-9a4b-6c7d8e9f0a11", GameType.SURVIVAL,
                false);
        Sounds sounds = new Sounds(helper);
        helper.runAfterDelay(1, () -> {
            UltimateLifeformCombat.perform(holder.player, holder.data,
                    UltimateLifeformSpec.LIGHT_FLASH, 40);
            boolean flash = sounds.atEntity(
                    ModSoundEvents.AJA_STONE_BEAM.get(), holder.player,
                    2.0F, 1.0F);
            UltimateLifeformCombat.perform(holder.player, holder.data,
                    UltimateLifeformSpec.LIGHT_FLASH_DECOY, 50);
            int beams = sounds.count(ModSoundEvents.AJA_STONE_BEAM.get());
            sounds.close();
            helper.assertTrue(flash && beams == 2,
                    "1.16 sounded the stone beam (2.0, 1.0) for the Light"
                            + " Flash and its decoy; flash=" + flash
                            + " beams=" + beams + " heard " + sounds.heard);
            holder.close();
            helper.succeed();
        });
    }

    // 1.16 KillerRabbitsSummon / PhantomSummon: startedHolding played the
    // summon sound (2.0, 1.0) on the presser's client alone, perform played
    // it with playLocalSound (MASTER, 1.0, 1.0) on every client that ran it
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void summonChargeAndFireSoundAsIn116(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimatePillarSummon",
                "b2d1e3f5-1c2e-4d3f-9a4b-6c7d8e9f0a12", GameType.SURVIVAL,
                false);
        Sounds sounds = new Sounds(helper);
        short key = (short) 32_310;
        String summon = UltimateLifeformAddon.SOUNDS
                .get("ultimate_lifeform_summon").get().getLocation()
                .toString();
        helper.runAfterDelay(1, () -> holder.press(helper,
                UltimateLifeformSpec.BUNNY_SUMMON, key));
        helper.runAfterDelay(3, () -> helper.assertTrue(
                holder.notified.contains(summon + " player 2.0 1.0")
                        && sounds.count(UltimateLifeformAddon.SOUNDS
                                .get("ultimate_lifeform_summon").get()) == 0,
                "1.16 played the summon sound (2.0, 1.0) for the presser"
                        + " alone when the charge started; notified "
                        + holder.notified + " heard " + sounds.heard));
        helper.runAfterDelay(30, () -> {
            sounds.close();
            helper.assertTrue(holder.data.getSummonedRabbitsCount() > 0,
                    "Premise: the summon did not fire");
            helper.assertTrue(sounds.atPosition(UltimateLifeformAddon.SOUNDS
                            .get("ultimate_lifeform_summon").get(),
                            holder.player.position(), SoundSource.MASTER,
                            1.0F, 1.0F),
                    "1.16 played the summon sound at the fire (MASTER, 1.0,"
                            + " 1.0) for everyone; heard " + sounds.heard);
            AbilityInput.keyRelease(key, holder.player);
            holder.close();
            helper.succeed();
        });
    }

    // 1.16 PillarmanGiantCarthwheelPrison / PillarmanSelfDetonation called
    // builder.holdType() on top of the add-on's holdToFire(30 / 60, false):
    // the charge went on while the key was held, never fired by itself, and
    // the release fired it (PowerBaseImpl.stopHeldAction(true))
    @GameTest(template = "empty", timeoutTicks = 140)
    public static void prisonAndDetonationFireOnTheRelease(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimatePillarHeat",
                "b2d1e3f5-1c2e-4d3f-9a4b-6c7d8e9f0a13", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        holder.data.setPreviousMode(player, PillarmanMode.HEAT);
        List<Entity> blasts = new ArrayList<>();
        Consumer<ExplosionEvent.Start> stop = event -> {
            if (event.getExplosion().getDirectSourceEntity() == player) {
                blasts.add(player);
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false,
                ExplosionEvent.Start.class, stop);
        short prisonKey = (short) 32_311;
        short blastKey = (short) 32_312;
        float full = UltimateLifeformData.MAX_ENERGY;
        helper.runAfterDelay(1, () -> holder.press(helper,
                UltimateLifeformSpec.GIANT_CARTHWHEEL_PRISON, prisonKey));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(holder.data.getEnergy() == full
                            && !player.hasEffect(MobEffects.FIRE_RESISTANCE)
                            && !holder.data.isAbilityOnCooldown(
                                    UltimateLifeformSpec
                                            .GIANT_CARTHWHEEL_PRISON.id),
                    "1.16 held the prison past its 30-tick charge without"
                            + " firing it; energy=" + holder.data.getEnergy()
                            + " fireResistance="
                            + player.hasEffect(MobEffects.FIRE_RESISTANCE));
            AbilityInput.keyRelease(prisonKey, player);
        });
        helper.runAfterDelay(43, () -> {
            helper.assertTrue(player.hasEffect(MobEffects.FIRE_RESISTANCE)
                            && spent(holder.data, full, 2500.0F)
                            && holder.data.isAbilityOnCooldown(
                                    UltimateLifeformSpec
                                            .GIANT_CARTHWHEEL_PRISON.id),
                    "1.16 fired the prison on the release; energy="
                            + holder.data.getEnergy() + " fireResistance="
                            + player.hasEffect(MobEffects.FIRE_RESISTANCE));
            player.removeEffect(MobEffects.FIRE_RESISTANCE);
            holder.data.setCommandEnergy(player, full);
        });
        helper.runAfterDelay(47, () -> holder.press(helper,
                UltimateLifeformSpec.SELF_DETONATION, blastKey));
        helper.runAfterDelay(117, () -> {
            helper.assertTrue(blasts.isEmpty()
                            && holder.data.getEnergy() == full,
                    "1.16 held the self detonation past its 60-tick charge"
                            + " without exploding; blasts=" + blasts.size()
                            + " energy=" + holder.data.getEnergy());
            AbilityInput.keyRelease(blastKey, player);
        });
        helper.runAfterDelay(120, () -> {
            NeoForge.EVENT_BUS.unregister(stop);
            helper.assertTrue(blasts.size() == 1
                            && spent(holder.data, full, 3000.0F),
                    "1.16 exploded on the release; blasts=" + blasts.size()
                            + " energy=" + holder.data.getEnergy());
            holder.close();
            helper.succeed();
        });
    }

    // 1.16 UltimateLifeformKillerRabbitEntity: an ordinary rabbit (no
    // killer bunny name or armor) biting for 8; after its 600 ticks it
    // walks back to the owner and vanishes within 1 block, off the count
    // and without a health debuff (it was discarded where it stood before)
    @GameTest(template = "empty", timeoutTicks = 260)
    public static void killerRabbitIsAPlainRabbitThatWalksBack(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimatePillarRabbits",
                "b2d1e3f5-1c2e-4d3f-9a4b-6c7d8e9f0a14", GameType.SURVIVAL,
                false);
        ServerPlayer owner = holder.player;
        owner.setPos(at(helper, new Vec3(2.5D, 0.0D, 2.5D)));
        holder.data.setSummonedRabbits(2, 0);
        UltimateRabbitEntity rabbit = UltimateLifeformAddon.ULTIMATE_RABBIT
                .get().create(helper.getLevel());
        rabbit.setPos(at(helper, new Vec3(2.5D, 0.0D, 7.5D)));
        rabbit.setSummonedBy(owner, 0);
        helper.getLevel().addFreshEntity(rabbit);
        UltimateRabbitEntity home = UltimateLifeformAddon.ULTIMATE_RABBIT
                .get().create(helper.getLevel());
        home.setPos(at(helper, new Vec3(2.5D, 0.0D, 3.0D)));
        home.setSummonedBy(owner, 0);
        home.setNoAi(true);
        helper.getLevel().addFreshEntity(home);
        Cow cow = cow(helper, new Vec3(0.5D, 0.0D, 0.5D));
        cow.setNoAi(true);
        helper.assertTrue(rabbit.getVariant() != Rabbit.Variant.EVIL
                        && !rabbit.hasCustomName()
                        && rabbit.getAttributeValue(Attributes.ARMOR) == 0.0D,
                "1.16 summoned ordinary rabbits, not the killer bunny; got "
                        + rabbit.getVariant() + " armor "
                        + rabbit.getAttributeValue(Attributes.ARMOR));
        float before = cow.getHealth();
        rabbit.doHurtTarget(cow);
        helper.assertTrue(Math.abs(before - cow.getHealth() - 8.0F) < 1.0E-3F,
                "1.16 rabbits bit for 8; the cow lost "
                        + (before - cow.getHealth()));
        helper.runAfterDelay(3, () -> helper.assertTrue(rabbit.isAlive()
                        && home.isRemoved()
                        && holder.data.getSummonedRabbitsCount() == 1
                        && holder.data.getHealthDebuff() == 0,
                "1.16 sent a rabbit whose time was up back to its owner; it"
                        + " vanished where it stood (alive=" + rabbit.isAlive()
                        + " home=" + home.isRemoved()
                        + " count=" + holder.data.getSummonedRabbitsCount()
                        + " debuff=" + holder.data.getHealthDebuff() + ")"));
        helper.runAfterDelay(4, () -> helper.succeedWhen(() -> {
            helper.assertTrue(rabbit.isRemoved()
                            || rabbit.distanceTo(owner) < 2.5F,
                    "1.16 walked the rabbit back to its owner; it is still "
                            + rabbit.distanceTo(owner) + " away");
            rabbit.discard();
            cow.discard();
            holder.close();
        }));
    }

    // 1.16 UltimateLifeformPhantomEntity: size 0 with attack damage 10, idle
    // orbiting 20 blocks above its owner; after its 600 ticks it swoops back
    // and vanishes within 2 blocks of the owner, off the count, no debuff
    @GameTest(template = "empty", timeoutTicks = 40, skyAccess = true)
    public static void phantomOrbitsAboveItsOwnerAndSwoopsBack(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimatePillarPhantoms",
                "b2d1e3f5-1c2e-4d3f-9a4b-6c7d8e9f0a15", GameType.SURVIVAL,
                false);
        ServerPlayer owner = holder.player;
        owner.setPos(at(helper, new Vec3(2.5D, 0.0D, 2.5D)));
        holder.data.setSummonedPhantoms(3, 600);
        UltimatePhantomEntity idle = UltimateLifeformAddon.ULTIMATE_PHANTOM
                .get().create(helper.getLevel());
        idle.setPos(at(helper, new Vec3(2.5D, 6.0D, 2.5D)));
        idle.setSummonedBy(owner, 600);
        helper.getLevel().addFreshEntity(idle);
        UltimatePhantomEntity back = UltimateLifeformAddon.ULTIMATE_PHANTOM
                .get().create(helper.getLevel());
        back.setPos(at(helper, new Vec3(2.5D, 4.0D, 8.5D)));
        back.setSummonedBy(owner, 0);
        helper.getLevel().addFreshEntity(back);
        UltimatePhantomEntity home = UltimateLifeformAddon.ULTIMATE_PHANTOM
                .get().create(helper.getLevel());
        home.setPos(at(helper, new Vec3(2.5D, 1.0D, 3.5D)));
        home.setSummonedBy(owner, 0);
        home.setNoAi(true);
        helper.getLevel().addFreshEntity(home);
        helper.assertTrue(idle.getPhantomSize() == 0
                        && idle.getAttributeValue(Attributes.ATTACK_DAMAGE)
                                == 10.0D,
                "1.16 summoned size-0 phantoms with attack damage 10; got"
                        + " size " + idle.getPhantomSize() + " damage "
                        + idle.getAttributeValue(Attributes.ATTACK_DAMAGE));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(idle.getAnchorPoint().equals(
                            owner.blockPosition().above(20))
                            && idle.getTarget() == null,
                    "1.16 kept an idle phantom's orbit 20 blocks above its"
                            + " owner; anchor " + idle.getAnchorPoint()
                            + " owner " + owner.blockPosition());
            helper.assertTrue(back.isAlive() && back.getTarget() == owner,
                    "1.16 swooped a phantom whose time was up back at its"
                            + " owner; alive=" + back.isAlive() + " target="
                            + back.getTarget());
        });
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(home.isRemoved()
                            && holder.data.getSummonedPhantomsCount() == 2
                            && holder.data.getHealthDebuff() == 0,
                    "1.16 took a phantom that reached its owner off the"
                            + " count without a debuff; removed="
                            + home.isRemoved() + " count="
                            + holder.data.getSummonedPhantomsCount()
                            + " debuff=" + holder.data.getHealthDebuff());
            idle.discard();
            back.discard();
            holder.close();
            helper.succeed();
        });
    }

    // 1.16 KillerRabbitsSummon / PhantomSummon.perform: each summon at a
    // random spot of the user's box (the phantoms one block up), added to
    // the count; their tick: a rabbit walks to an owner on the ground beyond
    // 8 blocks and teleports next to it beyond 32, a phantom re-anchors at
    // its owner beyond 32 and teleports above an owner on the ground beyond 64
    @GameTest(template = "empty", timeoutTicks = 40, skyAccess = true)
    public static void summonsComeOutOfTheUserAndKeepUpWithIt(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimatePillarKeepUp",
                "b2d1e3f5-1c2e-4d3f-9a4b-6c7d8e9f0a16", GameType.SURVIVAL,
                false);
        ServerPlayer owner = holder.player;
        owner.setPos(at(helper, new Vec3(0.5D, 0.0D, 0.5D)));
        holder.data.setSummonedRabbits(2, 600);
        holder.data.setSummonedPhantoms(1, 600);
        UltimateLifeformCombat.perform(owner, holder.data,
                UltimateLifeformSpec.BUNNY_SUMMON, 20);
        UltimateLifeformCombat.perform(owner, holder.data,
                UltimateLifeformSpec.PHANTOM_SUMMON, 30);
        List<UltimateRabbitEntity> rabbits = helper.getLevel()
                .getEntitiesOfClass(UltimateRabbitEntity.class,
                        owner.getBoundingBox().inflate(4.0D),
                        rabbit -> rabbit.isOwnedBy(owner.getUUID()));
        List<UltimatePhantomEntity> phantoms = helper.getLevel()
                .getEntitiesOfClass(UltimatePhantomEntity.class,
                        owner.getBoundingBox().inflate(4.0D),
                        phantom -> phantom.isOwnedBy(owner.getUUID()));
        boolean rabbitsInBox = rabbits.size() == 5 && rabbits.stream()
                .allMatch(rabbit -> inUserBox(owner, rabbit, 0.0D));
        boolean phantomsInBox = phantoms.size() == 3 && phantoms.stream()
                .allMatch(phantom -> inUserBox(owner, phantom, 1.0D));
        String spots = "rabbits " + offsets(owner, rabbits) + " phantoms "
                + offsets(owner, phantoms);
        rabbits.forEach(Entity::discard);
        phantoms.forEach(Entity::discard);
        helper.assertTrue(rabbitsInBox && phantomsInBox,
                "1.16 summoned at random spots of the user's box (phantoms"
                        + " one block up); " + spots);
        helper.assertTrue(holder.data.getSummonedRabbitsCount() == 7
                        && holder.data.getSummonedPhantomsCount() == 4,
                "1.16 added each summon to the count; rabbits "
                        + holder.data.getSummonedRabbitsCount()
                        + " phantoms "
                        + holder.data.getSummonedPhantomsCount());
        owner.setOnGround(false);
        UltimatePhantomEntity anchored = phantom(helper, owner,
                new Vec3(0.5D, 40.0D, 0.5D));
        helper.getLevel().addFreshEntity(anchored);
        UltimatePhantomEntity high = phantom(helper, owner,
                new Vec3(0.5D, 70.0D, 0.5D));
        UltimateRabbitEntity walker = UltimateLifeformAddon.ULTIMATE_RABBIT
                .get().create(helper.getLevel());
        walker.setPos(at(helper, new Vec3(7.5D, 0.0D, 7.5D)));
        walker.setSummonedBy(owner, 600);
        UltimateRabbitEntity far = UltimateLifeformAddon.ULTIMATE_RABBIT
                .get().create(helper.getLevel());
        far.setPos(at(helper, new Vec3(0.5D, 40.0D, 0.5D)));
        far.setSummonedBy(owner, 600);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(anchored.getAnchorPoint()
                            .equals(owner.blockPosition())
                            && anchored.distanceTo(owner) > 32.0F,
                    "1.16 re-anchored a phantom beyond 32 blocks at its"
                            + " owner (no teleport while the owner is off"
                            + " the ground); anchor "
                            + anchored.getAnchorPoint() + " owner "
                            + owner.blockPosition() + " distance "
                            + anchored.distanceTo(owner));
            owner.setOnGround(true);
            helper.getLevel().addFreshEntity(high);
            helper.getLevel().addFreshEntity(walker);
            helper.getLevel().addFreshEntity(far);
        });
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(high.position().distanceTo(
                            owner.position().add(0.0D, 1.9D, 0.0D)) < 0.5D,
                    "1.16 teleported a phantom beyond 64 blocks to 1.9"
                            + " above its owner on the ground; it is "
                            + high.position().subtract(owner.position()));
        });
        // the rabbit lands on its second tick; a path needs the ground
        helper.runAfterDelay(5, () -> {
            BlockPos goal = walker.getNavigation().getTargetPos();
            helper.assertTrue(goal != null
                            && goal.getX() == owner.getBlockX()
                            && goal.getZ() == owner.getBlockZ()
                            && Math.abs(goal.getY() - owner.getBlockY()) <= 1
                            && walker.getNavigation().isInProgress(),
                    "1.16 walked a rabbit beyond 8 blocks to its owner on"
                            + " the ground; its path goes to "
                            + walker.getNavigation().getTargetPos()
                            + " owner " + owner.blockPosition());
        });
        helper.runAfterDelay(8, () -> {
            float distance = far.distanceTo(owner);
            anchored.discard();
            high.discard();
            walker.discard();
            far.discard();
            helper.assertTrue(distance < 8.0F,
                    "1.16 teleported a rabbit beyond 32 blocks next to its"
                            + " owner on the ground; it is " + distance
                            + " away");
            holder.close();
            helper.succeed();
        });
    }

    private static UltimatePhantomEntity phantom(GameTestHelper helper,
            LivingEntity owner, Vec3 relative) {
        UltimatePhantomEntity phantom = UltimateLifeformAddon.ULTIMATE_PHANTOM
                .get().create(helper.getLevel());
        if (phantom == null) {
            throw new IllegalStateException("Phantom fixture failed");
        }
        phantom.setPos(at(helper, relative));
        phantom.setSummonedBy(owner, 600);
        return phantom;
    }

    // Entity.getRandomX / getRandomY of the user, raised by up
    private static boolean inUserBox(LivingEntity user, Entity summon,
            double up) {
        double margin = 1.0E-6D;
        return Math.abs(summon.getX() - user.getX())
                        <= user.getBbWidth() + margin
                && Math.abs(summon.getZ() - user.getZ())
                        <= user.getBbWidth() + margin
                && summon.getY() >= user.getY() + up - margin
                && summon.getY()
                        <= user.getY() + up + user.getBbHeight() + margin;
    }

    private static List<Vec3> offsets(LivingEntity user,
            List<? extends Entity> summons) {
        return summons.stream()
                .map(summon -> summon.position().subtract(user.position()))
                .toList();
    }

    // the cost was taken; the bar refills 12 a tick under the open sky
    private static boolean spent(UltimateLifeformData data, float full,
            float cost) {
        return data.getEnergy() >= full - cost
                && data.getEnergy() <= full - cost + 100.0F;
    }

    private static int amplifier(LivingEntity entity,
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>
                    effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance != null ? instance.getAmplifier() : -1;
    }

    private static int duration(LivingEntity entity,
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>
                    effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance != null ? instance.getDuration() : -1;
    }

    private static boolean isWarning(ConditionCheck check, String postfix) {
        return check.getWarning() != null
                && check.getWarning().getContents()
                        instanceof TranslatableContents contents
                && ("jojo.message.action_condition." + postfix)
                        .equals(contents.getKey());
    }

    private static ServerLevel level(GameTestHelper helper,
            net.minecraft.resources.ResourceKey<Level> key) {
        ServerLevel level = helper.getLevel().getServer().getLevel(key);
        if (level == null) {
            throw new IllegalStateException("Premise: no level " + key);
        }
        return level;
    }

    private static Vec3 at(GameTestHelper helper, Vec3 relative) {
        return Vec3.atBottomCenterOf(helper.absolutePos(BlockPos.ZERO))
                .add(relative.x - 0.5D, relative.y, relative.z - 0.5D);
    }

    private static Cow cow(GameTestHelper helper, Vec3 relative) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        if (cow == null) {
            throw new IllegalStateException("Cow fixture failed");
        }
        cow.setPos(at(helper, relative));
        cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000.0D);
        cow.setHealth(1_000.0F);
        helper.getLevel().addFreshEntity(cow);
        return cow;
    }

    private static UltimateRabbitEntity rabbit(GameTestHelper helper,
            LivingEntity owner, Vec3 relative) {
        UltimateRabbitEntity rabbit = UltimateLifeformAddon.ULTIMATE_RABBIT
                .get().create(helper.getLevel());
        if (rabbit == null) {
            throw new IllegalStateException("Rabbit fixture failed");
        }
        rabbit.setPos(at(helper, relative));
        rabbit.setSummonedBy(owner, 600);
        helper.getLevel().addFreshEntity(rabbit);
        return rabbit;
    }

    private static ServerPlayer player(GameTestHelper helper, String name,
            String uuid, GameType gameType, boolean hurtable) {
        return holderPlayer(helper, name, uuid, gameType, hurtable,
                new ArrayList<>(), new ArrayList<>());
    }

    private static ServerPlayer holderPlayer(GameTestHelper helper,
            String name, String uuid, GameType gameType, boolean hurtable,
            List<Component> messages, List<String> notified) {
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
        player.setPos(at(helper, new Vec3(2.5D, 2.0D, 2.5D)));
        player.setGameMode(gameType);
        if (!helper.getLevel().addFreshEntity(player)) {
            throw new IllegalStateException(
                    "Pillar rules test player could not be added: " + name);
        }
        return player;
    }

    private static Holder holder(GameTestHelper helper, String name,
            String uuid, GameType gameType, boolean hurtable) {
        List<Component> messages = new ArrayList<>();
        List<String> notified = new ArrayList<>();
        ServerPlayer player = holderPlayer(helper, name, uuid, gameType,
                hurtable, messages, notified);
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
        if (!power.trySetPowerType(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM.get())) {
            throw new IllegalStateException(
                    "Pillar rules test power grant failed: " + name);
        }
        power.updateAvailableMoves();
        UltimateLifeformData data = power.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        data.setCommandEnergy(player, UltimateLifeformData.MAX_ENERGY);
        return new Holder(player, power, data, messages, notified);
    }

    private record Holder(
            ServerPlayer player,
            PlayerPower power,
            UltimateLifeformData data,
            List<Component> messages,
            List<String> notified) {
        void press(GameTestHelper helper, UltimateLifeformSpec spec,
                short key) {
            helper.assertTrue(input(spec, key, InputMethod.HOLD),
                    "Premise: the " + spec.id + " press was refused: "
                            + messages);
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

        void close() {
            power.setPowerType(null);
            player.discard();
        }
    }

    // sounds played into the world (not those sent to one player)
    private static final class Sounds implements AutoCloseable {
        private final List<Heard> heard = new ArrayList<>();
        private final Consumer<PlayLevelSoundEvent.AtEntity> atEntity;
        private final Consumer<PlayLevelSoundEvent.AtPosition> atPosition;

        private record Heard(ResourceLocation sound, Entity entity,
                Vec3 position, SoundSource source, float volume,
                float pitch) {}

        Sounds(GameTestHelper helper) {
            // only this test's own box of the shared test level
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
                            event.getSource(), event.getOriginalVolume(),
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
                            event.getPosition(), event.getSource(),
                            event.getOriginalVolume(),
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

        boolean atPosition(SoundEvent sound, Vec3 position,
                SoundSource source, float volume, float pitch) {
            return heard.stream().anyMatch(h ->
                    h.sound.equals(sound.getLocation()) && h.entity == null
                            && h.position.distanceToSqr(position) < 1.0E-4D
                            && h.source == source && h.volume == volume
                            && h.pitch == pitch);
        }

        int count(SoundEvent sound) {
            return (int) heard.stream().filter(h ->
                    h.sound.equals(sound.getLocation())).count();
        }

        @Override
        public void close() {
            NeoForge.EVENT_BUS.unregister(atEntity);
            NeoForge.EVENT_BUS.unregister(atPosition);
        }
    }
}
