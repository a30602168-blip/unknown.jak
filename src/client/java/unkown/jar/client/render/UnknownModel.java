package unkown.jar.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.builders.PartPose;
import net.minecraft.util.Mth;
import unkown.jar.UnkownJar;

/**
 * Humanoides Modell mit eigenen, phasenabhaengigen Posen.
 * P1 regungslos, P2 schiefer Kopf, P3 gebrochener Hals, P4: keine menschlichen Beine,
 * stattdessen acht lange, duenne, kantige Spinnenbeine (je 5 Segmente mit eckigen Knien).
 */
public class UnknownModel extends HumanoidModel<UnknownRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(UnkownJar.id("unknown"), "main");

	private static final int LEGS = 8;
	private static final int SEGMENTS = 5;
	// Profil eines Beins: (x nach aussen, y nach unten) relativ zum Ansatz, in Modell-Pixeln. Das letzte Segment endet am Boden.
	private static final float[][] PROFILE = {{0, 0}, {7, -10}, {18, -17}, {27, -4}, {32, 8}};
	private static final float[] FAN_DEG = {-62, -21, 21, 62};
	private static final float[] REACH = {1.05F, 0.9F, 0.95F, 1.1F};

	private final ModelPart[] legPivots = new ModelPart[LEGS];
	private final ModelPart[][] legSegs = new ModelPart[LEGS][SEGMENTS];

	public UnknownModel(ModelPart root) {
		super(root);
		for (int i = 0; i < LEGS; i++) {
			ModelPart pivot = root.getChild("leg" + i);
			this.legPivots[i] = pivot;
			ModelPart parent = pivot;
			for (int j = 0; j < SEGMENTS; j++) {
				ModelPart seg = parent.getChild("s" + j);
				this.legSegs[i][j] = seg;
				parent = seg;
			}
		}
	}

	/** Menschlicher Koerper (Phase 1-3) plus acht Spinnenbeine (nur sichtbar in Phase 4). */
	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
		PartDefinition root = mesh.getRoot();

		for (int i = 0; i < LEGS; i++) {
			boolean left = i >= 4;
			int k = i % 4;
			float anchorY = 3.0F + k * 2.5F;
			float yaw = (float) Math.toRadians(FAN_DEG[k]) + (left ? (float) Math.PI : 0.0F);
			PartDefinition pivot = root.addOrReplaceChild("leg" + i, CubeListBuilder.create(),
					PartPose.offsetAndRotation(left ? -3.0F : 3.0F, anchorY, 0.0F, 0.0F, yaw, 0.0F));

			// Punkte des Beins in der Beinebene
			float[][] pts = new float[SEGMENTS + 1][2];
			for (int j = 0; j < PROFILE.length; j++) {
				pts[j][0] = PROFILE[j][0] * REACH[k];
				pts[j][1] = PROFILE[j][1];
			}
			pts[SEGMENTS][0] = 36.0F * REACH[k];
			pts[SEGMENTS][1] = 24.0F - anchorY; // Spitze steht auf dem Boden

			PartDefinition parent = pivot;
			float prevTheta = 0.0F;
			float prevLen = 0.0F;
			for (int j = 0; j < SEGMENTS; j++) {
				float dx = pts[j + 1][0] - pts[j][0];
				float dy = pts[j + 1][1] - pts[j][1];
				float len = (float) Math.hypot(dx, dy);
				float theta = (float) Math.atan2(-dx, dy);
				CubeListBuilder cube = CubeListBuilder.create().texOffs(0, 16)
						.addBox(-0.5F, 0.0F, -0.5F, 1.0F, len, 1.0F)
						.addBox(-1.0F, len - 1.0F, -1.0F, 2.0F, 2.0F, 2.0F); // eckiges Knie
				parent = parent.addOrReplaceChild("s" + j, cube,
						PartPose.offsetAndRotation(0.0F, j == 0 ? 0.0F : prevLen, 0.0F, 0.0F, 0.0F, theta - prevTheta));
				prevTheta = theta;
				prevLen = len;
			}
		}
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(UnknownRenderState state) {
		super.setupAnim(state);
		float t = state.ageInTicks;

		boolean spider = state.phase >= 4;
		this.rightLeg.visible = !spider;
		this.leftLeg.visible = !spider;
		for (ModelPart pivot : this.legPivots) {
			pivot.visible = spider;
		}

		switch (state.phase) {
			case 1 -> {
				this.head.zRot = 0.10F * Mth.sin(t * 0.05F);
				this.rightArm.xRot *= 0.2F;
				this.leftArm.xRot *= 0.2F;
				this.rightArm.zRot += 0.04F * Mth.sin(t * 0.09F) + 0.04F;
				this.leftArm.zRot -= 0.04F * Mth.sin(t * 0.09F + 1.0F) + 0.04F;
			}
			case 2 -> {
				this.head.zRot = 0.45F + 0.05F * Mth.sin(t * 0.8F);
				this.head.xRot += 0.25F;
				this.rightArm.xRot = -0.25F + 0.2F * Mth.sin(t * 0.45F);
				this.leftArm.xRot = -0.15F + 0.2F * Mth.sin(t * 0.45F + 2.0F);
				this.rightArm.zRot = 0.12F;
				this.leftArm.zRot = -0.12F;
				this.body.xRot = 0.15F;
			}
			case 3 -> {
				// Gebrochener Hals, ein Arm haengt, der andere zeigt auf dich; bei der Jagd weit ausgebreitete Arme.
				this.head.zRot = 0.95F + 0.04F * Mth.sin(t * 0.9F);
				this.head.xRot += 0.2F;
				if (state.hunting) {
					this.rightArm.xRot = -1.2F + 0.1F * Mth.sin(t * 1.6F);
					this.leftArm.xRot = -1.2F + 0.1F * Mth.sin(t * 1.6F + 1.7F);
					this.rightArm.zRot = 0.9F;
					this.leftArm.zRot = -0.9F;
					this.body.xRot = 0.6F;
				} else {
					this.rightArm.xRot = 0.0F;
					this.rightArm.zRot = 0.05F;
					this.leftArm.xRot = -1.45F;
					this.leftArm.zRot = -0.1F + 0.03F * Mth.sin(t * 0.5F);
					this.body.xRot = 0.15F;
				}
			}
			default -> {
				float raise = state.hunting ? -2.7F : -1.2F;
				float shake = state.hunting ? 0.2F : 0.08F;
				this.rightArm.xRot = raise + shake * Mth.sin(t * 2.3F);
				this.leftArm.xRot = raise + shake * Mth.sin(t * 2.3F + 2.1F);
				this.rightArm.zRot = 0.45F + 0.1F * Mth.sin(t * 1.9F);
				this.leftArm.zRot = -0.45F - 0.1F * Mth.sin(t * 1.9F + 1.0F);
				this.body.xRot = state.hunting ? 0.2F : 0.05F;
				// Ruckartiges Verdrehen des Kopfes, alle paar Sekunden.
				int cycle = (int) t % 60;
				if (cycle < 4) {
					this.head.zRot = 1.1F;
					this.head.yRot += 0.9F;
				} else {
					this.head.zRot = 0.2F * Mth.sin(t * 0.6F);
				}
				animateSpiderLegs(state, t);
			}
		}
	}

	/** Die Beine zucken und trippeln; bei der Jagd deutlich staerker. */
	private void animateSpiderLegs(UnknownRenderState state, float t) {
		float amp = (state.hunting ? 0.20F : 0.07F) + state.walkAnimationSpeed * 0.35F;
		float rate = state.hunting ? 1.5F : 0.6F;
		float walk = state.walkAnimationPos * 1.6F;
		for (int i = 0; i < LEGS; i++) {
			float phase = i * 1.3F;
			float wave = Mth.sin(t * rate + phase) + Mth.sin(walk + phase * 2.0F);
			this.legPivots[i].yRot += amp * 0.6F * wave;
			this.legSegs[i][0].zRot += amp * 0.5F * wave;
			this.legSegs[i][1].zRot -= amp * 0.9F * Mth.sin(t * rate * 1.7F + phase);
			this.legSegs[i][3].zRot += amp * 0.7F * Mth.sin(t * rate * 1.3F + phase + 1.0F);
		}
	}
}
