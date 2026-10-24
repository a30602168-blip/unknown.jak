package unkown.jar.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import unkown.jar.UnkownJar;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> UNKNOWN_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, UnkownJar.id("unknown"));

	public static final EntityType<UnknownEntity> UNKNOWN = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			UNKNOWN_KEY,
			EntityType.Builder.<UnknownEntity>of(UnknownEntity::new, MobCategory.MONSTER)
					.sized(0.6F, 1.95F)
					.eyeHeight(1.74F)
					.clientTrackingRange(10)
					.fireImmune()
					.build(UNKNOWN_KEY));

	public static void init() {
		FabricDefaultAttributeRegistry.register(UNKNOWN, UnknownEntity.createAttributes());
	}

	private ModEntities() {
	}
}
