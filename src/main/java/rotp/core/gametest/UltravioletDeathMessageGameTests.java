package rotp.core.gametest;

import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.LightBeamEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltravioletDeathMessageGameTests {
	private UltravioletDeathMessageGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void beamDeathMessagesPreserveAttributionAndUltravioletTags(GameTestHelper helper) {
		Player shooter = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player victim = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		ProbeBeam beam = new ProbeBeam(helper.getLevel());
		try {
			DamageSource beamSource = beam.source(shooter);
			DamageSource sunlight = DamageUtil.make(helper.getLevel(), ModDamageTypes.ULTRAVIOLET);
			helper.assertTrue(beamSource.is(ModDamageTypes.ULTRAVIOLET_ENTITY)
					&& beamSource.getDirectEntity() == beam && beamSource.getEntity() == shooter,
					"The production beam source must retain direct beam and causing shooter");
			for (TagKey<DamageType> tag : List.of(DamageTypeTags.BYPASSES_ARMOR, DamageTypeTags.BYPASSES_WOLF_ARMOR,
					DamageTypeTags.BYPASSES_ENCHANTMENTS, DamageTypeTags.BYPASSES_EFFECTS, Tags.DamageTypes.IS_ENVIRONMENT)) {
				helper.assertTrue(sunlight.is(tag) && beamSource.is(tag), "Beam UV must retain the original tag " + tag.location());
			}
			helper.assertTrue(beamSource.type().scaling() == sunlight.type().scaling()
					&& beamSource.getFoodExhaustion() == sunlight.getFoodExhaustion(), "UV source semantics must stay unchanged");
			message(helper, beamSource.getLocalizedDeathMessage(victim), "death.attack.ultraviolet.entity", 2,
					victim.getDisplayName(), shooter.getDisplayName());
			ItemStack named = new ItemStack(Items.STICK);
			named.set(DataComponents.CUSTOM_NAME, Component.literal("UV test item"));
			shooter.setItemSlot(EquipmentSlot.MAINHAND, named);
			message(helper, beamSource.getLocalizedDeathMessage(victim), "death.attack.ultraviolet.entity.item", 3,
					victim.getDisplayName(), shooter.getDisplayName(), named.getDisplayName());
			DamageSource ownerless = beam.source(null);
			message(helper, ownerless.getLocalizedDeathMessage(victim), "death.attack.ultraviolet.entity", 2,
					victim.getDisplayName(), beam.getDisplayName());
			message(helper, sunlight.getLocalizedDeathMessage(victim), "death.attack.ultraviolet", 1, victim.getDisplayName());
		}
		finally {
			beam.discard();
			shooter.discard();
			victim.discard();
		}
		helper.succeed();
	}

	private static void message(GameTestHelper helper, Component component, String key, int count, Component... args) {
		helper.assertTrue(component.getContents() instanceof TranslatableContents, "Death message must be translatable");
		TranslatableContents contents = (TranslatableContents) component.getContents();
		helper.assertTrue(contents.getKey().equals(key) && contents.getArgs().length == count, "Wrong UV message key/argument count");
		for (int i = 0; i < args.length; i++) {
			helper.assertTrue(args[i].equals(contents.getArgs()[i]), "Wrong UV message argument " + i);
		}
	}

	private static final class ProbeBeam extends LightBeamEntity {
		private ProbeBeam(Level level) {
			super(ModEntityTypes.AJA_STONE_BEAM.get(), level);
		}

		private DamageSource source(LivingEntity owner) {
			return getDamageSource(owner);
		}
	}
}
