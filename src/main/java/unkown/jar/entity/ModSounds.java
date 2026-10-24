package unkown.jar.entity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import unkown.jar.UnkownJar;

public final class ModSounds {
	public static final SoundEvent AMBIENT = register("ambient");
	public static final SoundEvent WHISPER = register("whisper");
	public static final SoundEvent APPEAR = register("appear");
	public static final SoundEvent VANISH = register("vanish");
	public static final SoundEvent TELEPORT = register("teleport");
	public static final SoundEvent CHASE = register("chase");
	public static final SoundEvent GROWL = register("growl");
	public static final SoundEvent STEP = register("step");
	public static final SoundEvent HURT = register("hurt");
	public static final SoundEvent DEATH = register("death");
	public static final SoundEvent SCREAM = register("scream");
	public static final SoundEvent DOOM = register("doom");
	public static final SoundEvent DISCONNECT = register("disconnect");
	public static final SoundEvent JUMPSCARE = register("jumpscare");

	private static SoundEvent register(String name) {
		Identifier id = UnkownJar.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
		// erzwingt das Laden der Klasse
	}

	private ModSounds() {
	}
}
