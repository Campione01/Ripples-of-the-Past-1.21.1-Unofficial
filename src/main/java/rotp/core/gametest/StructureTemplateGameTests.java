package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.entity.HamonMasterEntity;
import rotp.core.init.ModBlocks;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.mrpresident.MrPresidentRoomStateOwner;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.worldgen.structure.HamonTempleStructure;
import rotp.core.worldgen.structure.PillarmanTempleStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Core structure templates must load from data/jojo_ripples/structure/ (1.21.1 never reads "structures/"), the Hamon
 * temple rocks sit on the pathway ends as in 1.16, and a template-placed Hamon master gets his Hamon.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StructureTemplateGameTests {
	private StructureTemplateGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void coreStructureTemplatesLoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<ResourceLocation> ids = new ArrayList<>(HamonTempleStructure.templateIds());
		ids.addAll(PillarmanTempleStructure.templateIds());
		ids.add(MrPresidentRoomStateOwner.ROOM_TEMPLATE);
		helper.assertTrue(ids.size() == 15, "Expected 15 core templates, got " + ids.size());
		for (ResourceLocation id : ids) {
			StructureTemplate template = level.getStructureManager().get(id).orElse(null);
			helper.assertTrue(template != null, "Structure template " + id + " does not load");
			helper.assertTrue(template.getSize().getX() > 0 && template.getSize().getY() > 0 && template.getSize().getZ() > 0,
					"Structure template " + id + " is empty: " + template.getSize());
			String stale = findStaleNamespace(template.save(new CompoundTag()));
			helper.assertTrue(stale == null, "Structure template " + id + " still holds the 1.16 id " + stale);
		}
		StructureTemplate room = level.getStructureManager().get(MrPresidentRoomStateOwner.ROOM_TEMPLATE).orElseThrow();
		helper.assertTrue(!room.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), ModBlocks.MR_PRESIDENT_EXIT.get()).isEmpty(),
				"Mr. President room template has no exit gem");
		helper.assertTrue(!MrPresidentRoomStateOwner.placeRoomTemplate(level, JojoMod.resLoc("r148_missing_room_probe"),
				helper.absolutePos(new BlockPos(2, 2, 2))), "A missing room template still reported the room as generated");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void hamonTempleRocksSitOnPathwayEnds(GameTestHelper helper) {
		BlockPos origin = new BlockPos(1000, 120, -2000);
		StructurePiecesBuilder builder = new StructurePiecesBuilder();
		HamonTempleStructure.generatePieces(helper.getLevel().getStructureManager(), origin, builder, RandomSource.create(148L));
		List<BoundingBox> pathways = new ArrayList<>();
		List<BoundingBox> rocks = new ArrayList<>();
		for (StructurePiece piece : builder.build().pieces()) {
			if (piece instanceof HamonTempleStructure.Piece templePiece) {
				String id = templePiece.templateId();
				if (id.endsWith("hamon_temple/pathway")) {
					pathways.add(piece.getBoundingBox());
				}
				else if (id.contains("hamon_temple/rock_")) {
					rocks.add(piece.getBoundingBox());
				}
			}
		}
		helper.assertTrue(pathways.size() == 4 && rocks.size() == 4, "Expected 4 pathways and 4 rocks, got "
				+ pathways.size() + " and " + rocks.size());
		for (int i = 0; i < 4; i++) {
			Rotation rotation = Rotation.values()[i];
			BoundingBox rock = rocks.get(i);
			BoundingBox path = pathways.get(i);
			BlockPos expected = origin.offset(-4, -3, -4).offset(new BlockPos(-21, 0, 0).rotate(rotation));
			helper.assertTrue(rock.minX() == expected.getX() && rock.minY() == expected.getY() && rock.minZ() == expected.getZ(),
					"Rock " + rotation + " placed at " + rock + ", 1.16 corner is " + expected);
			int cx = (rock.minX() + rock.maxX()) / 2;
			int cz = (rock.minZ() + rock.maxZ()) / 2;
			helper.assertTrue(cx >= path.minX() && cx <= path.maxX() && cz >= path.minZ() && cz <= path.maxZ(),
					"Rock " + rotation + " centre (" + cx + ", " + cz + ") is off its pathway " + path);
		}
		helper.succeed();
	}

	// skyAccess: no barrier lid over the 1x1x1 cell, so the placed master does not suffocate
	@GameTest(template = "empty", timeoutTicks = 200, skyAccess = true)
	public static void hamonTempleMasterGetsHamonWhenPlaced(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StructureTemplate building = level.getStructureManager().get(HamonTempleStructure.templateIds().get(0)).orElseThrow();
		BlockPos local = null;
		ListTag entities = building.save(new CompoundTag()).getList("entities", Tag.TAG_COMPOUND);
		for (int i = 0; i < entities.size(); i++) {
			CompoundTag entry = entities.getCompound(i);
			if ("jojo_ripples:hamon_master".equals(entry.getCompound("nbt").getString("id"))) {
				ListTag pos = entry.getList("blockPos", Tag.TAG_INT);
				local = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
			}
		}
		helper.assertTrue(local != null, "hamon_temple/building has no jojo_ripples:hamon_master");
		// Place only the master's own block cell, the way worldgen does (no finalizeSpawn).
		// Use the test's own cell: only its chunk is force-loaded. A cell one block over (the old 1,2,1) sits in the
		// next chunk whenever the random test origin is at chunk-local x or z 15; that chunk is not entity-ticking,
		// the master never ticks and never gets his Hamon, so the test failed at random.
		BlockPos target = helper.absolutePos(new BlockPos(0, 1, 0));
		helper.assertTrue(level.isPositionEntityTicking(target), "Test cell " + target + " is not entity-ticking");
		BlockPos offset = target.subtract(local);
		building.placeInWorld(level, offset, offset, new StructurePlaceSettings().setBoundingBox(new BoundingBox(target)),
				level.getRandom(), 2);
		List<HamonMasterEntity> masters = level.getEntitiesOfClass(HamonMasterEntity.class, new AABB(target).inflate(2));
		helper.assertTrue(masters.size() == 1, "Template placement made " + masters.size() + " Hamon masters");
		HamonMasterEntity master = masters.get(0);
		helper.succeedWhen(() -> {
			helper.assertTrue(PlayerPower.getPowerData(master, ModPlayerPowers.HAMON).isPresent(),
					"Template-placed Hamon master has no Hamon to teach");
			master.discard();
		});
	}

	private static String findStaleNamespace(Tag tag) {
		if (tag instanceof StringTag string) {
			return string.getAsString().startsWith("jojo:") ? string.getAsString() : null;
		}
		if (tag instanceof ListTag list) {
			for (Tag child : list) {
				String found = findStaleNamespace(child);
				if (found != null) {
					return found;
				}
			}
		}
		else if (tag instanceof CompoundTag compound) {
			for (String key : compound.getAllKeys()) {
				String found = findStaleNamespace(compound.get(key));
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}
}
