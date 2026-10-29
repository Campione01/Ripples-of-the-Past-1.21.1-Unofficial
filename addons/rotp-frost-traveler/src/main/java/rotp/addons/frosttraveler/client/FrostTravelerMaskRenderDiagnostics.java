package rotp.addons.frosttraveler.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joml.Matrix3f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.addons.frosttraveler.FrostTravelerAddon;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Read-only production render receipts for the Frost Traveler mask. */
@OnlyIn(Dist.CLIENT)
public final class FrostTravelerMaskRenderDiagnostics {
    public static final String MASK_REGISTRY_ID = "rotp_zcs:meat_dgs";
    public static final String MASK_MODEL_RESOURCE_ID =
            FrostTravelerMaskReceiptValidator.MASK_MODEL_RESOURCE_ID;
    public static final String DONOR_MASK_MODEL_SHA256 =
            FrostTravelerMaskReceiptValidator.DONOR_MASK_MODEL_SHA256;
    public static final String CANONICAL_MASK_MODEL_SHA256 =
            FrostTravelerMaskReceiptValidator
                    .CANONICAL_MASK_MODEL_SHA256;
    public static final String DISGUISE_LAYER_CLASS =
            DisguiseLayer.class.getName();
    public static final String SCOPED_VISIBILITY_CLASS =
            "rotp.core.api.client.render."
                     + "ScopedPlayerModelVisibility";
    public static final String OWNER_THIRD_PERSON = "owner_third_person";
    public static final String TRACKED_OBSERVER = "tracked_observer";
    private static final String NONE = "none";

    private static final Map<UUID, MutableFrame> CURRENT =
            new HashMap<>();
    private static final Map<UUID, Snapshot> LAST =
            new HashMap<>();
    private static long frameSequence;
    private static boolean frameOpen;
    private static UUID latestWearer;
    private static int registeredDisguiseLayerCount;
    private static int unexpectedMaskLayerRegistrationCount;
    private static ItemModelSnapshot cachedItemModelSnapshot;
    private static ResourceManager cachedItemModelResourceManager;
    private static long resourceReloadGeneration;

    private FrostTravelerMaskRenderDiagnostics() {}

    static synchronized void recordLayerRegistration(String layerClass) {
        if (DISGUISE_LAYER_CLASS.equals(layerClass)) {
            registeredDisguiseLayerCount++;
        } else {
            unexpectedMaskLayerRegistrationCount++;
        }
    }

    public static synchronized int registeredDisguiseLayerCount() {
        return registeredDisguiseLayerCount;
    }

    public static synchronized int unexpectedMaskLayerRegistrationCount() {
        return unexpectedMaskLayerRegistrationCount;
    }

    public static synchronized boolean noUnexpectedLayerRegistrations() {
        return registeredDisguiseLayerCount > 0
                && unexpectedMaskLayerRegistrationCount == 0;
    }

    /** Returns the current or most recently finalized frame for one wearer. */
    public static synchronized Snapshot snapshot(UUID wearerUuid) {
        if (wearerUuid == null) {
            return Snapshot.unobserved(
                    frameSequence, null, armorPath());
        }
        MutableFrame current = CURRENT.get(wearerUuid);
        if (frameOpen && current != null) {
            return current.freeze(
                    frameSequence,
                    false,
                    LAST.get(wearerUuid),
                    armorPath());
        }
        return LAST.getOrDefault(
                wearerUuid,
                Snapshot.unobserved(
                        frameSequence,
                        wearerUuid,
                        armorPath()));
    }

    /** Returns the snapshot for the active client player. */
    public static synchronized Snapshot localPlayerSnapshot() {
        Player player = Minecraft.getInstance().player;
        return snapshot(player != null ? player.getUUID() : null);
    }

    /** Returns the last wearer observed by a production render callback. */
    public static synchronized Snapshot latestSnapshot() {
        return snapshot(latestWearer);
    }

    static synchronized void beginFrame() {
        if (frameOpen) {
            finishFrame();
        }
        frameSequence++;
        frameOpen = true;
        CURRENT.clear();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (UUID wearerUuid : LAST.keySet()) {
            Player wearer = minecraft.level
                    .getPlayerByUUID(wearerUuid);
            MutableFrame frame = new MutableFrame(wearerUuid);
            if (wearer != null) {
                frame.observeEquipment(wearer);
            }
            CURRENT.put(wearerUuid, frame);
        }
    }

    static synchronized void endFrame() {
        if (frameOpen) {
            finishFrame();
        }
    }

    static synchronized void recordPlayerPre(
            AbstractClientPlayer player,
            PlayerModel<?> model) {
        MutableFrame frame = frame(player);
        frame.playerRenderPreCount++;
        frame.currentPreVisibility = VisibilityState.capture(model);
        frame.currentRenderHideRequested = false;
    }

    static synchronized void recordScopedHideRequest(
            AbstractClientPlayer player) {
        MutableFrame frame = frame(player);
        frame.scopedHideRequestCount++;
        frame.currentRenderHideRequested = true;
    }

    static synchronized void recordPlayerPost(
            AbstractClientPlayer player,
            PlayerModel<?> model) {
        MutableFrame frame = frame(player);
        frame.playerRenderPostCount++;
        if (frame.currentRenderHideRequested
                && frame.currentPreVisibility != null) {
            frame.visibilityRestoreCheckCount++;
            if (!frame.currentPreVisibility.equals(
                    VisibilityState.capture(model))) {
                frame.visibilityRestoreMismatchCount++;
            }
        }
        frame.currentPreVisibility = null;
        frame.currentRenderHideRequested = false;
    }

    static synchronized void recordLayerVisit(
            AbstractClientPlayer player) {
        MutableFrame frame = frame(player);
        frame.disguiseLayerVisitCount++;
    }

    static synchronized void recordPlayerSubmission(
            AbstractClientPlayer wearer,
            UUID targetUuid,
            EntityModel<?> model,
            ResourceLocation texture,
            GeometrySnapshot geometry) {
        recordSubmission(
                wearer,
                "player",
                targetUuid != null ? targetUuid.toString() : NONE,
                model,
                "net.minecraft.client.renderer.entity.player.PlayerRenderer",
                texture,
                geometry);
    }

    static synchronized void recordEntitySubmission(
            AbstractClientPlayer wearer,
            ResourceLocation targetType,
            EntityModel<?> model,
            EntityRenderer<?> renderer,
            ResourceLocation texture,
            GeometrySnapshot geometry) {
        recordSubmission(
                wearer,
                "entity",
                targetType != null ? targetType.toString() : NONE,
                model,
                renderer != null
                        ? renderer.getClass().getName()
                        : NONE,
                texture,
                geometry);
    }

    static synchronized void clear() {
        CURRENT.clear();
        LAST.clear();
        frameOpen = false;
        latestWearer = null;
        invalidateItemModelCache();
    }

    static synchronized void onResourceReload(
            ResourceManager resourceManager) {
        resourceReloadGeneration++;
        cachedItemModelSnapshot = null;
        cachedItemModelResourceManager = null;
    }

    private static void invalidateItemModelCache() {
        resourceReloadGeneration++;
        cachedItemModelSnapshot = null;
        cachedItemModelResourceManager = null;
    }

    private static void recordSubmission(
            AbstractClientPlayer wearer,
            String targetKind,
            String targetId,
            EntityModel<?> model,
            String rendererClass,
            ResourceLocation texture,
            GeometrySnapshot geometry) {
        MutableFrame frame = frame(wearer);
        frame.disguiseSubmissionCount++;
        frame.frameTargetKind = targetKind;
        frame.frameTargetId = targetId;
        frame.frameModelClass = model != null
                ? model.getClass().getName()
                : NONE;
        frame.frameRendererClass = rendererClass;
        frame.frameTexture = texture != null
                ? texture.toString()
                : NONE;
        GeometrySnapshot submittedGeometry = geometry != null
                ? geometry : GeometrySnapshot.empty();
        frame.frameSubmittedVertexCount +=
                submittedGeometry.vertexCount();
        frame.frameGeometry.merge(submittedGeometry);
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        frame.frameCameraEntityUuid = camera != null
                ? camera.getUUID() : null;
        ResourceLocation cameraType = camera != null
                ? BuiltInRegistries.ENTITY_TYPE.getKey(camera.getType())
                : null;
        frame.frameCameraEntityId = cameraType != null
                ? cameraType.toString() : NONE;
        frame.frameViewRoute = camera != null
                && wearer.getUUID().equals(camera.getUUID())
                && minecraft.options.getCameraType()
                        != CameraType.FIRST_PERSON
                ? OWNER_THIRD_PERSON
                : camera != null
                        && !wearer.getUUID().equals(camera.getUUID())
                                ? TRACKED_OBSERVER
                                : NONE;
    }

    private static MutableFrame frame(AbstractClientPlayer player) {
        ensureFrame();
        latestWearer = player.getUUID();
        MutableFrame frame = CURRENT.computeIfAbsent(
                player.getUUID(), MutableFrame::new);
        frame.observeEquipment(player);
        return frame;
    }

    private static void ensureFrame() {
        if (!frameOpen) {
            beginFrame();
        }
    }

    private static void finishFrame() {
        Set<UUID> wearers = new LinkedHashSet<>(LAST.keySet());
        wearers.addAll(CURRENT.keySet());
        for (UUID wearerUuid : wearers) {
            MutableFrame frame = CURRENT.computeIfAbsent(
                    wearerUuid, MutableFrame::new);
            Snapshot snapshot = frame.freeze(
                    frameSequence,
                    true,
                    LAST.get(wearerUuid),
                    armorPath());
            LAST.put(wearerUuid, snapshot);
        }
        CURRENT.clear();
        frameOpen = false;
    }

    private static ArmorPathSnapshot armorPath() {
        try {
            Item item = FrostTravelerAddon.MEAT_DGS_ITEM.get();
            ItemStack stack = new ItemStack(item);
            IClientItemExtensions extension =
                    IClientItemExtensions.of(item);
            EquipmentSlot slot = item.getEquipmentSlot(stack);
            boolean armorItem = item instanceof ArmorItem;
            boolean defaultExtension =
                    extension == IClientItemExtensions.DEFAULT;
            return new ArmorPathSnapshot(
                    BuiltInRegistries.ITEM.getKey(item).toString(),
                    item.getClass().getName(),
                    slot != null ? slot.name() : NONE,
                    armorItem,
                    armorItem,
                    defaultExtension,
                    extension.getClass().getName(),
                    defaultExtension,
                    itemModel());
        } catch (RuntimeException exception) {
            return ArmorPathSnapshot.unavailable();
        }
    }

    private static ItemModelSnapshot itemModel() {
        ResourceManager resourceManager = Minecraft.getInstance()
                .getResourceManager();
        int managerIdentity = System.identityHashCode(resourceManager);
        if (cachedItemModelSnapshot != null
                && cachedItemModelResourceManager == resourceManager
                && cachedItemModelSnapshot.reloadGeneration()
                        == resourceReloadGeneration
                && cachedItemModelSnapshot.resourceManagerIdentity()
                        == managerIdentity) {
            return cachedItemModelSnapshot;
        }
        ResourceLocation resourceId = ResourceLocation.fromNamespaceAndPath(
                "rotp_zcs", "models/item/meat_dgs.json");
        try {
            var resource = resourceManager.getResource(resourceId);
            if (resource.isEmpty()) {
                cachedItemModelSnapshot =
                        ItemModelSnapshot.unavailable(
                                resourceId.toString(),
                                resourceReloadGeneration,
                                managerIdentity);
                cachedItemModelResourceManager = resourceManager;
                return cachedItemModelSnapshot;
            }
            var selectedResource = resource.orElseThrow();
            try (var input = selectedResource.open()) {
                byte[] bytes = input.readAllBytes();
                JsonObject root = JsonParser.parseString(
                                new String(bytes, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                JsonArray scale = headScale(root);
                boolean scaleExact = scale != null
                        && scale.size() == 3
                        && numericZero(scale.get(0))
                        && numericZero(scale.get(1))
                        && numericZero(scale.get(2));
                cachedItemModelSnapshot = new ItemModelSnapshot(
                        resourceId.toString(),
                        selectedResource.sourcePackId(),
                        sha256(bytes),
                        resourceReloadGeneration,
                        managerIdentity,
                        true,
                        !root.has("elements"),
                        scaleExact,
                        scaleExact ? scale.get(0).getAsDouble() : Double.NaN,
                        scaleExact ? scale.get(1).getAsDouble() : Double.NaN,
                        scaleExact ? scale.get(2).getAsDouble() : Double.NaN);
                cachedItemModelResourceManager = resourceManager;
                return cachedItemModelSnapshot;
            }
        } catch (IOException | RuntimeException exception) {
            cachedItemModelSnapshot =
                    ItemModelSnapshot.unavailable(
                            resourceId.toString(),
                            resourceReloadGeneration,
                            managerIdentity);
            cachedItemModelResourceManager = resourceManager;
            return cachedItemModelSnapshot;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(bytes);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format(
                        Locale.ROOT, "%02X", value & 0xFF));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", exception);
        }
    }

    private static JsonArray headScale(JsonObject root) {
        JsonElement display = root.get("display");
        if (display == null || !display.isJsonObject()) {
            return null;
        }
        JsonElement head = display.getAsJsonObject().get("head");
        if (head == null || !head.isJsonObject()) {
            return null;
        }
        JsonElement scale = head.getAsJsonObject().get("scale");
        return scale != null && scale.isJsonArray()
                ? scale.getAsJsonArray() : null;
    }

    private static boolean numericZero(JsonElement element) {
        return element != null
                && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isNumber()
                && element.getAsDouble() == 0.0D;
    }

    public static synchronized boolean currentItemModelReceiptAccepted(
            ItemModelSnapshot candidate) {
        ItemModelSnapshot selected = itemModel();
        ResourceManager resourceManager = Minecraft.getInstance()
                .getResourceManager();
        return cachedItemModelResourceManager == resourceManager
                && candidate != null
                && candidate.equals(selected)
                && itemModelReceiptAccepted(
                        candidate,
                        selected.sourcePackId(),
                        resourceReloadGeneration,
                        System.identityHashCode(resourceManager));
    }

    public static boolean itemModelReceiptAccepted(
            ItemModelSnapshot candidate,
            String expectedSourcePackId,
            long expectedReloadGeneration,
            int expectedResourceManagerIdentity) {
        return candidate != null
                && FrostTravelerMaskReceiptValidator
                        .itemModelReceiptAccepted(
                                new FrostTravelerMaskReceiptValidator
                                        .ItemModelReceipt(
                                                candidate.resourceId(),
                                                candidate.sourcePackId(),
                                                candidate.contentSha256(),
                                                candidate.reloadGeneration(),
                                                candidate
                                                        .resourceManagerIdentity(),
                                                candidate.resourcePresent(),
                                                candidate.elementsAbsent(),
                                                candidate
                                                        .headScaleExactZero(),
                                                candidate.headScaleX(),
                                                candidate.headScaleY(),
                                                candidate.headScaleZ()),
                                expectedSourcePackId,
                                expectedReloadGeneration,
                                expectedResourceManagerIdentity);
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM
                .getKey(stack.getItem());
        return id != null ? id.toString() : NONE;
    }

    static CountingVertexConsumer counting(VertexConsumer delegate) {
        return new CountingVertexConsumer(delegate);
    }

    static final class CountingVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final GeometryAccumulator geometry =
                new GeometryAccumulator();

        private CountingVertexConsumer(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        int vertexCount() {
            return geometry.vertexCount;
        }

        GeometrySnapshot geometrySnapshot() {
            return geometry.freeze();
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            geometry.recordPosition(x, y, z);
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(
                int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(
                float red, float green, float blue, float alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            delegate.setColor(color);
            return this;
        }

        @Override
        public VertexConsumer setWhiteAlpha(int alpha) {
            delegate.setWhiteAlpha(alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            geometry.recordUv(u, v);
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setLight(int packedLight) {
            delegate.setLight(packedLight);
            return this;
        }

        @Override
        public VertexConsumer setOverlay(int packedOverlay) {
            delegate.setOverlay(packedOverlay);
            return this;
        }

        @Override
        public VertexConsumer setNormal(
                float normalX, float normalY, float normalZ) {
            delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }

        @Override
        public void addVertex(
                float x,
                float y,
                float z,
                int color,
                float u,
                float v,
                int packedOverlay,
                int packedLight,
                float normalX,
                float normalY,
                float normalZ) {
            geometry.recordPosition(x, y, z);
            geometry.recordUv(u, v);
            delegate.addVertex(
                    x,
                    y,
                    z,
                    color,
                    u,
                    v,
                    packedOverlay,
                    packedLight,
                    normalX,
                    normalY,
                    normalZ);
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose,
                BakedQuad quad,
                float red,
                float green,
                float blue,
                float alpha,
                int packedLight,
                int packedOverlay) {
            geometry.recordUninspectedVertices(4);
            delegate.putBulkData(
                    pose,
                    quad,
                    red,
                    green,
                    blue,
                    alpha,
                    packedLight,
                    packedOverlay);
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose,
                BakedQuad quad,
                float[] brightness,
                float red,
                float green,
                float blue,
                float alpha,
                int[] lightmap,
                int packedOverlay,
                boolean readAlpha) {
            geometry.recordUninspectedVertices(4);
            delegate.putBulkData(
                    pose,
                    quad,
                    brightness,
                    red,
                    green,
                    blue,
                    alpha,
                    lightmap,
                    packedOverlay,
                    readAlpha);
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose,
                BakedQuad quad,
                float red,
                float green,
                float blue,
                float alpha,
                int packedLight,
                int packedOverlay,
                boolean readExistingColor) {
            geometry.recordUninspectedVertices(4);
            delegate.putBulkData(
                    pose,
                    quad,
                    red,
                    green,
                    blue,
                    alpha,
                    packedLight,
                    packedOverlay,
                    readExistingColor);
        }

        @Override
        public VertexConsumer misc(
                VertexFormatElement element, int... rawData) {
            delegate.misc(element, rawData);
            return this;
        }

        @Override
        public int applyBakedLighting(
                int packedLight, ByteBuffer data) {
            return delegate.applyBakedLighting(packedLight, data);
        }

        @Override
        public void applyBakedNormals(
                Vector3f generated,
                ByteBuffer data,
                Matrix3f normalTransform) {
            delegate.applyBakedNormals(
                    generated, data, normalTransform);
        }
    }

    private static final class GeometryAccumulator {
        private int vertexCount;
        private int positionSampleCount;
        private int uvSampleCount;
        private int uninspectedVertexCount;
        private boolean allPositionsFinite = true;
        private boolean allUvsFinite = true;
        private float minX = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;
        private float minU = Float.POSITIVE_INFINITY;
        private float maxU = Float.NEGATIVE_INFINITY;
        private float minV = Float.POSITIVE_INFINITY;
        private float maxV = Float.NEGATIVE_INFINITY;

        private void recordPosition(float x, float y, float z) {
            vertexCount++;
            positionSampleCount++;
            allPositionsFinite &= Float.isFinite(x)
                    && Float.isFinite(y) && Float.isFinite(z);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        private void recordUv(float u, float v) {
            uvSampleCount++;
            allUvsFinite &= Float.isFinite(u) && Float.isFinite(v);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }

        private void recordUninspectedVertices(int count) {
            vertexCount += count;
            uninspectedVertexCount += count;
        }

        private void merge(GeometrySnapshot snapshot) {
            vertexCount += snapshot.vertexCount();
            positionSampleCount += snapshot.positionSampleCount();
            uvSampleCount += snapshot.uvSampleCount();
            uninspectedVertexCount += snapshot.uninspectedVertexCount();
            allPositionsFinite &= snapshot.allPositionsFinite();
            allUvsFinite &= snapshot.allUvsFinite();
            if (snapshot.positionSampleCount() > 0) {
                minX = Math.min(minX, snapshot.minX());
                maxX = Math.max(maxX, snapshot.maxX());
                minY = Math.min(minY, snapshot.minY());
                maxY = Math.max(maxY, snapshot.maxY());
                minZ = Math.min(minZ, snapshot.minZ());
                maxZ = Math.max(maxZ, snapshot.maxZ());
            }
            if (snapshot.uvSampleCount() > 0) {
                minU = Math.min(minU, snapshot.minU());
                maxU = Math.max(maxU, snapshot.maxU());
                minV = Math.min(minV, snapshot.minV());
                maxV = Math.max(maxV, snapshot.maxV());
            }
        }

        private GeometrySnapshot freeze() {
            return new GeometrySnapshot(
                    vertexCount,
                    positionSampleCount,
                    uvSampleCount,
                    uninspectedVertexCount,
                    allPositionsFinite,
                    allUvsFinite,
                    minX,
                    maxX,
                    minY,
                    maxY,
                    minZ,
                    maxZ,
                    minU,
                    maxU,
                    minV,
                    maxV);
        }
    }

    private static final class MutableFrame {
        private final UUID wearerUuid;
        private boolean wearerResolved;
        private String headItemId = "minecraft:air";
        private boolean maskEquipped;
        private boolean headSlotResolved;
        private int playerRenderPreCount;
        private int playerRenderPostCount;
        private int disguiseLayerVisitCount;
        private int disguiseSubmissionCount;
        private int scopedHideRequestCount;
        private int visibilityRestoreCheckCount;
        private int visibilityRestoreMismatchCount;
        private VisibilityState currentPreVisibility;
        private boolean currentRenderHideRequested;
        private String frameTargetKind = NONE;
        private String frameTargetId = NONE;
        private String frameModelClass = NONE;
        private String frameRendererClass = NONE;
        private String frameTexture = NONE;
        private int frameSubmittedVertexCount;
        private final GeometryAccumulator frameGeometry =
                new GeometryAccumulator();
        private String frameViewRoute = NONE;
        private UUID frameCameraEntityUuid;
        private String frameCameraEntityId = NONE;

        private MutableFrame(UUID wearerUuid) {
            this.wearerUuid = wearerUuid;
        }

        private void observeEquipment(Player wearer) {
            wearerResolved = true;
            ItemStack head = wearer.getItemBySlot(EquipmentSlot.HEAD);
            headItemId = itemId(head);
            maskEquipped = head.is(
                    FrostTravelerAddon.MEAT_DGS_ITEM.get());
            headSlotResolved = maskEquipped
                    && head.getEquipmentSlot() == EquipmentSlot.HEAD;
        }

        private Snapshot freeze(
                long sequence,
                boolean finalized,
                Snapshot previous,
                ArmorPathSnapshot armorPath) {
            boolean previousEquipped = previous != null
                    && previous.maskEquipped();
            boolean firstAfterUnequip =
                    previousEquipped && !maskEquipped;
            long totalLayerVisits = disguiseLayerVisitCount
                    + (previous != null
                            ? previous.totalDisguiseLayerVisitCount()
                            : 0L);
            long totalSubmissions = disguiseSubmissionCount
                    + (previous != null
                            ? previous.totalDisguiseSubmissionCount()
                            : 0L);
            long totalHideRequests = scopedHideRequestCount
                    + (previous != null
                            ? previous.totalScopedHideRequestCount()
                            : 0L);
            long totalSubmittedVertices = frameSubmittedVertexCount
                    + (previous != null
                            ? previous.totalSubmittedVertexCount()
                            : 0L);
            boolean submitted = disguiseSubmissionCount > 0;
            return new Snapshot(
                    sequence,
                    finalized,
                    wearerUuid,
                    wearerResolved,
                    headItemId,
                    maskEquipped,
                    headSlotResolved,
                    playerRenderPreCount,
                    playerRenderPostCount,
                    disguiseLayerVisitCount,
                    disguiseSubmissionCount,
                    scopedHideRequestCount,
                    visibilityRestoreCheckCount,
                    visibilityRestoreMismatchCount,
                    totalLayerVisits,
                    totalSubmissions,
                    totalHideRequests,
                    frameTargetKind,
                    frameTargetId,
                    frameModelClass,
                    frameRendererClass,
                    frameTexture,
                    frameSubmittedVertexCount,
                    frameGeometry.freeze(),
                    totalSubmittedVertices,
                    frameViewRoute,
                    frameCameraEntityUuid,
                    frameCameraEntityId,
                    submitted
                            ? sequence
                            : previous != null
                                    ? previous.lastSubmissionFrameSequence()
                                    : -1L,
                    submitted
                            ? frameTargetKind
                            : previous != null
                                    ? previous.lastTargetKind()
                                    : NONE,
                    submitted
                            ? frameTargetId
                            : previous != null
                                    ? previous.lastTargetId()
                                    : NONE,
                    submitted
                            ? frameModelClass
                            : previous != null
                                    ? previous.lastModelClass()
                                    : NONE,
                    submitted
                            ? frameRendererClass
                            : previous != null
                                    ? previous.lastRendererClass()
                                    : NONE,
                    submitted
                            ? frameTexture
                            : previous != null
                                    ? previous.lastTexture()
                                    : NONE,
                    previousEquipped,
                    firstAfterUnequip,
                    firstAfterUnequip
                            && disguiseSubmissionCount == 0,
                    firstAfterUnequip
                            && scopedHideRequestCount == 0,
                    armorPath);
        }
    }

    public record Snapshot(
            long frameSequence,
            boolean frameFinalized,
            UUID wearerUuid,
            boolean wearerResolved,
            String headItemId,
            boolean maskEquipped,
            boolean headSlotResolved,
            int playerRenderPreCount,
            int playerRenderPostCount,
            int disguiseLayerVisitCount,
            int disguiseSubmissionCount,
            int scopedHideRequestCount,
            int visibilityRestoreCheckCount,
            int visibilityRestoreMismatchCount,
            long totalDisguiseLayerVisitCount,
            long totalDisguiseSubmissionCount,
            long totalScopedHideRequestCount,
            String frameTargetKind,
            String frameTargetId,
            String frameModelClass,
            String frameRendererClass,
            String frameTexture,
            int frameSubmittedVertexCount,
            GeometrySnapshot frameGeometry,
            long totalSubmittedVertexCount,
            String frameViewRoute,
            UUID frameCameraEntityUuid,
            String frameCameraEntityId,
            long lastSubmissionFrameSequence,
            String lastTargetKind,
            String lastTargetId,
            String lastModelClass,
            String lastRendererClass,
            String lastTexture,
            boolean previousFrameMaskEquipped,
            boolean firstFrameAfterUnequip,
            boolean unequipFrameZeroDisguiseSubmissions,
            boolean unequipFrameZeroScopedHideRequests,
            ArmorPathSnapshot armorPath) {
        public boolean scopedVisibilityRestored() {
            return visibilityRestoreCheckCount > 0
                    && visibilityRestoreMismatchCount == 0;
        }

        public boolean unequipCleanupPassed() {
            return firstFrameAfterUnequip
                    && playerRenderPreCount > 0
                    && disguiseLayerVisitCount > 0
                    && unequipFrameZeroDisguiseSubmissions
                    && unequipFrameZeroScopedHideRequests;
        }

        public String disguiseLayerClass() {
            return DISGUISE_LAYER_CLASS;
        }

        public String scopedVisibilityClass() {
            return SCOPED_VISIBILITY_CLASS;
        }

        private static Snapshot unobserved(
                long sequence,
                UUID wearerUuid,
                ArmorPathSnapshot armorPath) {
            return new Snapshot(
                    sequence,
                    true,
                    wearerUuid,
                    false,
                    "minecraft:air",
                    false,
                    false,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0L,
                    0L,
                    0L,
                    NONE,
                    NONE,
                    NONE,
                    NONE,
                    NONE,
                    0,
                    GeometrySnapshot.empty(),
                    0L,
                    NONE,
                    null,
                    NONE,
                    -1L,
                    NONE,
                    NONE,
                    NONE,
                    NONE,
                    NONE,
                    false,
                    false,
                    false,
                    false,
                    armorPath);
        }
    }

    public record ArmorPathSnapshot(
            String registryId,
            String itemClass,
            String equipmentSlot,
            boolean armorItem,
            boolean humanoidArmorLayerEligible,
            boolean defaultClientExtension,
            String clientExtensionClass,
            boolean customArmorExtensionAbsent,
            ItemModelSnapshot itemModel) {
        public boolean maskArmorPathBlocked() {
            return !humanoidArmorLayerEligible
                    && customArmorExtensionAbsent;
        }

        private static ArmorPathSnapshot unavailable() {
            return new ArmorPathSnapshot(
                    MASK_REGISTRY_ID,
                    NONE,
                    NONE,
                    false,
                    false,
                    false,
                    NONE,
                     false,
                     ItemModelSnapshot.unavailable(
                             MASK_MODEL_RESOURCE_ID,
                             resourceReloadGeneration,
                             0));
        }
    }

    public record ItemModelSnapshot(
            String resourceId,
            String sourcePackId,
            String contentSha256,
            long reloadGeneration,
            int resourceManagerIdentity,
            boolean resourcePresent,
            boolean elementsAbsent,
            boolean headScaleExactZero,
            double headScaleX,
            double headScaleY,
            double headScaleZ) {
        public boolean maskGeometrySuppressed() {
            return resourcePresent
                    && elementsAbsent
                    && headScaleExactZero
                    && headScaleX == 0.0D
                    && headScaleY == 0.0D
                    && headScaleZ == 0.0D;
        }

        private static ItemModelSnapshot unavailable(
                String resourceId,
                long reloadGeneration,
                int resourceManagerIdentity) {
            return new ItemModelSnapshot(
                    resourceId,
                    NONE,
                    NONE,
                    reloadGeneration,
                    resourceManagerIdentity,
                    false,
                    false,
                    false,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN);
        }
    }

    public record GeometrySnapshot(
            int vertexCount,
            int positionSampleCount,
            int uvSampleCount,
            int uninspectedVertexCount,
            boolean allPositionsFinite,
            boolean allUvsFinite,
            float minX,
            float maxX,
            float minY,
            float maxY,
            float minZ,
            float maxZ,
            float minU,
            float maxU,
            float minV,
            float maxV) {
        private static final float POSITION_LIMIT = 8.0F;
        private static final float UV_EPSILON = 0.001F;

        public boolean fullyInspected() {
            return vertexCount > 0
                    && positionSampleCount == vertexCount
                    && uvSampleCount == vertexCount
                    && uninspectedVertexCount == 0;
        }

        public boolean positionBoundsReasonable() {
            return positionSampleCount > 0
                    && finiteOrdered(minX, maxX)
                    && finiteOrdered(minY, maxY)
                    && finiteOrdered(minZ, maxZ)
                    && minX >= -POSITION_LIMIT
                    && minX <= POSITION_LIMIT
                    && maxX >= -POSITION_LIMIT
                    && maxX <= POSITION_LIMIT
                    && minY >= -POSITION_LIMIT
                    && minY <= POSITION_LIMIT
                    && maxY >= -POSITION_LIMIT
                    && maxY <= POSITION_LIMIT
                    && minZ >= -POSITION_LIMIT
                    && minZ <= POSITION_LIMIT
                    && maxZ >= -POSITION_LIMIT
                    && maxZ <= POSITION_LIMIT;
        }

        public boolean uvBoundsReasonable() {
            return uvSampleCount > 0
                    && finiteOrdered(minU, maxU)
                    && finiteOrdered(minV, maxV)
                    && minU >= -UV_EPSILON
                    && minU <= 1.0F + UV_EPSILON
                    && maxU >= -UV_EPSILON
                    && maxU <= 1.0F + UV_EPSILON
                    && minV >= -UV_EPSILON
                    && minV <= 1.0F + UV_EPSILON
                    && maxV >= -UV_EPSILON
                    && maxV <= 1.0F + UV_EPSILON;
        }

        public boolean malformedRingAbsent() {
            return fullyInspected()
                    && allPositionsFinite
                    && allUvsFinite
                    && positionBoundsReasonable()
                    && uvBoundsReasonable();
        }

        public static boolean receiptAccepted(
                GeometrySnapshot candidate) {
            return candidate != null
                    && FrostTravelerMaskReceiptValidator
                            .geometryReceiptAccepted(
                                    new FrostTravelerMaskReceiptValidator
                                            .GeometryReceipt(
                                                    candidate.vertexCount(),
                                                    candidate
                                                            .positionSampleCount(),
                                                    candidate.uvSampleCount(),
                                                    candidate
                                                            .uninspectedVertexCount(),
                                                    candidate
                                                            .allPositionsFinite(),
                                                    candidate.allUvsFinite(),
                                                    candidate.minX(),
                                                    candidate.maxX(),
                                                    candidate.minY(),
                                                    candidate.maxY(),
                                                    candidate.minZ(),
                                                    candidate.maxZ(),
                                                    candidate.minU(),
                                                    candidate.maxU(),
                                                    candidate.minV(),
                                                    candidate.maxV()));
        }

        private static boolean finiteOrdered(float min, float max) {
            return Float.isFinite(min)
                    && Float.isFinite(max)
                    && min <= max;
        }

        private static GeometrySnapshot empty() {
            return new GeometryAccumulator().freeze();
        }
    }

    private record VisibilityState(
            boolean head,
            boolean hat,
            boolean body,
            boolean jacket,
            boolean leftArm,
            boolean leftSleeve,
            boolean rightArm,
            boolean rightSleeve,
            boolean leftLeg,
            boolean leftPants,
            boolean rightLeg,
            boolean rightPants) {
        private static VisibilityState capture(PlayerModel<?> model) {
            return new VisibilityState(
                    model.head.visible,
                    model.hat.visible,
                    model.body.visible,
                    model.jacket.visible,
                    model.leftArm.visible,
                    model.leftSleeve.visible,
                    model.rightArm.visible,
                    model.rightSleeve.visible,
                    model.leftLeg.visible,
                    model.leftPants.visible,
                    model.rightLeg.visible,
                    model.rightPants.visible);
        }
    }
}
