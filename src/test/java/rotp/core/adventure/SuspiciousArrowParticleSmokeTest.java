package rotp.core.adventure;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonParser;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import rotp.core.client.particle.type.OnomatopoeiaParticle;

/** Factory execution and emitter wiring checks; filtered spawning also needs a client run. */
public final class SuspiciousArrowParticleSmokeTest {
	private SuspiciousArrowParticleSmokeTest() {}

	public static void main(String[] args) throws Exception {
		SpriteSet sprites = new SpriteSet() {
			public TextureAtlasSprite get(int age, int lifetime) { return null; }
			public TextureAtlasSprite get(RandomSource random) { return null; }
		};
		Particle afk = new OnomatopoeiaParticle.GoFactory(sprites)
				.createParticle(null, null, 0, 0, 0, 0, 0, 0);
		check(afk.getLifetime() == 400, "AFK MENACING default remains 400 ticks");
		Particle doParticle = new OnomatopoeiaParticle.DoFactory(sprites)
				.createParticle(null, null, 0, 0, 0, 0, 0, 0);
		check(doParticle.getLifetime() == 40, "DO particle remains 40 ticks");
		String arrow = Files.readString(Path.of("src/main/java/rotp/core/adventure/SpawnArrowsInSusBlocks.java"));
		String body = arrow.substring(arrow.indexOf("public static void onItemSynchedToClient("),
				arrow.indexOf("public static Vec3 randomPointAroundBlock("));
		check(body.contains("particle.setLifetime(40)") && body.contains("if (particle != null)"),
				"brushable-arrow emitter overrides only its returned particle");
		check(body.contains(".jojo_ripples$addParticle(ModParticles.MENACING.get(), false,")
				&& !body.contains("particleEngine") && !body.contains("level.addParticle("),
				"brushable-arrow spawn keeps vanilla distance and particle-setting filters");
		check(body.contains("tickCount % 3 == 0") && body.contains("!ClientProxy.isClientPaused()")
				&& body.contains(".menacingParticles") && body.contains("!brushableBlockEntity.isRemoved()")
				&& body.contains("randomPointAroundBlock(blockPos, 1.25, 2)"), "emission conditions unchanged");
		String emitter = Files.readString(Path.of("src/main/java/rotp/core/client/particle/type/custom/MenacingParticleEmitter.java"));
		check(emitter.contains("particle.setLifetime(200)"), "entity emitter keeps its 200-tick override");
		String invoker = Files.readString(Path.of("src/main/java/rotp/core/mixin/client/particle/LevelRendererParticleInvoker.java"));
		check(invoker.contains("@Mixin(LevelRenderer.class)") && invoker.contains("@Invoker(\"addParticleInternal\")"),
				"returned particle comes from the vanilla filtered method");
		var mixins = JsonParser.parseString(Files.readString(Path.of("src/main/resources/jojo_ripples.mixins.json")))
				.getAsJsonObject().getAsJsonArray("client");
		check(mixins.asList().stream().anyMatch(e -> e.getAsString().equals("client.particle.LevelRendererParticleInvoker")),
				"particle invoker is wired on the client");
		System.out.println("Suspicious-arrow particle smoke tests passed: two factories and six emitter/filter wiring checks");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
