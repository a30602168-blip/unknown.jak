package unkown.jar.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

public final class Fx {
	public static void play(ServerLevel level, SoundEvent sound, double x, double y, double z, float volume, float pitch) {
		level.playSound((Player) null, x, y, z, sound, SoundSource.HOSTILE, volume, pitch);
	}

	public static void particles(ServerLevel level, ParticleOptions type, double x, double y, double z, int count, double spread) {
		level.sendParticles(type, x, y, z, count, spread, spread, spread, 0.02);
	}

	private Fx() {
	}
}
