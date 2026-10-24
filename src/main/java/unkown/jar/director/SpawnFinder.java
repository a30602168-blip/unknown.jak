package unkown.jar.director;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import unkown.jar.entity.Phases;

import java.util.concurrent.ThreadLocalRandom;

/** Sucht sichere, zum Horror passende Positionen (dunkel, nicht im Blickfeld, freier Platz). */
public final class SpawnFinder {

	/** Position in der Ferne. Phase 1+2 nur im Dunkeln; meist ausserhalb des Blickfelds. */
	public static BlockPos findSpawn(ServerLevel level, ServerPlayer player, int phase) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		double min = Phases.minSpawnDist(phase);
		double max = Phases.maxSpawnDist(phase);
		boolean needDark = phase <= 2;
		Vec3 view = player.getViewVector(1.0F);
		double viewLen = Math.hypot(view.x, view.z);

		for (int i = 0; i < 40; i++) {
			double angle = r.nextDouble() * Math.PI * 2;
			double dx = Math.cos(angle);
			double dz = Math.sin(angle);
			boolean inFront = viewLen > 1.0E-3 && (dx * view.x + dz * view.z) / viewLen > 0.55;
			if (inFront && r.nextDouble() < 0.75) {
				continue; // meistens hinter oder neben dem Spieler auftauchen
			}
			double dist = min + r.nextDouble() * (max - min);
			BlockPos ground = groundAt(level, player.getX() + dx * dist, player.getZ() + dz * dist, player.getBlockY(), phase);
			if (ground == null) {
				continue;
			}
			if (needDark && level.getMaxLocalRawBrightness(ground) > 7) {
				continue;
			}
			return ground;
		}
		return null;
	}

	/** Position hinter dem Spieler (Phase-2-Teleport). */
	public static BlockPos behind(ServerLevel level, ServerPlayer player, double min, double max, int phase) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		Vec3 view = player.getViewVector(1.0F);
		double base = Math.atan2(-view.z, -view.x);
		for (int i = 0; i < 16; i++) {
			double angle = base + (r.nextDouble() - 0.5) * Math.toRadians(100);
			double dist = min + r.nextDouble() * (max - min);
			BlockPos ground = groundAt(level, player.getX() + Math.cos(angle) * dist, player.getZ() + Math.sin(angle) * dist, player.getBlockY(), phase);
			if (ground != null) {
				return ground;
			}
		}
		return null;
	}

	/** Findet Boden in der Naehe der Hoehe aroundY mit genug Platz fuer ein Entity dieser Phase. */
	public static BlockPos groundAt(ServerLevel level, double x, double z, int aroundY, int phase) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		BlockPos probe = new BlockPos(bx, aroundY, bz);
		if (!level.hasChunkAt(probe) || !level.isPositionEntityTicking(probe)) {
			return null;
		}
		double scale = Phases.scale(phase);
		double halfW = 0.3 * scale;
		double height = 1.95 * scale;
		for (int dy = 10; dy >= -10; dy--) {
			BlockPos feet = new BlockPos(bx, aroundY + dy, bz);
			BlockPos below = feet.below();
			BlockState floor = level.getBlockState(below);
			if (floor.getCollisionShape(level, below).isEmpty() || !floor.getFluidState().isEmpty()) {
				continue;
			}
			if (!level.getFluidState(feet).isEmpty()) {
				continue;
			}
			AABB box = new AABB(bx + 0.5 - halfW, feet.getY(), bz + 0.5 - halfW, bx + 0.5 + halfW, feet.getY() + height, bz + 0.5 + halfW);
			if (level.noCollision(box)) {
				return feet;
			}
		}
		return null;
	}

	private SpawnFinder() {
	}
}
