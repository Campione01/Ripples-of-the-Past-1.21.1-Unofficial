package rotp.core.client.layer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.joml.Matrix4f;

import rotp.core.client.layer.StuckProjectiles.Type;
import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.init.ModItems;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;

// 1.16 MobStuckArrowLayer: stuck arrows and knives on non-player mobs.
public class MobStuckArrowLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
	private static final float[] DEFAULT_SCALE = {
			StuckProjectiles.PLAYER_SCALE, StuckProjectiles.PLAYER_SCALE, StuckProjectiles.PLAYER_SCALE };
	@Nullable private static final Method RENDERER_SCALE = findMethod(LivingEntityRenderer.class, "scale",
			LivingEntity.class, PoseStack.class, float.class);
	@Nullable private static final Method HEAD_PARTS = findMethod(AgeableListModel.class, "headParts");
	@Nullable private static final Method BODY_PARTS = findMethod(AgeableListModel.class, "bodyParts");

	private final LivingEntityRenderer<T, M> renderer;
	@Nullable private EntityModel<T> cachedModel;
	private List<StuckCube> cubes = List.of();
	private float[] areas = new float[0];
	private float totalArea;
	private boolean slime;

	public MobStuckArrowLayer(LivingEntityRenderer<T, M> renderer) {
		super(renderer);
		this.renderer = renderer;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		boolean init = true;
		float[] scaleBack = DEFAULT_SCALE;
		for (Type type : Type.values()) {
			int num = StuckProjectiles.numStuck(type, entity);
			if (num <= 0) {
				continue;
			}
			if (init) {
				initCubes(entity);
				if (!anyCubeVisible()) {
					return;
				}
				scaleBack = scaleBack(entity, partialTick);
				init = false;
			}
			RandomSource random = RandomSource.create(StuckProjectiles.seed(entity, type));
			for (int i = 0; i < num; i++) {
				StuckCube stuckCube = cubes.get(StuckProjectiles.pickWeighted(areas, random.nextFloat() * totalArea));
				ModelPart.Cube cube = stuckCube.cube();
				poseStack.pushPose();
				stuckCube.translateAndRotate(poseStack);
				float[] point = StuckProjectiles.cubePoint(random, slime);
				poseStack.translate(
						Mth.lerp(point[0], cube.minX, cube.maxX) / 16.0F,
						Mth.lerp(point[1], cube.minY, cube.maxY) / 16.0F,
						Mth.lerp(point[2], cube.minZ, cube.maxZ) / 16.0F);
				poseStack.scale(scaleBack[0], scaleBack[1], scaleBack[2]);
				renderProjectile(type, poseStack, buffer, packedLight, entity,
						StuckProjectiles.direction(point[0]),
						StuckProjectiles.direction(point[1]),
						StuckProjectiles.direction(point[2]), partialTick);
				poseStack.popPose();
			}
		}
	}

	// Same projectile placement as vanilla ArrowLayer / 1.16 KnifeLayer.
	static void renderProjectile(Type type, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			Entity entity, float x, float y, float z, float partialTick) {
		AbstractArrow projectile = switch (type) {
		case ARROW -> new Arrow(entity.level(), entity.getX(), entity.getY(), entity.getZ(), ItemStack.EMPTY, null);
		case KNIFE -> new KnifeEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(),
				new ItemStack(ModItems.KNIFE.get()));
		};
		projectile.setYRot(StuckProjectiles.yRot(x, z));
		projectile.setXRot(StuckProjectiles.xRot(x, y, z));
		projectile.yRotO = projectile.getYRot();
		projectile.xRotO = projectile.getXRot();
		Minecraft.getInstance().getEntityRenderDispatcher().render(projectile, 0.0D, 0.0D, 0.0D, 0.0F, partialTick,
				poseStack, buffer, packedLight);
	}

	private float[] scaleBack(T entity, float partialTick) {
		if (RENDERER_SCALE == null) {
			return DEFAULT_SCALE;
		}
		try {
			PoseStack scaled = new PoseStack();
			RENDERER_SCALE.invoke(renderer, entity, scaled, partialTick);
			Matrix4f pose = scaled.last().pose();
			return StuckProjectiles.scaleBack(pose.m00(), pose.m11(), pose.m22());
		}
		catch (ReflectiveOperationException | RuntimeException e) {
			return DEFAULT_SCALE;
		}
	}

	private boolean anyCubeVisible() {
		if (totalArea <= 0) {
			return false;
		}
		for (StuckCube cube : cubes) {
			if (cube.part().visible) {
				return true;
			}
		}
		return false;
	}

	// Rebuilt when the renderer swaps its model (e.g. tropical fish).
	private void initCubes(T entity) {
		M model = getParentModel();
		if (model == cachedModel) {
			return;
		}
		cachedModel = model;
		slime = entity instanceof Slime;
		cubes = model != null ? collectCubes(rootParts(model)) : List.of();
		areas = new float[cubes.size()];
		totalArea = 0;
		for (int i = 0; i < areas.length; i++) {
			areas[i] = cubes.get(i).area();
			totalArea += areas[i];
		}
	}

	private static List<ModelPart> rootParts(EntityModel<?> model) {
		List<ModelPart> parts = new ArrayList<>();
		if (model instanceof HierarchicalModel<?> hierarchical) {
			parts.add(hierarchical.root());
		}
		else if (model instanceof ListModel<?> list) {
			list.parts().forEach(parts::add);
		}
		else if (model instanceof AgeableListModel<?> ageable) {
			addInvoked(parts, HEAD_PARTS, ageable);
			addInvoked(parts, BODY_PARTS, ageable);
		}
		if (parts.isEmpty()) {
			// Other models: every ModelPart field, as the 1.16 fallback did.
			for (Class<?> c = model.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
				for (Field field : c.getDeclaredFields()) {
					if (Modifier.isStatic(field.getModifiers())) {
						continue;
					}
					try {
						if (ModelPart.class.isAssignableFrom(field.getType())) {
							field.setAccessible(true);
							parts.add((ModelPart) field.get(model));
						}
						else if (ModelPart[].class.isAssignableFrom(field.getType())) {
							field.setAccessible(true);
							ModelPart[] array = (ModelPart[]) field.get(model);
							if (array != null) {
								parts.addAll(List.of(array));
							}
						}
					}
					catch (ReflectiveOperationException | RuntimeException ignored) {}
				}
			}
		}
		return parts;
	}

	private static void addInvoked(List<ModelPart> parts, @Nullable Method method, Object model) {
		if (method == null) {
			return;
		}
		try {
			if (method.invoke(model) instanceof Iterable<?> iterable) {
				for (Object part : iterable) {
					if (part instanceof ModelPart modelPart) {
						parts.add(modelPart);
					}
				}
			}
		}
		catch (ReflectiveOperationException | RuntimeException ignored) {}
	}

	// Every cube with its parent chain; a part reached deeper in the tree keeps the longer chain (1.16).
	private static List<StuckCube> collectCubes(List<ModelPart> roots) {
		Map<ModelPart, List<ModelPart>> parents = new IdentityHashMap<>();
		// Discovery order keeps the cube list, and so the positions, stable.
		List<ModelPart> order = new ArrayList<>();
		List<ModelPart> generation = new ArrayList<>();
		for (ModelPart root : roots) {
			if (root != null && parents.putIfAbsent(root, List.of()) == null) {
				order.add(root);
				generation.add(root);
			}
		}
		while (!generation.isEmpty()) {
			List<ModelPart> next = new ArrayList<>();
			for (ModelPart parent : generation) {
				List<ModelPart> parentChain = parents.get(parent);
				if (parentChain.size() >= 64) {
					continue;
				}
				List<ModelPart> chain = new ArrayList<>(parentChain);
				chain.add(parent);
				for (ModelPart child : parent.children.values()) {
					List<ModelPart> known = parents.get(child);
					if (known == null || known.size() < chain.size()) {
						if (known == null) {
							order.add(child);
						}
						parents.put(child, chain);
						next.add(child);
					}
				}
			}
			generation = next;
		}
		List<StuckCube> cubes = new ArrayList<>();
		for (ModelPart part : order) {
			List<ModelPart> chain = parents.get(part);
			for (ModelPart.Cube cube : part.cubes) {
				cubes.add(new StuckCube(cube, part, chain, StuckProjectiles.cubeArea(
						cube.minX, cube.minY, cube.minZ, cube.maxX, cube.maxY, cube.maxZ)));
			}
		}
		return cubes;
	}

	@Nullable
	private static Method findMethod(Class<?> owner, String name, Class<?>... params) {
		try {
			Method method = owner.getDeclaredMethod(name, params);
			method.setAccessible(true);
			return method;
		}
		catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private record StuckCube(ModelPart.Cube cube, ModelPart part, List<ModelPart> parents, float area) {
		void translateAndRotate(PoseStack poseStack) {
			for (ModelPart parent : parents) {
				parent.translateAndRotate(poseStack);
			}
			part.translateAndRotate(poseStack);
		}
	}
}
