package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.vampirism.abilities.VampirismZombieSummonAbility;
import rotp.core.impl.powers.vampirism.entity.HungryZombieEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.playerpower.PlayerPower;

/**
 * 1.16 Zombie Summon parity: HungryZombieEntity.killed did not run the vanilla villager conversion, the int field
 * xpReward was multiplied by 1.5 with truncation, NonStandAction.checkEnergy reported the missing blood, and the
 * Peaceful refusal had its own English text.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HungryZombieSummonParityGameTests {
    private static final String BATCH = "hungry_zombie_summon_parity";
    private static final String NO_ENERGY = "jojo.message.action_condition.no_energy_vampirism";
    private static final String PEACEFUL = "jojo.message.action_condition.peaceful";

    private HungryZombieSummonParityGameTests() {}

    @GameTest(template = "empty", batch = BATCH)
    public static void villagerKilledOnHardBecomesOwnedHungryZombie(GameTestHelper helper) {
        killOnHard(helper, EntityType.VILLAGER);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void pillagerKilledOnHardBecomesOwnedHungryZombie(GameTestHelper helper) {
        killOnHard(helper, EntityType.PILLAGER);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void adultDropsSevenAndBabySeventeenExperience(GameTestHelper helper) {
        try (Scene scene = Scene.open(helper)) {
            scene.premise(scene.level.getDifficulty() != Difficulty.PEACEFUL
                    && scene.level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT), "needs a non-peaceful world with mob loot");
            FakePlayer stranger = scene.player("HungryXpStranger");
            // 1.16: xpReward *= 1.5 on the int field, 5 -> 7; a baby drops (int) (7 * 2.5)
            int adult = scene.experienceDroppedTo(stranger, false);
            int baby = scene.experienceDroppedTo(stranger, true);
            helper.assertTrue(adult == 7 && baby == 17,
                    "Hungry Zombie killed by a player who is not its owner dropped " + adult + " xp as an adult and "
                            + baby + " as a baby, 1.16 dropped 7 and 17");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void underfundedSummonPressShowsNoEnergyMessage(GameTestHelper helper) {
        try (Scene scene = Scene.open(helper)) {
            scene.premise(scene.level.getDifficulty() != Difficulty.PEACEFUL, "needs a non-peaceful world");
            RecordingPlayer vampire = new RecordingPlayer(scene.level);
            scene.vampire(vampire);
            PlayerPower power = PowerClass.PLAYER_POWER.attachGet(vampire);
            VampirismData data = PlayerPower.getPowerData(vampire, ModPlayerPowers.VAMPIRISM).orElseThrow();
            Ability summon = power.getAbility("vampirism_zombie_summon");
            scene.premise(summon instanceof VampirismZombieSummonAbility && !vampire.isCreative(), "registered summon is missing");
            VampirismState.get(vampire).blood().setCurrent(100.0F);
            data.setBloodLevel(100.0F);
            scene.premise(summon.checkConditions(power).isPositive(), "summon at its exact cost of 100 blood is not admitted");
            VampirismState.get(vampire).blood().setCurrent(99.0F);
            data.setBloodLevel(99.0F);
            vampire.actionBar.clear();
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            boolean admitted = AbilityInput.withConditionCheck(available.getContextVariationContainer(summon), vampire,
                    InputMethod.CLICK);
            List<String> keys = vampire.keys();
            helper.assertTrue(!admitted && keys.equals(List.of(NO_ENERGY)),
                    "Zombie Summon press with 99 of 100 blood: admitted=" + admitted + " messages=" + keys
                            + " expected the single message " + NO_ENERGY);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void peacefulRefusalKeeps116EnglishText(GameTestHelper helper) {
        // 1.16 en_us; 1.16 en_pt had no entry of its own and showed the en_us text
        String expected = "Monsters can't hurt anyone on Peaceful difficulty";
        List<String> wrong = new ArrayList<>();
        for (String locale : new String[] { "en_us", "en_pt" }) {
            JsonElement value = readLang(locale).get(PEACEFUL);
            if (value != null && !expected.equals(value.getAsString()) || value == null && locale.equals("en_us")) {
                wrong.add(locale + " = " + (value != null ? value.getAsString() : null));
            }
        }
        helper.assertTrue(wrong.isEmpty(), PEACEFUL + " is not the 1.16 text \"" + expected + "\": " + wrong);
        helper.succeed();
    }

    private static void killOnHard(GameTestHelper helper, EntityType<? extends Mob> victimType) {
        MinecraftServer server = helper.getLevel().getServer();
        Difficulty original = helper.getLevel().getDifficulty();
        try (Scene scene = Scene.open(helper)) {
            FakePlayer owner = scene.player("HungryKillOwner");
            scene.vampire(owner);
            HungryZombieEntity killer = scene.mob(ModEntityTypes.HUNGRY_ZOMBIE.get(), scene.origin);
            killer.setOwner(owner);
            Mob victim = scene.mob(victimType, scene.origin.add(0.0D, 0.0D, 2.0D));
            Vec3 victimPosition = victim.position();
            scene.premise(killer.getOwner() == owner && victim.isAlive(), "zombie does not resolve its vampire owner");
            // set and restored in this call, no tick sees the change
            server.setDifficulty(Difficulty.HARD, true);
            scene.premise(scene.level.getDifficulty() == Difficulty.HARD, "could not switch to Hard");
            victim.hurt(scene.level.damageSources().mobAttack(killer), 1000.0F);
            scene.premise(victim.isDeadOrDying() || victim.isRemoved(), "the victim survived");

            List<ZombieVillager> vanilla = scene.level.getEntitiesOfClass(ZombieVillager.class, scene.box);
            List<HungryZombieEntity> converted = scene.level.getEntitiesOfClass(HungryZombieEntity.class, scene.box,
                    zombie -> zombie != killer);
            boolean owned = converted.size() == 1 && converted.get(0).isEntityOwner(owner)
                    && converted.get(0).getOwner() == owner && converted.get(0).isAlive()
                    && converted.get(0).position().distanceTo(victimPosition) < 1.0E-6D;
            helper.assertTrue(vanilla.isEmpty() && owned && victim.isRemoved(),
                    "Hungry Zombie killed a " + EntityType.getKey(victimType).getPath() + " on Hard: zombieVillagers="
                            + vanilla.size() + " hungryZombies=" + converted.size() + " ownedAtVictimPosition=" + owned
                            + " victimRemoved=" + victim.isRemoved()
                            + ", 1.16 always turned it into one Hungry Zombie of the killer's owner");
        }
        finally {
            server.setDifficulty(original, true);
        }
        helper.succeed();
    }

    private static JsonObject readLang(String locale) {
        String path = "assets/" + JojoMod.MOD_ID + "/lang/" + locale + ".json";
        InputStream stream = HungryZombieSummonParityGameTests.class.getResourceAsStream("/" + path);
        if (stream == null) {
            stream = HungryZombieSummonParityGameTests.class.getClassLoader().getResourceAsStream(path);
        }
        if (stream == null) {
            throw new GameTestAssertException(path + " is missing");
        }
        try (InputStream in = stream) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch (IOException e) {
            throw new GameTestAssertException("Could not read " + path + ": " + e);
        }
    }

    private static final class RecordingPlayer extends FakePlayer {
        private final List<Component> actionBar = new ArrayList<>();

        private RecordingPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "HungrySummonPoor"));
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            if (actionBar) {
                this.actionBar.add(message);
            }
        }

        private List<String> keys() {
            return actionBar.stream()
                    .map(message -> message.getContents() instanceof TranslatableContents contents
                            ? contents.getKey() : message.getString())
                    .toList();
        }
    }

    private static final class Scene implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final List<FakePlayer> players = new ArrayList<>();
        private Vec3 origin;
        private AABB box;

        private Scene(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
        }

        private static Scene open(GameTestHelper helper) {
            Scene scene = new Scene(helper);
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            scene.origin = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 40.0D, chunk.getMinBlockZ() + 6.5D);
            scene.box = new AABB(scene.origin, scene.origin).inflate(6.0D);
            scene.premise(scene.level.getEntities((Entity) null, scene.box).isEmpty(), "scene contains a foreign entity");
            return scene;
        }

        private void premise(boolean condition, String message) {
            helper.assertTrue(condition, "HZ-PARITY premise: " + message);
        }

        private FakePlayer player(String name) {
            FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
            place(player);
            return player;
        }

        private void place(FakePlayer player) {
            players.add(player);
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.moveTo(origin.x, origin.y, origin.z - 3.0D, 0.0F, 0.0F);
            level.addNewPlayer(player);
        }

        private void vampire(FakePlayer player) {
            if (!players.contains(player)) {
                place(player);
            }
            PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
            power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            PlayerPower.getPowerData(player, ModPlayerPowers.VAMPIRISM).orElseThrow().setVampireFullPower(true, player);
        }

        private <T extends Mob> T mob(EntityType<T> type, Vec3 position) {
            T mob = type.create(level);
            premise(mob != null, "could not create " + EntityType.getKey(type));
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
            premise(level.addFreshEntity(mob), "could not add " + EntityType.getKey(type));
            return mob;
        }

        private int experienceDroppedTo(FakePlayer killer, boolean baby) {
            HungryZombieEntity zombie = mob(ModEntityTypes.HUNGRY_ZOMBIE.get(), origin);
            zombie.setBaby(baby);
            premise(zombie.isBaby() == baby && level.getEntitiesOfClass(ExperienceOrb.class, box).isEmpty(),
                    "zombie age or a stray experience orb");
            zombie.hurt(level.damageSources().playerAttack(killer), 1000.0F);
            premise(zombie.isDeadOrDying(), "the Hungry Zombie survived");
            int total = 0;
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box)) {
                total += orb.getValue();
                orb.discard();
            }
            zombie.discard();
            return total;
        }

        @Override
        public void close() {
            try {
                for (FakePlayer player : players) {
                    PowerClass.PLAYER_POWER.attachGet(player).setPowerType(null);
                }
            }
            finally {
                for (Entity entity : level.getEntities((Entity) null, box)) {
                    if (!entity.isRemoved()) entity.discard();
                }
                for (FakePlayer player : players) {
                    if (!player.isRemoved()) player.discard();
                }
            }
        }
    }
}
