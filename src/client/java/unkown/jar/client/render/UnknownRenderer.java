package unkown.jar.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import unkown.jar.UnkownJar;
import unkown.jar.entity.UnknownEntity;

public class UnknownRenderer extends HumanoidMobRenderer<UnknownEntity, UnknownRenderState, UnknownModel> {
	private static final Identifier[] TEXTURES = {
			UnkownJar.id("textures/entity/phase1.png"),
			UnkownJar.id("textures/entity/phase1.png"),
			UnkownJar.id("textures/entity/phase2.png"),
			UnkownJar.id("textures/entity/phase3.png"),
			UnkownJar.id("textures/entity/phase4.png")
	};

	public UnknownRenderer(EntityRendererProvider.Context context) {
		super(context, new UnknownModel(context.bakeLayer(UnknownModel.LAYER)), 0.5F);
	}

	@Override
	public UnknownRenderState createRenderState() {
		return new UnknownRenderState();
	}

	@Override
	public void extractRenderState(UnknownEntity entity, UnknownRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.phase = Math.max(1, Math.min(4, entity.getPhase()));
		state.hunting = entity.isHunting();
	}

	@Override
	public Identifier getTextureLocation(UnknownRenderState state) {
		return TEXTURES[Math.max(1, Math.min(4, state.phase))];
	}
}
