package rotp.addons.ultimate_lifeform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ultimate_lifeform.entity.UltimateAreaEffectEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateProjectileEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateScaleProjectileEntity;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.init.ModDamageTypes;

/**
 * The 1.16 add-on dealt these Hamon hits with the plain DamageUtil.HAMON source, which names no entity
 * (UltimateHamonSendoOverdriveEntity.Wave.tick, ProjectileUltimateHamonChargeCap.dealHamonDamageToTarget). An
 * Enderman only teleports away unharmed from a Hamon source that has a direct entity and a separate owner.
 */
@GameTestHolder(UltimateLifeformAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimateHamonEndermanGameTests {
    private UltimateHamonEndermanGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_enderman_sendo", timeoutTicks = 40)
    public static void sendoWaveDamagesAnEndermanWithThePlainHamonSource(GameTestHelper helper) {
        Scene scene = new Scene(helper, "sendo wave");
        try {
            scene.build();
            Cow owner = scene.cow(scene.point(10.5D, 0.0D, 4.5D));
            UltimateAreaEffectEntity sendo = UltimateLifeformAddon.ULTIMATE_SENDO_HAMON_OVERDRIVE.get().create(scene.level);
            scene.check(sendo != null, "fixture: the registered Sendo type created no entity");
            // the targeted Sendo Overdrive of UltimateLifeformCombat, on the block the Enderman stands on
            sendo.initializeSendo(owner, BlockPos.containing(scene.start).below(), Direction.UP, 1.125F, 9.0F, 8);
            // 1.16 Wave.tick hurt with the raw wave damage
            float expected = sendo.getEffectDamage();
            scene.check(expected > 0.0F, "fixture: the wave has no damage for an Enderman");
            for (int tick = 0; tick < 4; tick++) {
                sendo.tick();
            }
            scene.check(sendo.getActiveWaveAges().equals(List.of(1)),
                    "fixture: the first wave did not run at tick 4: " + sendo.getActiveWaveAges());
            scene.checkPlainHamonHit(expected, "the Sendo wave");
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_enderman_scale", timeoutTicks = 40)
    public static void hamonScaleDamagesAnEndermanWithThePlainHamonSource(GameTestHelper helper) {
        Scene scene = new Scene(helper, "hamon scale");
        try {
            scene.build();
            Cow owner = scene.shooter();
            UltimateScaleProjectileEntity scale = UltimateLifeformAddon.SCALE_PROJECTILE.get().create(scene.level);
            scene.check(scale != null, "fixture: the registered scale type created no entity");
            scale.initialize(owner, UltimateScaleProjectileEntity.DONOR_DAMAGE, true, 1.0F, 0.0F);
            for (int tick = 0; tick < 10 && !scale.isRemoved(); tick++) {
                scale.tick();
            }
            scene.check(scale.isRemoved(), "fixture: the scale never reached the Enderman; scale at " + scale.position());
            scene.checkPlainHamonHit(scene.hamonHurtAmount(UltimateScaleProjectileEntity.DONOR_DAMAGE), "a Hamon scale");
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    // Only the old serializer class under the kept scale registry id reaches this branch of UltimateProjectileEntity.
    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_enderman_projectile", timeoutTicks = 40)
    public static void hamonProjectileDamagesAnEndermanWithThePlainHamonSource(GameTestHelper helper) {
        Scene scene = new Scene(helper, "hamon projectile");
        try {
            scene.build();
            Cow owner = scene.shooter();
            @SuppressWarnings("unchecked")
            EntityType<UltimateProjectileEntity> type = (EntityType<UltimateProjectileEntity>)
                    (EntityType<?>) UltimateLifeformAddon.SCALE_PROJECTILE.get();
            UltimateProjectileEntity projectile = new UltimateProjectileEntity(type, scene.level);
            projectile.initialize(owner, 5.0F, true, 1.0F, 0.0F);
            scene.check(!projectile.isTentacle() && !projectile.isGuardian(), "fixture: the projectile is a tentacle or a laser end");
            for (int tick = 0; tick < 10 && !projectile.isRemoved(); tick++) {
                projectile.tick();
            }
            scene.check(projectile.isRemoved(),
                    "fixture: the projectile never reached the Enderman; projectile at " + projectile.position());
            scene.checkPlainHamonHit(scene.hamonHurtAmount(5.0F), "a Hamon projectile");
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    /** A stone-floored room in the test origin's chunk, 32 blocks above it, with an Enderman that cannot leave it. */
    private static final class Scene {
        private final GameTestHelper helper;
        final ServerLevel level;
        private final String name;
        private final int x;
        private final int y;
        private final int z;
        private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        private final List<Entity> owned = new ArrayList<>();
        private final List<DamageSource> sources = new ArrayList<>();
        private Consumer<EntityTeleportEvent.EnderEntity> redirect;
        private Consumer<LivingIncomingDamageEvent> incoming;
        EnderMan enderman;
        Vec3 start;
        private Vec3 refuge;
        private float health;
        private boolean closed;

        Scene(GameTestHelper helper, String name) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.name = name;
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(origin);
            this.x = chunk.getMinBlockX();
            this.y = origin.getY() + 32;
            this.z = chunk.getMinBlockZ();
        }

        Vec3 point(double dx, double dy, double dz) {
            return new Vec3(x + dx, y + dy, z + dz);
        }

        void check(boolean condition, String message) {
            helper.assertTrue(condition, "ULF-HAMON-ENDERMAN " + name + ": " + message);
        }

        void build() {
            BlockPos from = new BlockPos(x + 4, y - 1, z + 3);
            BlockPos to = new BlockPos(x + 12, y + 4, z + 13);
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                check(level.isLoaded(pos) && level.isEmptyBlock(pos), "fixture: room cell is not loaded and empty: " + pos);
            }
            for (BlockPos pos : BlockPos.betweenClosed(from, new BlockPos(to.getX(), y - 1, to.getZ()))) {
                originals.put(pos.immutable(), level.getBlockState(pos));
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            }
            start = point(8.5D, 0.0D, 7.5D);
            refuge = point(6.5D, 0.0D, 11.5D);
            enderman = EntityType.ENDERMAN.create(level);
            check(enderman != null, "fixture: no Enderman");
            enderman.setNoAi(true);
            enderman.setPos(start);
            add(enderman);
            health = enderman.getHealth();
            // the random destination is replaced, so the Enderman stays inside the room
            redirect = event -> {
                if (event.getEntity() == enderman) {
                    event.setTargetX(refuge.x);
                    event.setTargetY(refuge.y);
                    event.setTargetZ(refuge.z);
                }
            };
            NeoForge.EVENT_BUS.addListener(EntityTeleportEvent.EnderEntity.class, redirect);
            incoming = event -> {
                if (event.getEntity() == enderman) {
                    sources.add(event.getSource());
                }
            };
            NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, incoming);
        }

        Cow cow(Vec3 feet) {
            Cow cow = EntityType.COW.create(level);
            check(cow != null, "fixture: no cow");
            cow.setNoAi(true);
            cow.setPos(feet);
            cow.setXRot(0.0F);
            cow.setYRot(0.0F);
            cow.setYHeadRot(0.0F);
            cow.yBodyRot = 0.0F;
            return add(cow);
        }

        /** A cow three blocks in front of the Enderman, looking at it. */
        Cow shooter() {
            return cow(point(8.5D, 0.0D, 4.5D));
        }

        <T extends Entity> T add(T entity) {
            owned.add(entity);
            check(level.addFreshEntity(entity), "fixture: could not add " + entity);
            return entity;
        }

        /** What HamonAbilityHelpers.hamonHurt deals to the Enderman when the source names no Hamon user. */
        float hamonHurtAmount(float baseDamage) {
            float amount = HamonAbilityHelpers.hamonDamageAmount(enderman, baseDamage)
                    * HamonAbilityHelpers.configHamonDamageMultiplier();
            check(amount > 0.0F, "fixture: the hit has no damage for an Enderman");
            return amount;
        }

        void checkPlainHamonHit(float expected, String what) {
            float lost = health - enderman.getHealth();
            check(enderman.isAlive() && Math.abs(lost - expected) < 1.0E-4F,
                    "1.16 dealt the Hamon damage of " + what + " with the plain Hamon source, which an Enderman takes ("
                            + expected + "), but health went " + health + " -> " + enderman.getHealth() + " and it "
                            + (enderman.position().distanceTo(refuge) < 0.01D ? "teleported away"
                                    : enderman.position().distanceTo(start) < 0.01D ? "did not teleport"
                                            : "stands at " + enderman.position()));
            check(sources.size() == 1 && sources.get(0).is(ModDamageTypes.HAMON)
                            && sources.get(0).getDirectEntity() == null && sources.get(0).getEntity() == null,
                    "1.16 DamageUtil.HAMON names no entity, but " + what + " hit with " + describeSources());
        }

        private String describeSources() {
            List<String> lines = new ArrayList<>();
            for (DamageSource source : sources) {
                lines.add(source.getMsgId() + " direct=" + source.getDirectEntity() + " owner=" + source.getEntity());
            }
            return lines.toString();
        }

        void close() {
            if (closed) return;
            closed = true;
            if (redirect != null) NeoForge.EVENT_BUS.unregister(redirect);
            if (incoming != null) NeoForge.EVENT_BUS.unregister(incoming);
            for (Entity entity : owned) if (!entity.isRemoved()) entity.discard();
            originals.forEach(level::setBlockAndUpdate);
        }
    }
}
