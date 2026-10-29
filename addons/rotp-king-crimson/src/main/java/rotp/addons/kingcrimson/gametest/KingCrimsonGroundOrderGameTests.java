package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonPhase4Spec;
import rotp.core.init.ModDamageTypes;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonGroundOrderGameTests {
    private KingCrimsonGroundOrderGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_ground_order")
    public static void groundPunchAreaPrecedesNativePrimary(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonGroundOrderGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        // 1.16 KingCrimsonGroundSlam.punchEntity/punchBlock push the area before the hit, as Ground Punch does.
        int scenes = orderScenes(helper, checks, "kingcrimson_groundpunch")
                + orderScenes(helper, checks, "kingcrimson_groundslam");
        checks.finish(helper, "D-KC-GROUND-03", scenes);
    }

    private static int orderScenes(GameTestHelper helper, KingCrimsonGroundGameTestFixture.Checks checks, String ability) {
        boolean slam = ability.equals("kingcrimson_groundslam");
        double range = slam ? KingCrimsonPhase4Spec.GROUND_SLAM_RANGE : KingCrimsonPhase4Spec.GROUND_PUNCH_RANGE;
        double vertical = slam ? KingCrimsonPhase4Spec.GROUND_SLAM_VERTICAL_KNOCKBACK
                : KingCrimsonPhase4Spec.GROUND_PUNCH_VERTICAL_KNOCKBACK;
        String prefix = slam ? "G3/slam/" : "G3/";
        int scenes = 0;
        for (int mode = 0; mode < 4; mode++) {
            String id = prefix + "entity/" + mode;
            try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, mode == 2 ? 0 : 1)) {
                if (slam) scene.stand.setArmsOnlyMode(true, true);
                scene.stand.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6);
                var primary = scene.cow(scene.origin.add(0, 0, 1.5), mode == 3 ? 0.25F : 200);
                var secondary = scene.cow(primary.position().add(1.2, 0, 0), 200);
                if (mode == 1) scene.listen(LivingIncomingDamageEvent.class, event -> {
                    if (event.getEntity() == primary) event.setCanceled(true);
                });
                float expectedArea = Math.max(0.25F, (float) (scene.stand.getAttackDamage()
                        * (0.5 - primary.position().distanceTo(secondary.position()) / range)));
                Vec3 push = secondary.position().subtract(primary.position()).multiply(0.4, 0, 0.4).add(0, vertical, 0);
                scene.cast(primary, "heavy_punch", ability);
                var primaryHits = scene.hits(primary);
                var areaHits = scene.hits(secondary);
                scene.premise(primaryHits.size() == 1 && areaHits.size() == 1, "Actual primary and area native hurt callbacks");
                checks.check(scene.hits.indexOf(areaHits.getFirst()) < scene.hits.indexOf(primaryHits.getFirst()),
                        id + " native secondary hurt precedes primary; sequence="
                                + scene.hits.stream().map(hit -> hit.target.getId()).toList());
                var area = areaHits.getFirst();
                checks.check(area.source.is(DamageTypes.MOB_ATTACK) && !area.source.is(ModDamageTypes.STAND_ATTACK)
                                && area.source.getDirectEntity() == scene.stand && area.source.getEntity() == scene.user
                                && KingCrimsonGroundGameTestFixture.near(area.amount, expectedArea),
                        id + " unchanged ordinary source/feet falloff/raw secondary at M=" + scene.multiplier.get());
                checks.check(KingCrimsonGroundGameTestFixture.near(secondary.getDeltaMovement(), area.after.add(push)),
                        id + " unchanged additive area push" + vertical);
                checks.check(Boolean.valueOf(mode != 1).equals(primaryHits.getFirst().result),
                        id + " actual primary success/refusal preserved");
                if (mode == 2) checks.check(primary.getHealth() == 200 && primaryHits.getFirst().amount == 0,
                        id + " M0 successful primary with no health loss");
                if (mode == 3) {
                    var drops = scene.spawns.stream().filter(spawn -> spawn.duringHurt() == primary
                            && spawn.entity() instanceof ItemEntity).toList();
                    scene.premise(primary.isDeadOrDying() && !drops.isEmpty(), "Actual native lethal hit and native loot spawned");
                    checks.check(drops.stream().allMatch(spawn -> KingCrimsonGroundGameTestFixture.near(
                                    spawn.entity().getDeltaMovement(), spawn.initialVelocity())),
                            id + " newly created native loot not retrospectively area-pushed: "
                                    + drops.stream().map(spawn -> spawn.initialVelocity() + " -> " + spawn.entity().getDeltaMovement()).toList());
                }
            }
            scenes++;
        }
        try (var scene = new KingCrimsonGroundGameTestFixture(helper, prefix + "block", 1)) {
            if (slam) scene.stand.setArmsOnlyMode(true, true);
            scene.stand.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
            BlockPos block = BlockPos.containing(scene.origin).offset(0, 0, 2);
            Vec3 center = Vec3.atCenterOf(block);
            scene.block(block, Blocks.DIRT.defaultBlockState());
            // Ground Punch, a 1.16 heavy attack, keeps no block it was pressed at and aims again when it strikes: the
            // user looks at the block and the secondary stays inside the area but off the Stand's precision ray.
            // Ground Slam, a light attack, keeps the block: the user's level look passes over it, so the block breaks
            // only because the slam kept its press target.
            if (!slam) scene.aimUserAt(center);
            var secondary = scene.cow(center.add(slam ? 1.2 : 2.0, 0, 0), 200);
            if (!slam) scene.premise(scene.offUserPrecisionRay(secondary), "Secondary off the precision ray, precision="
                    + scene.stand.getPrecision());
            else scene.premise(!scene.userLookMeets(block), "Slam user's look passes over the block");
            List<KingCrimsonGroundGameTestFixture.Spawn> drops = new ArrayList<>();
            List<Long> areaHitsAtDrop = new ArrayList<>();
            scene.listen(EntityJoinLevelEvent.class, event -> {
                if (event.getLevel() == scene.level && event.getEntity() instanceof ItemEntity item
                        && item.getItem().is(Items.DIRT) && item.position().distanceTo(center) < 1) {
                    scene.owned.add(item);
                    drops.add(new KingCrimsonGroundGameTestFixture.Spawn(item, null, item.getDeltaMovement()));
                    areaHitsAtDrop.add(areaHits(scene, secondary));
                }
            });
            var action = scene.cast(new ActionTarget(block, Direction.NORTH), "heavy_punch", ability);
            scene.premise(block.equals(action.punchedTarget.getBlockPos()) && scene.level.getBlockState(block).isAir()
                    && !drops.isEmpty(), "Actual registered block punch broke dirt with native drops");
            // Ground Punch's heavy block explosion may also hurt the secondary; only the area hit is ordered here.
            checks.check(areaHits(scene, secondary) == 1 && areaHitsAtDrop.stream().allMatch(count -> count == 1),
                    prefix + "block secondary area hurt preceded native destruction/drop events=" + areaHitsAtDrop);
            checks.check(drops.stream().allMatch(spawn -> KingCrimsonGroundGameTestFixture.near(
                            spawn.entity().getDeltaMovement(), spawn.initialVelocity())),
                    prefix + "block native block drops retain spawn velocity=" + drops.stream()
                            .map(spawn -> spawn.initialVelocity() + " -> " + spawn.entity().getDeltaMovement()).toList());
        }
        return scenes + 1;
    }

    private static long areaHits(KingCrimsonGroundGameTestFixture scene, Entity target) {
        return scene.hits(target).stream().filter(hit -> hit.source.is(DamageTypes.MOB_ATTACK)).count();
    }
}
