package com.github.standobyte.jojo.client.entityrender.parsemodel.generic;

import java.util.ArrayList;
import java.util.Map;

import org.joml.Vector3f;

import com.github.standobyte.jojo.client.entityrender.parsemodel.ParseModEntityModel.Utils.RotatedCubeCounter;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class GenericModelFormatSmokeTest {
	private GenericModelFormatSmokeTest() {}

	public static void run() {
		verifyLeafWithoutChildrenParses();
		verifyLeafWithNullChildrenParses();
		verifyNullChildEntryFailsWithPath();
		verifyUnexportedCycleIsPruned();
	}

	private static void verifyLeafWithoutChildrenParses() {
		assertLeafParses("""
				{
				  "resolution": {"width": 16, "height": 16},
				  "elements": [],
				  "outliner": [{
				    "name": "root",
				    "origin": [0, 0, 0],
				    "children": [{
				      "name": "leaf",
				      "origin": [0, 0, 0]
				    }]
				  }]
				}
				""", "missing children");
	}

	private static void verifyLeafWithNullChildrenParses() {
		assertLeafParses("""
				{
				  "resolution": {"width": 16, "height": 16},
				  "elements": [],
				  "outliner": [{
				    "name": "root",
				    "origin": [0, 0, 0],
				    "children": [{
				      "name": "leaf",
				      "origin": [0, 0, 0],
				      "children": null
				    }]
				  }]
				}
				""", "null children");
	}

	private static void verifyNullChildEntryFailsWithPath() {
		try {
			GenericModelFormat.parseGenericModel(JsonParser.parseString("""
					{
					  "resolution": {"width": 16, "height": 16},
					  "elements": [],
					  "outliner": [{
					    "name": "root",
					    "origin": [0, 0, 0],
					    "children": [null]
					  }]
					}
					"""));
			throw new AssertionError("null outliner child must fail");
		}
		catch (JsonParseException error) {
			check(error.getMessage().contains("root[0]"),
					"null child error must include its outliner path");
		}
	}

	private static void verifyUnexportedCycleIsPruned() {
		GenericModelFormat.GroupParsed root =
				group("root", true);
		GenericModelFormat.GroupParsed hidden =
				group("hidden", false);
		root.children().add(hidden);
		hidden.children().add(root);

		GenericModelFormat.recursiveVisitChildren(
				root, Map.of(), new RotatedCubeCounter());
		check(root.children().isEmpty(),
				"unexported group remained in the model tree");
	}

	private static GenericModelFormat.GroupParsed group(
			String name, boolean exported) {
		return new GenericModelFormat.GroupParsed(
				name,
				new Vector3f(),
				null,
				null,
				exported,
				false,
				true,
				0,
				new ArrayList<>());
	}

	private static void assertLeafParses(String json, String caseName) {
		LayerDefinition definition = GenericModelFormat.parseGenericModel(JsonParser.parseString(json));
		PartDefinition root = definition.mesh.getRoot().getChild("root");
		check(root != null, caseName + " model lost its root group");
		check(root.getChild("leaf") != null, caseName + " model lost its leaf group");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
