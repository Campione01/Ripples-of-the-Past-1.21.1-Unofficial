package rotp.addons.heavensdoor.state;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.entity.HeavensDoorStandEntity;
import rotp.addons.heavensdoor.mixin.EntityPersistentDataAccessor;
import rotp.addons.heavensdoor.network.BookCommand;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;

@GameTestHolder(HeavensDoorAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SelfHarmOwnerGameTests {
    private static final String OWNER = "rotp_hd.SelfHarmOwner";
    private static final String OTHER = "unrelated.test";

    private SelfHarmOwnerGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ordinaryCowTickDoesNotAllocatePersistentData(GameTestHelper helper) {
        Cow cow = cow(helper);
        try {
            helper.assertTrue(existing(cow) == null, "Fresh cow already has persistent data");
            helper.assertTrue(!cow.saveWithoutId(new CompoundTag()).contains("NeoForgeData"),
                    "Fresh cow fixture already serializes persistent data");
            for (int i = 0; i < 20; i++) {
                BookCommandSessionService.tickSelfHarmOwner(cow);
            }
            helper.assertTrue(existing(cow) == null
                            && !cow.saveWithoutId(new CompoundTag()).contains("NeoForgeData"),
                    "Ordinary owner tick allocated/saved an empty persistent-data tag");
            helper.succeed();
        }
        finally {
            cow.discard();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void taggedOwnerAliveDeadAndAbsentKeepExistingCleanupSemantics(GameTestHelper helper) {
        FakePlayer owner = player(helper);
        Cow cow = cow(helper);
        try {
            cow.getPersistentData().putString(OTHER, "preserve");
            cow.getPersistentData().putUUID(OWNER, owner.getUUID());
            cow.addEffect(new MobEffectInstance(HeavensDoorAddon.KYS_EFFECT, 200));
            BookCommandSessionService.tickSelfHarmOwner(cow);
            helper.assertTrue(cow.hasEffect(HeavensDoorAddon.KYS_EFFECT) && existing(cow).hasUUID(OWNER),
                    "Alive owner lost its active tagged effect");
            owner.setHealth(0.0F);
            BookCommandSessionService.tickSelfHarmOwner(cow);
            assertCleared(helper, cow, "Dead owner");
            cow.getPersistentData().putUUID(OWNER, UUID.randomUUID());
            cow.addEffect(new MobEffectInstance(HeavensDoorAddon.KYS_EFFECT, 200));
            BookCommandSessionService.tickSelfHarmOwner(cow);
            assertCleared(helper, cow, "Absent owner");
            helper.succeed();
        }
        finally {
            cow.discard();
            close(owner);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void staleLoadedAndExternalRemovalCleanWhileLegacyAndMalformedTagsRemain(GameTestHelper helper) {
        Cow source = cow(helper);
        Cow loaded = cow(helper);
        Cow legacy = cow(helper);
        try {
            source.getPersistentData().putString(OTHER, "preserve");
            source.getPersistentData().putUUID(OWNER, UUID.randomUUID());
            source.addEffect(new MobEffectInstance(HeavensDoorAddon.KYS_EFFECT, 200));
            source.removeEffect(HeavensDoorAddon.KYS_EFFECT);
            CompoundTag saved = source.saveWithoutId(new CompoundTag());
            loaded.load(saved);
            helper.assertTrue(existing(loaded) != null && existing(loaded).hasUUID(OWNER),
                    "Stale owner tag was not loaded through vanilla entity NBT");
            BookCommandSessionService.tickSelfHarmOwner(loaded);
            assertCleared(helper, loaded, "Loaded stale tag without effect");
            BookCommandSessionService.tickSelfHarmOwner(source);
            assertCleared(helper, source, "External effect removal");

            legacy.addEffect(new MobEffectInstance(HeavensDoorAddon.KYS_EFFECT, 200));
            BookCommandSessionService.tickSelfHarmOwner(legacy);
            helper.assertTrue(legacy.hasEffect(HeavensDoorAddon.KYS_EFFECT) && existing(legacy) == null,
                    "Unowned legacy effect was removed or acquired persistent data");
            legacy.getPersistentData().putString(OWNER, "legacy-malformed-owner");
            BookCommandSessionService.tickSelfHarmOwner(legacy);
            helper.assertTrue(legacy.hasEffect(HeavensDoorAddon.KYS_EFFECT)
                            && existing(legacy).getString(OWNER).equals("legacy-malformed-owner"),
                    "Malformed owner behavior changed");
            helper.succeed();
        }
        finally {
            source.discard();
            loaded.discard();
            legacy.discard();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registeredBookCommandManuallyTogglesSelfHarmOff(GameTestHelper helper) {
        FakePlayer owner = player(helper);
        Cow cow = cow(helper);
        StandPower power = PowerClass.STAND.attachGet(owner);
        try {
            helper.getLevel().addFreshEntity(cow);
            power.setStand(HeavensDoorAddon.HEAVENS_DOOR.get());
            power.setResolveLevel(4);
            helper.assertTrue(HeavensDoorAddon.HEAVENS_DOOR.get().summon(owner, power), "Heaven's Door summon failed");
            HeavensDoorStandEntity stand = (HeavensDoorStandEntity) power.getSummonedStandEntity();
            cow.getPersistentData().putString(OTHER, "preserve");
            toggle(helper, owner, stand, cow);
            helper.assertTrue(cow.hasEffect(HeavensDoorAddon.KYS_EFFECT)
                            && cow.getData(HeavensDoorAddon.WRITTEN_COMMAND_STATE).has(BookCommand.SELF_HARM_TOGGLE, owner.getUUID()),
                    "Registered command did not apply self-harm ownership");
            BookCommandSessionService.tickSelfHarmOwner(cow);
            helper.assertTrue(cow.hasEffect(HeavensDoorAddon.KYS_EFFECT), "Living command owner was rejected");
            toggle(helper, owner, stand, cow);
            assertCleared(helper, cow, "Manual command toggle off");
            helper.assertTrue(!cow.getData(HeavensDoorAddon.WRITTEN_COMMAND_STATE).has(BookCommand.SELF_HARM_TOGGLE),
                    "Manual toggle retained its source record");
            cow.getPersistentData().putUUID(OWNER, owner.getUUID());
            cow.addEffect(new MobEffectInstance(HeavensDoorAddon.KYS_EFFECT, 200));
            toggle(helper, owner, stand, cow);
            assertCleared(helper, cow, "Legacy owner's explicit toggle off");
            helper.succeed();
        }
        finally {
            HeavensDoorAddon.HEAVENS_DOOR.get().forceUnsummon(owner, power);
            cow.discard();
            close(owner);
        }
    }

    private static void toggle(GameTestHelper helper, FakePlayer owner, HeavensDoorStandEntity stand, Cow cow) {
        helper.assertTrue(BookStateService.markFromHit(stand, cow), "Book marking failed");
        var session = BookCommandSessionService.issue(owner, cow);
        helper.assertTrue(session != null, "Book command session was not issued");
        helper.assertTrue(BookCommandSessionService.consume(owner, session.nonce(), BookCommand.SELF_HARM_TOGGLE,
                        BookCommandSessionService.NO_PLAYER_OPTION), "Registered self-harm command was refused");
    }

    private static CompoundTag existing(Cow cow) {
        return ((EntityPersistentDataAccessor) cow).heavensDoor$existingPersistentData();
    }

    private static void assertCleared(GameTestHelper helper, Cow cow, String label) {
        helper.assertTrue(!cow.hasEffect(HeavensDoorAddon.KYS_EFFECT) && !existing(cow).contains(OWNER)
                        && existing(cow).getString(OTHER).equals("preserve"),
                label + " failed cleanup or removed unrelated NBT");
    }

    private static Cow cow(GameTestHelper helper) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "Cow fixture failed");
        cow.setPos(helper.absoluteVec(new Vec3(3, 2, 2)));
        return cow;
    }

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HdOwner"));
        player.setPos(helper.absoluteVec(new Vec3(2, 2, 2)));
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    private static void close(FakePlayer player) {
        EventHooks.firePlayerLoggedOut(player);
        player.discard();
    }
}
