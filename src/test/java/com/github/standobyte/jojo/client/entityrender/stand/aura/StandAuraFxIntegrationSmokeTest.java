package com.github.standobyte.jojo.client.entityrender.stand.aura;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.github.standobyte.jojo.config.client.ClientModSettings;
import com.github.standobyte.jojo.config.client.StandAuraSettings;

public final class StandAuraFxIntegrationSmokeTest {
    private StandAuraFxIntegrationSmokeTest() {}

    public static void main(String[] args) throws Exception {
        verifyDefaults();
        verifyModeAndSanitization();
        verifyPersistence();
        verifyIdentityQueue();
        verifyProductionWiring(Path.of(requiredProperty(
                "standAuraProjectDir")));
        System.out.println(
                "Stand Aura FX integration smoke test passed: "
                        + "upstreamMode=AUTO, parameters=23, "
                        + "persistentPanel=true, coreMask=true.");
    }

    private static void verifyDefaults() {
        StandAuraSettings settings = new StandAuraSettings();
        check(settings.enabled, "upstream AUTO must start enabled");
        check(settings.mode == StandAuraSettings.Mode.AUTO,
                "upstream mode default changed");
        List<String> expected = List.of(
                "chaos:0.2:0.0:1.0",
                "globalAlpha:0.9:0.0:2.0",
                "framebufferScale:0.5:0.25:1.0",
                "shapeScaleX:0.42:0.05:2.0",
                "shapeScaleY:0.68:0.05:2.0",
                "shapeOffsetX:0.0:-1.0:1.0",
                "shapeOffsetY:0.05:-1.0:1.0",
                "baseAuraWidth:0.05:0.0:0.5",
                "auraWidthChaos:0.028:0.0:0.5",
                "edgeWarpStrength:0.01:0.0:0.2",
                "auraThickness:0.45:0.0:2.0",
                "noiseScale:10.0:0.0:100.0",
                "fillAlphaBase:0.32:0.0:2.0",
                "fillAlphaFlow:0.2:0.0:2.0",
                "coreAlpha:0.16:0.0:2.0",
                "edgeAlphaBase:0.8:0.0:2.0",
                "edgeAlphaFlow:0.25:0.0:2.0",
                "rimAlpha:0.42:0.0:2.0",
                "innerHighlightBase:0.35:0.0:2.0",
                "innerHighlightFlow:0.2:0.0:2.0",
                "outerHighlightBase:0.7:0.0:2.0",
                "outerHighlightFlow:0.25:0.0:2.0",
                "edgeHighlightStrength:0.55:0.0:2.0");
        check(StandAuraSettings.PARAMETERS.size() == expected.size(),
                "upstream parameter count changed");
        for (int index = 0; index < expected.size(); index++) {
            String[] spec = expected.get(index).split(":");
            StandAuraSettings.Parameter parameter =
                    StandAuraSettings.PARAMETERS.get(index);
            check(parameter.name().equals(spec[0])
                            && parameter.defaultValue()
                                    == Float.parseFloat(spec[1])
                            && parameter.minimum()
                                    == Float.parseFloat(spec[2])
                            && parameter.maximum()
                                    == Float.parseFloat(spec[3])
                            && parameter.get(settings)
                                    == parameter.defaultValue(),
                    "upstream parameter drifted: " + spec[0]);
        }
    }

    private static void verifyModeAndSanitization() {
        StandAuraSettings settings = new StandAuraSettings();
        check(!settings.automaticAura(false, true),
                "AUTO ignored Resolve ownership");
        check(settings.automaticAura(true, false),
                "AUTO lost Resolve ownership");
        settings.mode = StandAuraSettings.Mode.OPEN;
        check(settings.automaticAura(false, true),
                "OPEN lost Stand-power ownership");
        settings.enabled = false;
        check(!settings.automaticAura(true, true),
                "disabled aura retained visual behavior");

        settings.chaos = Float.NaN;
        settings.globalAlpha = 99.0F;
        settings.framebufferScale = -1.0F;
        settings.mode = null;
        settings.sanitize();
        check(settings.chaos == 0.2F
                        && settings.globalAlpha == 2.0F
                        && settings.framebufferScale == 0.25F
                        && settings.mode == StandAuraSettings.Mode.AUTO,
                "persistent aura values were not sanitized");
    }

    private static void verifyIdentityQueue() {
        FrameRequestQueue<EqualTarget> queue =
                new FrameRequestQueue<>();
        EqualTarget first = new EqualTarget("same");
        EqualTarget second = new EqualTarget("same");
        queue.queue(first, null);
        queue.queue(second, 0x123456);
        check(queue.sizeForTest() == 2,
                "equal targets were merged by value");
        check(queue.consume(first).color() == null
                        && queue.consume(second).color() == 0x123456,
                "exact-target request color drifted");
        queue.queue(first, 0xABCDEF);
        queue.queue(first, null);
        check(queue.consume(first).color() == 0xABCDEF,
                "default request erased explicit color");
        queue.queue(first, null);
        queue.clear();
        check(queue.sizeForTest() == 0,
                "frame request leaked after cleanup");
    }

    private static void verifyPersistence() throws Exception {
        Path directory = Files.createTempDirectory(
                "stand-aura-settings-");
        Path settingsFile = directory.resolve("client_settings.json");
        try {
            Files.writeString(settingsFile, """
                    {
                      "standAura": {
                        "enabled": false,
                        "mode": "OPEN",
                        "chaos": 0.73,
                        "globalAlpha": 99.0,
                        "framebufferScale": -1.0
                      }
                    }
                    """);
            ClientModSettings.init(settingsFile.toFile());
            StandAuraSettings loaded =
                    ClientModSettings.getSettingsReadOnly().standAura;
            check(!loaded.enabled
                            && loaded.mode == StandAuraSettings.Mode.OPEN
                            && loaded.chaos == 0.73F
                            && loaded.globalAlpha == 2.0F
                            && loaded.framebufferScale == 0.25F,
                    "nested aura settings did not load or sanitize");
            ClientModSettings.edit(settings -> {
                settings.standAura.enabled = true;
                settings.standAura.mode = StandAuraSettings.Mode.AUTO;
                settings.standAura.noiseScale = 12.5F;
            }, false);
            String saved = Files.readString(settingsFile);
            require(saved, "\"standAura\"");
            require(saved, "\"enabled\": true");
            require(saved, "\"mode\": \"AUTO\"");
            require(saved, "\"noiseScale\": 12.5");
        }
        finally {
            Files.deleteIfExists(settingsFile);
            Files.deleteIfExists(directory);
        }
    }

    private static void verifyProductionWiring(Path root)
            throws Exception {
        String renderer = read(root,
                "src/main/java/com/github/standobyte/jojo/client/"
                        + "entityrender/stand/StandEntityRenderer.java");
        String setup = read(root,
                "src/main/java/com/github/standobyte/jojo/client/"
                        + "ClientSetup.java");
        String shaders = read(root,
                "src/main/java/com/github/standobyte/jojo/client/"
                        + "shader/ModShaders.java");
        String privateTargetShader = read(root,
                "src/main/java/com/github/standobyte/jojo/client/"
                        + "shader/core/PrivateTargetShaderInstance.java");
        String standRenderTypes = read(root,
                "src/main/java/com/github/standobyte/jojo/client/"
                        + "rendertype/ModRenderTypes.java");
        String standTranslucentFragment = read(root,
                "src/main/resources/assets/jojo_ripples/shaders/core/"
                        + "stand_translucent.fsh");
        String standTranslucencyFramebuffer = read(root,
                "src/main/java/com/github/standobyte/jojo/client/"
                        + "shader/StandTranslucencyFramebuffer.java");
        String entityMask = read(root,
                "src/main/java/com/github/standobyte/jojo/api/client/"
                        + "render/EntityMaskPostEffect.java");
        String auraShaders = read(root,
                "src/main/java/com/github/standobyte/jojo/client/"
                        + "entityrender/stand/aura/StandAuraShaders.java");
        String screen = read(root,
                "src/main/java/com/github/standobyte/jojo/powersystem/"
                        + "standpower/client_screens/"
                        + "StandDisplaySettingsScreen.java");
        String layer = read(root,
                "src/main/java/com/github/standobyte/jojo/client/"
                        + "entityrender/stand/aura/StandAuraLayer.java");
        String livingLayer = read(root,
                "src/main/java/com/github/standobyte/jojo/client/"
                        + "entityrender/stand/aura/LivingAuraLayer.java");
        String compositor = read(root,
                "src/main/java/com/github/standobyte/jojo/client/"
                        + "entityrender/stand/aura/"
                        + "StandAuraMaskCompositor.java");
        String fragment = read(root,
                "src/main/resources/assets/jojo_ripples/shaders/core/"
                        + "stand_aura_composite.fsh");
        String notice = read(root, "THIRD_PARTY_NOTICES.md");
        String packagedNotice = read(root,
                "src/main/resources/META-INF/"
                        + "stand-aura-fx-notice.md");
        String legacyApi = read(root,
                "src/main/java/com/inza/standaurafx/api/"
                        + "StandAuraFxApi.java");

        require(renderer, "new StandAuraLayer<>(this)");
        require(setup, "StandAuraFxClient.register()");
        require(shaders, "StandAuraShaders.loadCoreShader(event)");
        require(shaders, "loadPrivateTargetCoreShader(event");
        require(shaders,
                "loadPrivateTargetCoreShader(event, "
                        + "JojoMod.resLoc(\"stand_translucent\")");
        require(shaders, "privateTargetBlit");
        require(shaders, "privateTargetEntityMask");
        require(shaders,
                "ResourceLocation.withDefaultNamespace(\"position_tex\")");
        require(shaders,
                "ResourceLocation.withDefaultNamespace("
                        + "\"rendertype_entity_solid\")");
        check(!shaders.contains(
                        "GameRenderer.getRendertypeEntitySolidShader"),
                "private mask shader can fall back to Iris's world target");
        require(privateTargetShader, "public boolean iris$skipDraw()");
        require(privateTargetShader, "return true;");
        check(!standRenderTypes.contains(
                        "IrisShaderPipelineCompat.isShaderPackInUse()"),
                "Iris still replaces Stand vertex alpha with a pack entity shader");
        require(standRenderTypes,
                "return STAND_TRANSLUCENT.apply(texture, "
                        + "new StandTranslucentState(outline, false));");
        require(standRenderTypes,
                "return STAND_TRANSLUCENT.apply(texture, "
                        + "new StandTranslucentState(true, true));");
        require(standTranslucentFragment,
                "color *= vertexColor * ColorModulator;");
        require(standTranslucencyFramebuffer,
                "stage == RenderLevelStageEvent.Stage.AFTER_LEVEL");
        require(standTranslucencyFramebuffer,
                "ModShaders.loadPrivateTargetCoreShader(event, "
                        + "JojoMod.resLoc(\"stand_translucency_composite\")");
        require(standTranslucencyFramebuffer,
                "Minecraft.getInstance().getMainRenderTarget()");
        require(standTranslucencyFramebuffer,
                "copyDepthFrom(sceneDepthBuffer, mainTarget.frameBufferId");
        require(standTranslucencyFramebuffer,
                "mainTarget.bindWrite(true);");
        check(!standTranslucencyFramebuffer.contains(
                        "CustomLevelRenderStages.BEFORE_SPECTATOR_SHADER"),
                "Stand alpha composite still runs inside Iris's world pipeline");
        require(auraShaders, "ModShaders.loadPrivateTargetCoreShader(");
        require(entityMask, "RenderLevelStageEvent.Stage.AFTER_LEVEL");
        require(entityMask, "blitAuraToMain(");
        require(entityMask, "() -> mainTarget.bindWrite(true)");
        check(!entityMask.contains(
                        "RenderLevelStageEvent.Stage.AFTER_ENTITIES"),
                "aura result still enters the pre-composite Iris G-buffer");
        require(entityMask, "ModShaders.privateTargetBlit()");
        require(entityMask, "ModShaders"
                + "::privateTargetEntityMask");
        check(!entityMask.contains("GameRenderer"
                        + "::getRendertypeEntitySolidShader"),
                "mask rerender still uses the Iris-owned world shader");
        requireOrder(
                entityMask,
                "ShaderInstance shader = ModShaders.privateTargetBlit();",
                "shader.setSampler(\"Sampler0\", aura.getColorTextureId());",
                "drawClipQuad(");
        requireOrder(
                entityMask,
                "shader.apply();",
                "bindDrawTarget.run();",
                "BufferUploader.draw(builder.buildOrThrow());");
        check(!entityMask.contains("BufferUploader.drawWithShader("),
                "shader apply can still rebind a private target during draw");
        require(screen, "StandAuraSettings.PARAMETERS");
        require(screen, "settings.standAura.enabled");
        require(layer, "state.visualContext.effectiveAlpha()");
        require(layer, "ModRenderTypes.standTranslucent(texture)");
        require(compositor, "EntityMaskPostEffect.register(");
        requireOrder(
                compositor,
                "ClientRenderCompatibility.snapshot()",
                ".irisShaderPackInUse()) {",
                "return false;");
        requireOrder(
                compositor,
                ".irisShaderPackInUse()) {",
                "return false;",
                "StandAuraSettings settings = settings();");
        requireOrder(
                compositor,
                "StandAuraSettings settings = settings();",
                "current.setOutputScale(settings.framebufferScale);",
                "return current.queue(");
        requireOrder(
                layer,
                "if (StandAuraMaskCompositor.queue(",
                "return;",
                "ModRenderTypes.standTranslucent(texture)");
        requireOrder(
                livingLayer,
                "if (StandAuraMaskCompositor.queue(",
                "return;",
                "RenderType.entityTranslucent(texture(entity))");
        require(fragment, "const int DIRECTION_COUNT = 16;");
        require(fragment, "const int STEP_COUNT = 48;");
        require(fragment, "uniform sampler2D uSceneDepthTex;");
        require(fragment, "vec2 maskUv(vec2 p)");
        require(fragment,
                "vec2 relativeScale = max(");
        require(fragment,
                "vec2 relativeOffset = uShapeOffset - vec2(0.0, 0.05);");
        require(fragment,
                "vec2 q = effectCoordinates(p) * mix(");
        require(fragment,
                "mix(uMaskUvMin, uMaskUvMax, vUv)");
        check(!fragment.contains("shapeToMaskUv"),
                "shape controls moved the entity mask or depth lookup");
        require(legacyApi,
                "com.github.standobyte.jojo.api.client.render.StandAuraFx");
        require(legacyApi, "public static void renderAura(Entity entity)");
        require(legacyApi,
                "public static void renderAura(Entity entity, int auraColor)");
        require(notice, "6f36008b37bc7165a8c1fd594b246923557dc417");
        require(packagedNotice,
                "6f36008b37bc7165a8c1fd594b246923557dc417");
        check(!readTree(root.resolve(
                        "src/main/java/com/github/standobyte/jojo/client/"
                                + "entityrender/stand/aura"))
                        .contains("net.irisshaders")
                        && !readTree(root.resolve(
                                "src/main/java/com/github/standobyte/jojo/"
                                        + "client/entityrender/stand/aura"))
                                .contains("superresolution"),
                "aura integration added private Iris/SR hooks");
    }

    private static String read(Path root, String relative)
            throws Exception {
        return Files.readString(root.resolve(relative));
    }

    private static String readTree(Path root) throws Exception {
        StringBuilder result = new StringBuilder();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .sorted().toList()) {
                result.append(Files.readString(file)).append('\n');
            }
        }
        return result.toString().toLowerCase();
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        check(value != null && !value.isBlank(),
                "missing system property: " + name);
        return value;
    }

    private static void require(String text, String token) {
        check(text.contains(token), "missing production token: " + token);
    }

    private static void requireOrder(
            String text, String first, String second, String third) {
        int firstIndex = text.indexOf(first);
        int secondIndex = text.indexOf(second, firstIndex + 1);
        int thirdIndex = text.indexOf(third, secondIndex + 1);
        check(firstIndex >= 0
                        && secondIndex > firstIndex
                        && thirdIndex > secondIndex,
                "production tokens are out of order: "
                        + first + " -> " + second + " -> " + third);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record EqualTarget(String value) {}
}
