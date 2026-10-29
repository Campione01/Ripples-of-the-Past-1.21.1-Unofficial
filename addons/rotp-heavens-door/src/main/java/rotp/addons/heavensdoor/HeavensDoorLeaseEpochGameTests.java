package rotp.addons.heavensdoor;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.heavensdoor.state.BookCommandLeaseService;
import rotp.addons.heavensdoor.state.PageOwnerSavedData;

@GameTestHolder(HeavensDoorAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavensDoorLeaseEpochGameTests {
    private HeavensDoorLeaseEpochGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void unloadedBehaviorCannotReturnAfterWriterRespawns(GameTestHelper helper) {
        unloadedBehaviorAfterDeath(helper, false);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void unloadedBehaviorIsRevokedBeforeWriterRespawns(GameTestHelper helper) {
        unloadedBehaviorAfterDeath(helper, true);
    }

    private static void unloadedBehaviorAfterDeath(GameTestHelper helper, boolean reloadFirst) {
        ServerPlayer original = HeavensDoorOwnerRulesGameTests.owner(helper, reloadFirst ? "HdLeaseBefore" : "HdLeaseAfter");
        ServerPlayer other = HeavensDoorOwnerRulesGameTests.owner(helper, "HdLeaseOther");
        ServerPlayer respawned = null;
        Cow subject = helper.spawn(EntityType.COW, 3, 2, 2);
        Cow foreign = helper.spawnWithNoFreeWill(EntityType.COW, 4, 2, 2);
        try {
            subject.setNoAi(true);
            int baseGoals = subject.goalSelector.getAvailableGoals().size();
            helper.assertTrue(BookCommandLeaseService.toggleBehavior(subject, original)
                            && BookCommandLeaseService.toggleBehavior(foreign, other), "Behavior fixture did not acquire both leases");
            long lifeEpoch = HeavensDoorAddon.bookCommandState(subject).behaviorIssuerEpoch();
            CompoundTag saved = saveAndUnload(helper, subject);
            original.hurt(original.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(!original.isAlive(), "Writer did not actually die");
            PageOwnerSavedData ledger = PageOwnerSavedData.get(helper.getLevel().getServer());
            PageOwnerSavedData restoredLedger = PageOwnerSavedData.load(ledger.save(new CompoundTag(), helper.getLevel().registryAccess()),
                    helper.getLevel().registryAccess());
            helper.assertTrue(!restoredLedger.matches(original.getUUID(), 0L), "Confirmed death did not survive saved-ledger round trip");
            if (reloadFirst) {
                helper.getLevel().getServer().getPlayerList().remove(original);
                helper.assertTrue(helper.getLevel().getServer().getPlayerList().getPlayer(original.getUUID()) == null,
                        "Fixture did not remove the dead writer through the native logout path before mob reload");
                subject = reload(helper, saved);
                BookCommandLeaseService.tickSubject(subject);
                helper.assertTrue(!HeavensDoorAddon.bookCommandState(subject).behaviorActive(),
                        "An unloaded behavior record remained eligible while its dead writer was absent");
            }
            respawned = helper.getLevel().getServer().getPlayerList().respawn(original, false, Entity.RemovalReason.KILLED);
            helper.assertTrue(respawned.isAlive() && respawned.getUUID().equals(original.getUUID())
                            && respawned.getPersistentData().getLong("rotp_hd.BookCommandLeaseEpoch") == lifeEpoch,
                    "Fixture did not reproduce the old persistent-player lifecycle epoch collision");
            if (!reloadFirst) {
                subject = reload(helper, saved);
            }
            // A new legitimate cast reactivates this UUID's world ledger, but not its old generation.
            ledger.begin(respawned.getUUID());
            BookCommandLeaseService.tickSubject(subject);
            BookCommandLeaseService.tickSubject(foreign);
            helper.assertTrue(!HeavensDoorAddon.bookCommandState(subject).behaviorActive(),
                    "An unloaded mob regained the dead writer's actual behavior after same-UUID respawn");
            helper.assertTrue(subject.goalSelector.getAvailableGoals().size() == baseGoals,
                    "The reloaded mob still has the dead writer's behavior goals");
            helper.assertTrue(HeavensDoorAddon.bookCommandState(foreign).behaviorActive(), "Writer death erased another caster's behavior");
            helper.assertTrue(BookCommandLeaseService.toggleBehavior(subject, respawned)
                            && HeavensDoorAddon.bookCommandState(subject).behaviorActive(), "A genuinely new post-respawn command was refused");
            helper.succeed();
        }
        finally {
            subject.discard();
            foreign.discard();
            HeavensDoorOwnerRulesGameTests.close(respawned != null ? respawned : original);
            HeavensDoorOwnerRulesGameTests.close(other);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void unloadedBehaviorSurvivesLowHealthAndATotem(GameTestHelper helper) {
        ServerPlayer writer = HeavensDoorOwnerRulesGameTests.owner(helper, "HdLeaseTotem");
        Cow subject = helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 2);
        try {
            helper.assertTrue(BookCommandLeaseService.toggleBehavior(subject, writer), "Behavior fixture failed");
            CompoundTag saved = saveAndUnload(helper, subject);
            for (int tick = 0; tick < 60; tick++) {
                writer.tick();
            }
            writer.setHealth(1.0F);
            BookCommandLeaseService.tickIssuer(writer);
            writer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            writer.hurt(writer.damageSources().generic(), 1000.0F);
            helper.assertTrue(writer.isAlive() && writer.getOffhandItem().isEmpty(), "Totem fixture did not survive a lethal hit");
            subject = reload(helper, saved);
            BookCommandLeaseService.tickSubject(subject);
            helper.assertTrue(HeavensDoorAddon.bookCommandState(subject).behaviorActive(),
                    "Unloaded mob behavior used the restricted 8% epoch or treated a totem as death");
            helper.succeed();
        }
        finally {
            subject.discard();
            HeavensDoorOwnerRulesGameTests.close(writer);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void legacyBehaviorCannotBindToANewerWriterLife(GameTestHelper helper) {
        ServerPlayer writer = HeavensDoorOwnerRulesGameTests.owner(helper, "HdLeaseLegacy");
        Cow subject = helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 2);
        try {
            helper.assertTrue(BookCommandLeaseService.toggleBehavior(subject, writer), "Legacy fixture failed");
            CompoundTag legacy = HeavensDoorAddon.bookCommandState(subject).serializeNBT(subject.registryAccess());
            legacy.putInt("Version", 2);
            legacy.getCompound("Behavior").remove("IssuerDeathEpoch");
            BookCommandLeaseService.onEntityUnload(subject);
            PageOwnerSavedData ledger = PageOwnerSavedData.get(writer.getServer());
            ledger.invalidate(writer.getUUID());
            ledger.begin(writer.getUUID());
            HeavensDoorAddon.bookCommandState(subject).deserializeNBT(subject.registryAccess(), legacy);
            BookCommandLeaseService.tickSubject(subject);
            helper.assertTrue(!HeavensDoorAddon.bookCommandState(subject).behaviorActive(),
                    "Legacy behavior without a death epoch was rebound to a newer writer life");
            helper.succeed();
        }
        finally {
            subject.discard();
            HeavensDoorOwnerRulesGameTests.close(writer);
        }
    }

    private static CompoundTag saveAndUnload(GameTestHelper helper, Cow subject) {
        CompoundTag saved = new CompoundTag();
        helper.assertTrue(subject.save(saved), "Behavior subject could not save");
        subject.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertTrue(helper.getLevel().getEntity(subject.getUUID()) == null, "Behavior subject did not unload");
        return saved;
    }

    private static Cow reload(GameTestHelper helper, CompoundTag saved) {
        Entity loaded = EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
        helper.assertTrue(loaded instanceof Cow && helper.getLevel().addFreshEntity(loaded), "Behavior subject could not reload");
        return (Cow) loaded;
    }
}
