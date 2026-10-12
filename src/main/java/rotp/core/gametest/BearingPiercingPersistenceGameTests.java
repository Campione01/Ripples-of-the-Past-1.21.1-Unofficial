package rotp.core.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ProjectilePiercing;
import rotp.core.customobjects.entity_projectile.ThrownNuggetBearingEntity;
import rotp.core.init.ModEntityTypes;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BearingPiercingPersistenceGameTests {
    private BearingPiercingPersistenceGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 80, batch = "bearing_piercing_persistence")
    public static void savedIronBearingDoesNotHitTheSameEntityAgain(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        suite.identity();
        try {
            suite.run();
        } finally {
            suite.close();
        }
        log("SUMMARY scenes=" + suite.scenes + " checks=" + suite.checks + " failures=" + suite.failures.size()
                + " details=" + suite.failures);
        helper.assertTrue(suite.failures.isEmpty(), "Bearing persistence failures; see SUMMARY");
        helper.succeed();
    }

    private static final class Suite implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final List<Entity> actors = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        int scenes, checks;

        Suite(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
        }

        void identity() {
            for (Class<?> type : new Class<?>[] {ProjectilePiercing.class, ThrownNuggetBearingEntity.class,
                    BearingPiercingPersistenceGameTests.class}) {
                String actual = sha(type);
                String expected = System.getProperty("rotp.bearing.expected." + type.getName());
                helper.assertTrue(type.getProtectionDomain().getCodeSource() != null
                        && (expected == null || expected.equalsIgnoreCase(actual)), "Bearing class binding " + type.getName());
                log("LOADED class=" + type.getName() + " sha256=" + actual + " source="
                        + type.getProtectionDomain().getCodeSource().getLocation());
            }
        }

        void run() {
            Cow first = cow(0), second = cow(2), third = cow(4), untouched = cow(6);
            for (int count = 0; count <= 2; count++) {
                ProjectilePiercing original = new ProjectilePiercing(3);
                if (count > 0) helper.assertTrue(original.pierceEntity(new EntityHitResult(first), 3), "First pierce admitted");
                if (count > 1) helper.assertTrue(original.pierceEntity(new EntityHitResult(second), 3), "Second pierce admitted");
                ProjectilePiercing restored = ProjectilePiercing.fromNBT(original.toNBT());
                helper.assertTrue(restored != null, "List restore exists");
                check("helper/" + count, restored.alreadyHit(first) == (count > 0), "first UUID retained");
                check("helper/" + count, restored.alreadyHit(second) == (count > 1), "second UUID retained");
                check("helper/" + count, !restored.alreadyHit(untouched), "unhit entity stays eligible");
                ListTag savedAgain = null;
                try { savedAgain = (ListTag) restored.toNBT(); }
                catch (RuntimeException error) { log("SAVE-ERROR helper/" + count + " " + error); }
                check("helper/" + count, savedAgain != null && savedAgain.size() == count, "same budget count and serializable hits");
                if (count > 0) check("helper/" + count, savedAgain != null && NbtUtils.loadUUID(savedAgain.get(0)).equals(first.getUUID()), "first serialized UUID");
                if (count > 1) check("helper/" + count, savedAgain != null && NbtUtils.loadUUID(savedAgain.get(1)).equals(second.getUUID()), "second serialized UUID");
                check("helper/" + count, restored.pierceEntity(new EntityHitResult(third), 3) == (count < 2), "remaining pierce budget unchanged");
                scenes++;
            }
            check("non-list", ProjectilePiercing.fromNBT(IntTag.valueOf(3)) == null, "non-list behavior unchanged");
            scenes++;

            for (int count = 1; count <= 2; count++) {
                first.setHealth(first.getMaxHealth());
                second.setHealth(second.getMaxHealth());
                first.invulnerableTime = second.invulnerableTime = 0;
                ThrownNuggetBearingEntity original = bearing(0);
                helper.assertTrue(original.getItem().is(Tags.Items.NUGGETS_IRON), "Loaded iron tag required");
                hit(original, first);
                if (count == 2) hit(original, second);
                helper.assertTrue(!original.isRemoved(), "Piercing projectile remains after " + count + " hits");
                helper.assertTrue(!eligible(original, first), "Original excludes first hit");
                CompoundTag saved = original.saveWithoutId(new CompoundTag());
                helper.assertTrue(saved.getList("pierced", 11).size() == count, "Actual projectile writes hit list");
                original.discard();
                ThrownNuggetBearingEntity restored = ModEntityTypes.NUGGET_BEARING.get().create(level);
                helper.assertTrue(restored != null, "Registered bearing recreation");
                actors.add(restored);
                restored.load(saved);
                helper.assertTrue(level.addFreshEntity(restored), "Restored projectile admitted");
                check("entity/" + count, !eligible(restored, first), "loaded first hit excluded");
                check("entity/" + count, eligible(restored, second) == (count < 2), "loaded second hit eligibility");
                check("entity/" + count, eligible(restored, untouched), "unhit target remains eligible");
                float health = first.getHealth();
                first.invulnerableTime = 0;
                if (eligible(restored, first)) hit(restored, first);
                check("entity/" + count, first.getHealth() == health, "no repeat damage after reload");
                check("entity/" + count, !restored.isRemoved(), "no spent pierce on excluded target");
                CompoundTag savedAgain = null;
                try { savedAgain = restored.saveWithoutId(new CompoundTag()); }
                catch (RuntimeException error) { log("SAVE-ERROR entity/" + count + " " + error); }
                check("entity/" + count, savedAgain != null && savedAgain.getList("pierced", 11).size() == count,
                        "serializable hit count unchanged after excluded contact");
                scenes++;
            }
        }

        Cow cow(double x) {
            Cow cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Cow exists");
            actors.add(cow);
            cow.setPos(helper.absoluteVec(new Vec3(3 + x, 3, 3)));
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(cow), "Cow admitted");
            return cow;
        }

        ThrownNuggetBearingEntity bearing(double x) {
            Vec3 pos = helper.absoluteVec(new Vec3(3 + x, 3, 6));
            ThrownNuggetBearingEntity projectile = new ThrownNuggetBearingEntity(level, pos.x, pos.y, pos.z,
                    new ItemStack(Items.IRON_NUGGET));
            actors.add(projectile);
            projectile.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(projectile), "Projectile admitted");
            return projectile;
        }

        void check(String scene, boolean passed, String detail) {
            checks++;
            if (!passed) { failures.add(scene + " " + detail); log("VIOLATION " + scene + " " + detail); }
        }

        @Override public void close() {
            for (Entity actor : actors) actor.discard();
            helper.assertTrue(actors.stream().allMatch(Entity::isRemoved), "Every fixture actor removed");
            log("CLEANUP actors=" + actors.size() + " removed=true");
        }
    }

    private static boolean eligible(ThrownNuggetBearingEntity projectile, Entity target) {
        return (boolean) invoke("canHitEntity", Entity.class, projectile, target);
    }

    private static void hit(ThrownNuggetBearingEntity projectile, Entity target) {
        invoke("onHitEntity", EntityHitResult.class, projectile, new EntityHitResult(target));
    }

    // Invoke the registered projectile's real protected contact predicates, without replacing them in a test subtype.
    private static Object invoke(String name, Class<?> argument, Object receiver, Object value) {
        try {
            Method method = ThrownNuggetBearingEntity.class.getDeclaredMethod(name, argument);
            method.setAccessible(true);
            return method.invoke(receiver, value);
        } catch (InvocationTargetException error) {
            throw new IllegalStateException("Native bearing contact failed", error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Bearing contact method unavailable", error);
        }
    }

    private static String sha(Class<?> type) {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Class resource absent");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static void log(String text) { System.out.println("R769-BEARING " + text); }
}
