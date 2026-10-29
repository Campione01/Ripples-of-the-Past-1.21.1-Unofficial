package rotp.addons.frosttraveler.gametest;

import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;

import rotp.addons.frosttraveler.FrostTravelerAddon;
import rotp.addons.frosttraveler.item.DisguiseData;
import rotp.addons.frosttraveler.item.MeatMaskItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrostTravelerAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MeatMaskProfileGameTests {
    // Opaque data fixtures: these tests never resolve or render a skin.
    private static final String TEXTURE_VALUE = "mask-profile-texture-fixture";
    private static final String TEXTURE_SIGNATURE = "mask-profile-signature-fixture";

    private MeatMaskProfileGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void capturedPlayerProfileRoundTrips(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper, InteractionHand.MAIN_HAND)) {
            DisguiseData captured = capturePlayer(helper, fixture);
            assertPlayerSnapshot(helper, captured, fixture);

            fixture.target().getGameProfile().getProperties().clear();
            fixture.target().getGameProfile().getProperties().put(
                    "textures", new Property("textures", "changed-after-capture"));
            assertPlayerSnapshot(helper, captured, fixture);

            JsonElement encoded = DisguiseData.CODEC
                    .encodeStart(JsonOps.INSTANCE, captured).getOrThrow();
            helper.assertTrue(encoded.getAsJsonObject().has("captured_profile"),
                    "Captured profile was absent from persistent data");
            DisguiseData persisted = DisguiseData.CODEC
                    .parse(JsonOps.INSTANCE, encoded).getOrThrow();
            assertPlayerSnapshot(helper, persisted, fixture);
            helper.assertTrue(captured.equals(persisted),
                    "Persistent round trip changed the mask snapshot");

            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                    Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                DisguiseData.STREAM_CODEC.encode(buffer, captured);
                DisguiseData decoded = DisguiseData.STREAM_CODEC.decode(buffer);
                assertPlayerSnapshot(helper, decoded, fixture);
                helper.assertTrue(captured.equals(decoded) && buffer.readableBytes() == 0,
                        "Stream round trip changed data or left unread bytes");
            } finally {
                buffer.release();
            }
            assertMaskCount(helper, fixture.mask());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void legacyProfileAbsentAndMobRecapture(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper, InteractionHand.OFF_HAND)) {
            DisguiseData captured = capturePlayer(helper, fixture);
            JsonObject legacy = DisguiseData.CODEC
                    .encodeStart(JsonOps.INSTANCE, captured).getOrThrow()
                    .getAsJsonObject().deepCopy();
            legacy.remove("captured_profile");
            DisguiseData decoded = DisguiseData.CODEC
                    .parse(JsonOps.INSTANCE, legacy).getOrThrow();
            DisguiseData expectedLegacy = new DisguiseData(
                    captured.ownerUuid(), captured.targetType(), captured.targetUuid(),
                    captured.targetName(), captured.hostile());
            helper.assertTrue(decoded.equals(expectedLegacy)
                            && decoded.capturedProfile().isEmpty(),
                    "Legacy mask failed to preserve its profile-absent data");

            Cow cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "Could not create the cow capture target");
            try {
                InteractionResult result = maskItem(fixture.mask()).interactLivingEntity(
                        fixture.mask().copy(), fixture.owner(), cow, fixture.hand());
                helper.assertTrue(result == InteractionResult.CONSUME,
                        "Production callback refused mob recapture");
                DisguiseData recaptured = data(fixture.mask());
                helper.assertTrue(recaptured.ownerUuid().equals(fixture.owner().getUUID())
                                && recaptured.targetType().equals(Optional.of(
                                        BuiltInRegistries.ENTITY_TYPE.getKey(cow.getType())))
                                && recaptured.targetUuid().isEmpty()
                                && recaptured.capturedProfile().isEmpty()
                                && recaptured.targetName().equals(cow.getName().getString())
                                && !recaptured.hostile(),
                        "Mob recapture retained player identity or profile data");
                assertMaskCount(helper, fixture.mask());
            } finally {
                if (cow != null) {
                    cow.discard();
                }
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void maskCaptureRetainsOwnerAndSelfGuards(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper, InteractionHand.MAIN_HAND)) {
            DisguiseData blank = data(fixture.mask());
            InteractionResult self = maskItem(fixture.mask()).interactLivingEntity(
                    fixture.mask().copy(), fixture.owner(), fixture.owner(), fixture.hand());
            helper.assertTrue(self == InteractionResult.FAIL
                            && data(fixture.mask()).equals(blank),
                    "Production callback captured its own wearer");

            fixture.owner().setItemInHand(fixture.hand(), ItemStack.EMPTY);
            fixture.target().setItemInHand(InteractionHand.OFF_HAND, fixture.mask());
            InteractionResult foreignOwner = maskItem(fixture.mask()).interactLivingEntity(
                    fixture.mask().copy(), fixture.target(), fixture.owner(),
                    InteractionHand.OFF_HAND);
            helper.assertTrue(foreignOwner == InteractionResult.FAIL
                            && data(fixture.mask()).equals(blank),
                    "Production callback allowed another owner to capture");
            assertMaskCount(helper, fixture.mask());
            helper.succeed();
        }
    }

    private static Fixture fixture(GameTestHelper helper, InteractionHand hand) {
        GameProfile targetProfile = new GameProfile(UUID.randomUUID(), "MaskTarget");
        targetProfile.getProperties().put("textures",
                new Property("textures", TEXTURE_VALUE, TEXTURE_SIGNATURE));
        targetProfile.getProperties().put("fixture",
                new Property("fixture", "mask-extra-property", "mask-extra-signature"));
        PropertyMap expectedProperties = new PropertyMap();
        expectedProperties.putAll(targetProfile.getProperties());

        ServerPlayer owner = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "MaskOwner"));
        ServerPlayer target = null;
        try {
            target = FakePlayerFactory.get(helper.getLevel(), targetProfile);
            ItemStack mask = new ItemStack(FrostTravelerAddon.MEAT_DGS_ITEM.get());
            mask.set(FrostTravelerAddon.DISGUISE_DATA,
                    DisguiseData.blank(owner.getUUID()));
            owner.setItemInHand(hand, mask);
            return new Fixture(owner, target, mask, hand, expectedProperties);
        } catch (RuntimeException | Error error) {
            owner.discard();
            if (target != null) {
                target.discard();
            }
            throw error;
        }
    }

    private static DisguiseData capturePlayer(GameTestHelper helper, Fixture fixture) {
        ItemStack suppliedCopy = fixture.mask().copy();
        DisguiseData before = data(suppliedCopy);
        InteractionResult result = maskItem(fixture.mask()).interactLivingEntity(
                suppliedCopy, fixture.owner(), fixture.target(), fixture.hand());
        helper.assertTrue(result == InteractionResult.CONSUME,
                "Production callback refused player capture");
        helper.assertTrue(data(suppliedCopy).equals(before)
                        && fixture.owner().getItemInHand(fixture.hand()) == fixture.mask(),
                "Production callback did not use the actual hand stack");
        assertMaskCount(helper, fixture.mask());
        return data(fixture.mask());
    }

    private static void assertPlayerSnapshot(
            GameTestHelper helper, DisguiseData data, Fixture fixture) {
        helper.assertTrue(data.ownerUuid().equals(fixture.owner().getUUID())
                        && data.targetUuid().equals(Optional.of(fixture.target().getUUID()))
                        && data.targetType().isEmpty()
                        && data.targetName().equals("MaskTarget")
                        && !data.hostile(),
                "Player capture changed identity, ownership, or hostility");
        helper.assertTrue(data.capturedProfile().isPresent(),
                "Player capture omitted its profile snapshot");
        ResolvableProfile profile = data.capturedProfile().orElseThrow();
        helper.assertTrue(profile.id().equals(Optional.of(fixture.target().getUUID()))
                        && profile.name().equals(Optional.of("MaskTarget"))
                        && profile.properties().equals(fixture.expectedProperties())
                        && profile.gameProfile().getId().equals(fixture.target().getUUID())
                        && profile.gameProfile().getName().equals("MaskTarget")
                        && profile.gameProfile().getProperties()
                                .equals(fixture.expectedProperties()),
                "Captured profile lost its name, UUID, or property snapshot");
        Property textures = profile.properties().get("textures").iterator().next();
        helper.assertTrue(TEXTURE_VALUE.equals(textures.value())
                        && TEXTURE_SIGNATURE.equals(textures.signature()),
                "Captured texture property lost its value or signature");
    }

    private static MeatMaskItem maskItem(ItemStack stack) {
        return (MeatMaskItem) stack.getItem();
    }

    private static DisguiseData data(ItemStack stack) {
        DisguiseData data = stack.get(FrostTravelerAddon.DISGUISE_DATA);
        if (data == null) {
            throw new IllegalStateException("Mask lost its disguise data");
        }
        return data;
    }

    private static void assertMaskCount(GameTestHelper helper, ItemStack stack) {
        helper.assertTrue(stack.getCount() == 1,
                "Capture consumed or duplicated the mask");
    }

    private record Fixture(
            ServerPlayer owner,
            ServerPlayer target,
            ItemStack mask,
            InteractionHand hand,
            PropertyMap expectedProperties) implements AutoCloseable {
        @Override
        public void close() {
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            target.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            target.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            owner.discard();
            target.discard();
        }
    }
}
