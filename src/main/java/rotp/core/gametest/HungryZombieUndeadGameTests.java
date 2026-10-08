package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.vampirism.abilities.VampirismDarkAuraAbility;
import rotp.core.impl.powers.vampirism.entity.HungryZombieEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.mechanics.JojoDefinitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;

/**
 * 1.16 HungryZombieEntity is a ZombieEntity, whose getMobType() is UNDEAD. In 1.21.1 that is the minecraft:undead
 * entity type tag, which the vanilla smite, inverted healing and poison/regeneration tags include.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HungryZombieUndeadGameTests {
    private static final String BATCH = "hungry_zombie_undead";

    private HungryZombieUndeadGameTests() {}

    @GameTest(template = "empty", batch = BATCH)
    public static void hungryZombieIsUndeadForVanillaAndForTheMod(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityType<HungryZombieEntity> type = ModEntityTypes.HUNGRY_ZOMBIE.get();
        HungryZombieEntity zombie = type.create(level);
        helper.assertTrue(zombie != null, "HZ-UNDEAD premise: could not create the Hungry Zombie");
        try {
            zombie.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
            boolean undead = type.is(EntityTypeTags.UNDEAD);
            boolean smite = type.is(EntityTypeTags.SENSITIVE_TO_SMITE);
            boolean inverted = type.is(EntityTypeTags.INVERTED_HEALING_AND_HARM) && zombie.isInvertedHealAndHarm();
            boolean ignoresPoison = type.is(EntityTypeTags.IGNORES_POISON_AND_REGEN)
                    && !zombie.canBeAffected(new MobEffectInstance(MobEffects.POISON, 100))
                    && !zombie.canBeAffected(new MobEffectInstance(MobEffects.REGENERATION, 100));
            boolean modUndead = JojoDefinitions.isUndeadOrVampiric(zombie);
            boolean hamon = JojoDefinitions.isAffectedByHamon(zombie);
            boolean bleeds = JojoDefinitions.canBleed(zombie);
            helper.assertTrue(undead && smite && inverted && ignoresPoison && modUndead && hamon && bleeds,
                    "Hungry Zombie (1.16: a ZombieEntity, mob type UNDEAD): undeadTag=" + undead + " smiteTag=" + smite
                            + " invertedHealAndHarm=" + inverted + " ignoresPoisonAndRegen=" + ignoresPoison
                            + " isUndeadOrVampiric=" + modUndead + " isAffectedByHamon=" + hamon + " canBleed=" + bleeds);
        }
        finally {
            zombie.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void darkAuraDoesNotDebuffTheVampiresOwnZombies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Difficulty oldDifficulty = level.getDifficulty();
        List<Entity> owned = new ArrayList<>();
        LivingComponentAction component = null;
        FakePlayer player = null;
        try {
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            Vec3 origin = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "DarkAuraOwner"));
            owned.add(player);
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.setPos(origin);
            level.addNewPlayer(player);
            PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
            power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            VampirismData data = PlayerPower.getPowerData(player, ModPlayerPowers.VAMPIRISM).orElseThrow();
            data.setVampireFullPower(true, player);
            player.setHealth(player.getMaxHealth());
            VampirismState.get(player).blood().setCurrent(100.0F);
            data.setBloodLevel(100.0F);

            HungryZombieEntity zombie = ModEntityTypes.HUNGRY_ZOMBIE.get().create(level);
            Pillager living = EntityType.PILLAGER.create(level);
            helper.assertTrue(zombie != null && living != null, "HZ-UNDEAD premise: could not create the targets");
            owned.add(zombie);
            owned.add(living);
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
            zombie.setOwner(player);
            zombie.setPos(origin.add(1.0D, 0.0D, 0.0D));
            living.setNoAi(true);
            living.setNoGravity(true);
            living.setPos(origin.add(0.0D, 0.0D, 1.0D));
            helper.assertTrue(level.addFreshEntity(zombie) && level.addFreshEntity(living) && zombie.isEntityOwner(player),
                    "HZ-UNDEAD premise: the targets were not added");

            var ability = power.getAbility("vampirism_dark_aura");
            helper.assertTrue(ability instanceof VampirismDarkAuraAbility, "HZ-UNDEAD premise: registered Dark Aura is missing");
            EntityActionInstance action = ((VampirismDarkAuraAbility) ability)
                    .initActionOnAbilityUse(level, player, player, null);
            action.setPhaseStart(ActionPhase.PERFORM);
            component = LivingComponentAction.getComponent(player);
            component.setAction(action, player, SyncType.NO_SYNC);
            helper.assertTrue(component.getAction() == action, "HZ-UNDEAD premise: Dark Aura was rejected");
            component.tick();

            helper.assertTrue(Math.abs(VampirismState.get(player).blood().current() - 75.0F) < 0.001F
                            && living.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && living.hasEffect(MobEffects.WEAKNESS)
                            && living.hasEffect(MobEffects.DIG_SLOWDOWN),
                    "HZ-UNDEAD premise: Dark Aura did not run on the living control: blood="
                            + VampirismState.get(player).blood().current() + " effects=" + living.getActiveEffects());
            boolean slowed = zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
            boolean weakened = zombie.hasEffect(MobEffects.WEAKNESS);
            boolean fatigued = zombie.hasEffect(MobEffects.DIG_SLOWDOWN);
            helper.assertTrue(!slowed && !weakened && !fatigued,
                    "Dark Aura next to the vampire's own Hungry Zombie (1.16 VampirismDarkAura skips undead targets):"
                            + " slowness=" + slowed + " weakness=" + weakened + " miningFatigue=" + fatigued);
        }
        finally {
            try {
                if (component != null) component.setAction(null, player, SyncType.NO_SYNC);
            }
            finally {
                for (Entity entity : owned) {
                    if (!entity.isRemoved()) entity.discard();
                }
                level.getServer().setDifficulty(oldDifficulty, true);
            }
        }
        helper.succeed();
    }
}
