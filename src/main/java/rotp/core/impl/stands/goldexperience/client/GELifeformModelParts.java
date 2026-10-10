package rotp.core.impl.stands.goldexperience.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import rotp.core.compat.v1_21_4.missingmethods.Model_1_21_2plus;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Every part of a lifeform model that the growth animation moves, with all descendants. Only Stand and humanoid
 * models are given their root in 1.21.1; for every other vanilla model (cow, chicken, villager, ...) the parts are
 * read from the model's root or part list and its own fields, as the 1.16.5 renderer did.
 */
public final class GELifeformModelParts {
    private GELifeformModelParts() {}

    public static List<ModelPart> collect(EntityModel<?> model) {
        Set<ModelPart> found = Collections.newSetFromMap(new IdentityHashMap<>());
        if (model instanceof Model_1_21_2plus modelPlus) {
            List<ModelPart> known = modelPlus.jojo_ripples$allParts();
            if (known != null) {
                found.addAll(known);
            }
        }
        if (found.isEmpty()) {
            List<ModelPart> roots = new ArrayList<>();
            if (model instanceof HierarchicalModel<?> hierarchical) {
                roots.add(hierarchical.root());
            }
            else if (model instanceof ListModel<?> list) {
                list.parts().forEach(roots::add);
            }
            for (Class<?> type = model.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        addFieldParts(field, model, roots);
                    }
                }
            }
            roots.stream().filter(root -> root != null).forEach(root -> root.getAllParts().forEach(found::add));
        }
        return List.copyOf(found);
    }

    private static void addFieldParts(Field field, Object model, List<ModelPart> parts) {
        Class<?> fieldType = field.getType();
        boolean single = ModelPart.class.isAssignableFrom(fieldType);
        boolean array = ModelPart[].class.isAssignableFrom(fieldType);
        if ((!single && !array) || !field.trySetAccessible()) {
            return;
        }
        try {
            Object value = field.get(model);
            if (single && value != null) {
                parts.add((ModelPart) value);
            }
            else if (array && value != null) {
                for (ModelPart part : (ModelPart[]) value) {
                    if (part != null) {
                        parts.add(part);
                    }
                }
            }
        }
        catch (IllegalAccessException | RuntimeException ignored) {
        }
    }
}
