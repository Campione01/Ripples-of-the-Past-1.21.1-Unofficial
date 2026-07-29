package com.github.standobyte.jojo.item;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import com.github.standobyte.jojo.item.StandRemoverItem.Mode;

public final class StandRemoverItemContractSmokeTest {
	private static final Map<String, String> TEXTURE_HASHES = Map.of(
			"stand_remover_one_time.png",
			"C1874914ADFF1B9EB5432EEB60FCE97595499ED32DC575469DD8D4482ED6821D",
			"stand_eject_one_time.png",
			"57ABA5B350BA1B562854A8530BC46E14B19A5466B172C2CE43BD03F0673213EA",
			"stand_full_clear_one_time.png",
			"DBF4C2D5206CF4634D20D15368FF8956BBC9B0286C6F1E54FF1D65007C06677A");

	private StandRemoverItemContractSmokeTest() {}

	public static void run() {
		Path root = Path.of(System.getProperty("user.dir"));
		Path assets = root.resolve(
				"src/main/resources/assets/jojo_ripples");

		check("jojo_ripples:stand_remover_one_time".equals(
				Mode.REMOVE.sourceId().toString()),
				"remove transition source ID changed");
		check("jojo_ripples:stand_eject_one_time".equals(
				Mode.EJECT.sourceId().toString()),
				"eject transition source ID changed");
		check("jojo_ripples:stand_full_clear_one_time".equals(
				Mode.FULL_CLEAR.sourceId().toString()),
				"full-clear transition source ID changed");

		for (Map.Entry<String, String> entry : TEXTURE_HASHES.entrySet()) {
			Path texture = assets.resolve("textures/item")
					.resolve(entry.getKey());
			check(entry.getValue().equals(sha256(texture)),
					"donor texture bytes changed: " + entry.getKey());

			String itemId = entry.getKey().replace(".png", "");
			String expectedModel =
					"{\"parent\":\"item/generated\",\"textures\":"
					+ "{\"layer0\":\"jojo_ripples:item/"
					+ itemId + "\"}}";
			check(expectedModel.equals(read(assets.resolve(
					"models/item/" + itemId + ".json")).trim()),
					"adapted donor model changed: " + itemId);
		}

		String language = read(assets.resolve("lang/en_us.json"));
		check(language.contains(
				"\"item.jojo_ripples.stand_remover_one_time\": "
				+ "\"Remove Stand (one-time)\""),
				"remove item donor name missing");
		check(language.contains(
				"\"item.jojo_ripples.stand_eject_one_time\": "
				+ "\"Eject Stand (one-time)\""),
				"eject item donor name missing");
		check(language.contains(
				"\"item.jojo_ripples.stand_full_clear_one_time\": "
				+ "\"Full Stand Clear (one-time)\""),
				"full-clear item donor name missing");

		String itemSource = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/item/"
				+ "StandRemoverItem.java"));
		int discPreflight = itemSource.indexOf(
				"ejectedDisc = StandDiscItem.withStand(exactStand)");
		int extraction = itemSource.indexOf(
				"result = StandPowerTransitions.extract(");
		int appliedCheck = itemSource.indexOf("if (!result.applied())");
		int playerCommit = itemSource.indexOf(
				"if (!useOn(player, player))");
		int playerConsumption = itemSource.indexOf(
				"stack.shrink(1);", playerCommit);
		check(discPreflight >= 0 && discPreflight < extraction,
				"eject disc must be constructed before mutation");
		check(extraction < appliedCheck,
				"eject delivery must check the transaction result");
		check(playerCommit >= 0 && playerCommit < playerConsumption,
				"player item must be consumed only after commit");
		check(itemSource.contains(
				"DispenserBlock.registerBehavior(this"),
				"dispenser behavior missing");
		check(itemSource.contains("ItemUtil.giveItemTo("),
				"exact inventory-or-drop delivery missing");

		String registration = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/init/"
				+ "ModItems.java"));
		for (String itemId : TEXTURE_HASHES.keySet().stream()
				.map(name -> name.replace(".png", ""))
				.toList()) {
			check(registration.contains("\"" + itemId + "\""),
					"one-time item is not registered: " + itemId);
		}
	}

	private static String sha256(Path path) {
		try {
			return HexFormat.of().withUpperCase().formatHex(
					MessageDigest.getInstance("SHA-256")
							.digest(Files.readAllBytes(path)));
		}
		catch (IOException | NoSuchAlgorithmException exception) {
			throw new AssertionError("failed to hash " + path, exception);
		}
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException exception) {
			throw new AssertionError("failed to read " + path, exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
