package unkown.jar.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import unkown.jar.client.render.UnknownModel;
import unkown.jar.client.render.UnknownRenderer;
import unkown.jar.entity.ModEntities;

public class UnkownJarClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityModelLayerRegistry.registerModelLayer(UnknownModel.LAYER, UnknownModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.UNKNOWN, UnknownRenderer::new);
		DeathCrash.init();
	}
}
