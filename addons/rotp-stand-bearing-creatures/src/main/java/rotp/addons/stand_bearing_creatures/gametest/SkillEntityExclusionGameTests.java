package rotp.addons.stand_bearing_creatures.gametest;

import java.util.List;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.addons.stand_bearing_creatures.MobsWithPowersAddon;
import rotp.addons.stand_bearing_creatures.config.MobPowerConfig;
import rotp.addons.stand_bearing_creatures.util.MobPowerUtil;

@GameTestHolder(MobsWithPowersAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillEntityExclusionGameTests {
    private SkillEntityExclusionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void emptyLootLifeformsSkipRandomPowerAssignment(GameTestHelper helper) {
        Mob canonical = EntityType.PUFFERFISH.create(helper.getLevel());
        Mob legacy = EntityType.PUFFERFISH.create(helper.getLevel());
        Mob ordinary = EntityType.PUFFERFISH.create(helper.getLevel());
        int chance = MobPowerConfig.PERCENTAGE_CHANCE_TO_GETTING_A_STAND_FOR_MOB.get();
        boolean requireAdvancement =
                MobPowerConfig.REQUIRE_STAND_ADVANCEMENT_TO_SPAWN_MOBS_WITH_POWERS.get();
        boolean whitelist = MobPowerConfig.USE_ENTITY_WHITELIST.get();
        boolean useAddons = MobPowerConfig.USE_ADDON_STANDS.get();
        List<? extends String> entityList = MobPowerConfig.ENTITY_LIST.get();
        List<? extends String> blockedStands = MobPowerConfig.BLOCKED_STANDS_FOR_MOBS.get();
        try {
            helper.assertTrue(canonical != null && legacy != null && ordinary != null,
                    "Missing pufferfish join fixtures");
            MobPowerConfig.PERCENTAGE_CHANCE_TO_GETTING_A_STAND_FOR_MOB.set(100);
            MobPowerConfig.REQUIRE_STAND_ADVANCEMENT_TO_SPAWN_MOBS_WITH_POWERS.set(false);
            MobPowerConfig.USE_ENTITY_WHITELIST.set(false);
            MobPowerConfig.USE_ADDON_STANDS.set(false);
            MobPowerConfig.ENTITY_LIST.set(List.of());
            MobPowerConfig.BLOCKED_STANDS_FOR_MOBS.set(List.of());

            // Created lifeforms load an explicit loot override before their production join.
            loadEmptyLoot(canonical, BuiltInLootTables.EMPTY.location().toString());
            loadEmptyLoot(legacy, "empty");
            helper.assertTrue(canonical.getLootTable().equals(BuiltInLootTables.EMPTY)
                            && legacy.getLootTable().equals(BuiltInLootTables.EMPTY),
                    "Canonical or legacy empty-loot fixture did not load");
            helper.assertTrue(MobPowerUtil.isEligibleMob(ordinary)
                            && !MobPowerUtil.isGoldExperienceCreated(ordinary),
                    "Ordinary pufferfish eligibility or loot classification changed");

            Vec3 origin = helper.absoluteVec(new Vec3(1, 2, 1));
            canonical.setPos(origin);
            legacy.setPos(origin.add(2, 0, 0));
            ordinary.setPos(origin.add(4, 0, 0));
            helper.assertTrue(helper.getLevel().addFreshEntity(canonical),
                    "Canonical-empty lifeform join failed");
            helper.assertTrue(helper.getLevel().addFreshEntity(legacy),
                    "Legacy-empty lifeform join failed");
            helper.assertTrue(helper.getLevel().addFreshEntity(ordinary),
                    "Ordinary pufferfish join failed");
            helper.assertTrue(!MobPowerUtil.hasStand(canonical)
                            && !MobPowerUtil.hasPlayerPower(canonical)
                            && !canonical.getPersistentData().contains(MobPowerUtil.PROCESSED_TAG),
                    "Production join rolled powers for the canonical-empty lifeform");
            helper.assertTrue(!MobPowerUtil.hasStand(legacy)
                            && !MobPowerUtil.hasPlayerPower(legacy)
                            && !legacy.getPersistentData().contains(MobPowerUtil.PROCESSED_TAG),
                    "Production join rolled powers for the legacy-empty lifeform");
            helper.assertTrue(ordinary.getPersistentData().getBoolean(MobPowerUtil.PROCESSED_TAG)
                            && MobPowerUtil.hasStand(ordinary),
                    "The deterministic ordinary pufferfish control was not processed and granted a Stand");
            LogUtils.getLogger().info("R539 empty-loot production joins: canonicalExcluded=true, "
                    + "legacyExcluded=true, ordinaryProcessed=true, ordinaryStand=true");
            helper.succeed();
        } finally {
            try {
                if (canonical != null) canonical.discard();
                if (legacy != null) legacy.discard();
                if (ordinary != null) ordinary.discard();
            } finally {
                MobPowerConfig.PERCENTAGE_CHANCE_TO_GETTING_A_STAND_FOR_MOB.set(chance);
                MobPowerConfig.REQUIRE_STAND_ADVANCEMENT_TO_SPAWN_MOBS_WITH_POWERS.set(requireAdvancement);
                MobPowerConfig.USE_ENTITY_WHITELIST.set(whitelist);
                MobPowerConfig.USE_ADDON_STANDS.set(useAddons);
                MobPowerConfig.ENTITY_LIST.set(entityList);
                MobPowerConfig.BLOCKED_STANDS_FOR_MOBS.set(blockedStands);
            }
        }
    }

    private static void loadEmptyLoot(Mob mob, String lootTable) {
        CompoundTag tag = mob.saveWithoutId(new CompoundTag());
        tag.putString("DeathLootTable", lootTable);
        mob.load(tag);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void partNineSheerHeartSkipsPowerAssignment(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("rotp_zkq8", "sheer_heart");
        // The exclusion must not make the optional Part 9 addon a dependency.
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            LogUtils.getLogger().info("Part 9 SHA exclusion test: optional addon absent");
            helper.succeed();
            return;
        }
        Entity actor = BuiltInRegistries.ENTITY_TYPE.get(id).create(helper.getLevel());
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(actor instanceof Mob && cow != null, "Missing join fixtures");
        try {
            helper.assertTrue(actor.getType().is(MobPowerUtil.POWER_AI_EXCLUDED),
                    "Part 9 SHA is not in the loaded exclusion tag");
            helper.assertTrue(!MobPowerUtil.isEligibleMob(actor),
                    "Part 9 SHA remains eligible for random powers");
            helper.assertTrue(MobPowerUtil.isEligibleMob(cow),
                    "Ordinary cow eligibility changed under the active configuration");
            helper.assertTrue(StandPower.get((Mob) actor) == null,
                    "SHA already had a Stand attachment before the production join");
            actor.setPos(helper.absoluteVec(new Vec3(1, 2, 1)));
            cow.setPos(actor.position().add(2, 0, 0));
            helper.assertTrue(helper.getLevel().addFreshEntity(actor), "SHA join failed");
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Cow join failed");
            helper.assertTrue(StandPower.get((Mob) actor) == null
                            && PlayerPower.get((Mob) actor) == null
                            && !actor.getPersistentData().contains(MobPowerUtil.PROCESSED_TAG),
                    "Production join attached or rolled powers for excluded SHA");
            helper.assertTrue(StandPower.get(cow) != null && PlayerPower.get(cow) != null,
                    "Production join no longer attaches ordinary mob powers");
            LogUtils.getLogger().info("Part 9 SHA production join: excluded=true, "
                    + "standAttachment=false, playerPowerAttachment=false, processed=false; "
                    + "cowEligible=true, cowAttachments=true");
            helper.succeed();
        } finally {
            actor.discard();
            if (cow != null) {
                cow.discard();
            }
        }
    }
}
