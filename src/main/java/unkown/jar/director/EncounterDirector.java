package unkown.jar.director;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import unkown.jar.UnkownConfig;
import unkown.jar.UnkownJar;
import unkown.jar.entity.Fx;
import unkown.jar.entity.ModSounds;
import unkown.jar.entity.Phases;
import unkown.jar.entity.UnknownEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Steuert, WANN das Entity erscheint: hoechstens ein Entity pro Spieler, lange zufaellige Pausen,
 * nur in der Oberwelt, nur fuer lebende Spieler. Verwaltet ausserdem die Trennungs-Sequenz von Phase 4.
 */
public final class EncounterDirector {
	private static final Map<UUID, PlayerState> STATES = new HashMap<>();
	private static final int DISCONNECT_TICKS = 80;

	// "unknown joined the game": pro Spieler und Welt nur einmal (gespeichert in <Welt>/unkownjar_events.txt)
	private static MinecraftServer eventServer;
	private static final Set<UUID> JOIN_EVENT_SEEN = new HashSet<>();

	private static final class PlayerState {
		UUID entityId;
		int activePhase = 1;
		int lastPhase = 0;
		long nextEncounter = -1;
		long nextPhantom = -1;
		long firstSeen = -1;
		int disconnectTicks = -1;
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(EncounterDirector::tick);
	}

	private static void tick(MinecraftServer server) {
		UnkownConfig cfg = UnkownConfig.get();
		if (!cfg.enabled) {
			return;
		}
		long now = server.getTickCount();

		if (now % 400 == 0) {
			cleanup(server);
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			PlayerState st = STATES.computeIfAbsent(player.getUUID(), id -> new PlayerState());
			try {
				if (st.disconnectTicks >= 0) {
					tickDisconnect(player, st);
					continue;
				}
				if ((now + player.getId()) % 20 != 0) {
					continue; // nur einmal pro Sekunde pro Spieler
				}
				secondTick(server, player, st, now);
			} catch (Exception e) {
				UnkownJar.LOGGER.warn("unknown.jar: director error (ignored)", e);
			}
		}
	}

	private static void secondTick(MinecraftServer server, ServerPlayer player, PlayerState st, long now) {
		UnkownConfig cfg = UnkownConfig.get();
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (!player.isAlive() || player.isSpectator() || level.dimension() != Level.OVERWORLD) {
			return;
		}
		ThreadLocalRandom r = ThreadLocalRandom.current();
		int phase = Phases.current(level);

		if (st.firstSeen < 0) {
			st.firstSeen = now;
		}
		if (st.nextEncounter < 0) {
			st.nextEncounter = now + cfg.firstEncounterDelaySeconds * 20L;
			st.nextPhantom = now + 1200 + r.nextInt(2400);
			st.lastPhase = phase;
		}
		// Neue Phase = sie meldet sich bald zu Wort.
		if (phase != st.lastPhase) {
			st.lastPhase = phase;
			st.nextEncounter = Math.min(st.nextEncounter, now + 600 + r.nextInt(1200));
		}

		// Laeuft gerade eine Begegnung?
		Entity existing = st.entityId == null ? null : level.getEntity(st.entityId);
		if (st.entityId != null && (existing == null || existing.isRemoved())) {
			st.entityId = null;
			st.nextEncounter = now + gap(st.activePhase);
			return;
		}
		if (existing != null) {
			return;
		}

		if (now >= st.nextEncounter) {
			if (spawn(level, player, st, phase)) {
				return;
			}
			st.nextEncounter = now + 100; // keinen guten Platz gefunden, bald nochmal versuchen
			return;
		}

		// Einmaliges Ereignis: "unknown joined the game"
		if (cfg.joinEvent && now - st.firstSeen > 3600 && r.nextInt(900) == 0) {
			tryJoinEvent(server, player);
		}

		// Gelegentliche Geraeusche ohne sichtbares Entity.
		if (cfg.phantomSounds && now >= st.nextPhantom) {
			phantomSound(level, player, phase);
			st.nextPhantom = now + (long) ((2400 + r.nextInt(4800)) * Math.max(0.1, cfg.encounterIntervalMultiplier));
		}
	}

	private static int gap(int phase) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		int min = Phases.minGap(phase);
		int max = Phases.maxGap(phase);
		double mult = Math.max(0.02, UnkownConfig.get().encounterIntervalMultiplier);
		return (int) ((min + r.nextInt(max - min + 1)) * mult);
	}

	private static boolean spawn(ServerLevel level, ServerPlayer player, PlayerState st, int phase) {
		BlockPos pos = SpawnFinder.findSpawn(level, player, phase);
		if (pos == null) {
			return false;
		}
		UnknownEntity entity = new UnknownEntity(unkown.jar.entity.ModEntities.UNKNOWN, level);
		entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		float yaw = (float) (Math.toDegrees(Math.atan2(player.getZ() - entity.getZ(), player.getX() - entity.getX())) - 90.0);
		entity.setYRot(yaw);
		entity.setYHeadRot(yaw);
		entity.bindToPlayer(player.getUUID());
		entity.initForPhase(phase);
		if (!level.addFreshEntity(entity)) {
			return false;
		}
		st.entityId = entity.getUUID();
		st.activePhase = phase;
		return true;
	}

	private static void tryJoinEvent(MinecraftServer server, ServerPlayer player) {
		Path file = server.getWorldPath(LevelResource.ROOT).resolve("unkownjar_events.txt");
		try {
			if (eventServer != server) {
				eventServer = server;
				JOIN_EVENT_SEEN.clear();
				if (Files.exists(file)) {
					for (String line : Files.readAllLines(file)) {
						try {
							JOIN_EVENT_SEEN.add(UUID.fromString(line.trim()));
						} catch (IllegalArgumentException ignored) {
							// ungueltige Zeile ueberspringen
						}
					}
				}
			}
			if (!JOIN_EVENT_SEEN.add(player.getUUID())) {
				return; // schon einmal passiert
			}
			Files.write(file, List.of(player.getUUID().toString()),
					java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
		} catch (Exception e) {
			UnkownJar.LOGGER.warn("unknown.jar: events file unusable, join event skipped", e);
			return;
		}
		player.sendSystemMessage(Component.translatable("multiplayer.player.joined", "unknown").withStyle(ChatFormatting.YELLOW));
	}

	private static void phantomSound(ServerLevel level, ServerPlayer player, int phase) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		double angle = r.nextDouble() * Math.PI * 2;
		double dist = 8 + r.nextDouble() * 10;
		double x = player.getX() + Math.cos(angle) * dist;
		double z = player.getZ() + Math.sin(angle) * dist;
		Fx.play(level, phase >= 4 && r.nextInt(3) == 0 ? ModSounds.DOOM : ModSounds.WHISPER,
				x, player.getY(), z, 0.8F + phase * 0.2F, 0.8F + r.nextFloat() * 0.3F);
	}

	private static void cleanup(MinecraftServer server) {
		Iterator<Map.Entry<UUID, PlayerState>> it = STATES.entrySet().iterator();
		while (it.hasNext()) {
			if (server.getPlayerList().getPlayer(it.next().getKey()) == null) {
				it.remove();
			}
		}
	}

	// ------------------------------------------------------------------ Trennungs-Sequenz (Phase 4)

	/**
	 * Startet die Trennung: Dunkelheit, Schrei, Titel, Fake-Chat - und nach ca. 4 Sekunden wird der Spieler
	 * ganz normal vom Server getrennt (Einzelspieler: zurueck zum Hauptmenue, die Welt wird sauber gespeichert).
	 */
	public static void beginDisconnect(ServerPlayer player) {
		PlayerState st = STATES.computeIfAbsent(player.getUUID(), id -> new PlayerState());
		if (st.disconnectTicks >= 0) {
			return;
		}
		st.disconnectTicks = DISCONNECT_TICKS;
		if (player.level() instanceof ServerLevel level) {
			Fx.play(level, ModSounds.DISCONNECT, player.getX(), player.getY(), player.getZ(), 3.0F, 1.0F);
			Fx.play(level, ModSounds.SCREAM, player.getX(), player.getY(), player.getZ(), 3.0F, 0.55F);
		}
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, DISCONNECT_TICKS + 40, 0, false, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DISCONNECT_TICKS + 60, 0, false, false, false));
		player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 10));
		player.connection.send(new ClientboundSetSubtitleTextPacket(msg("message.unkownjar.subtitle", "It found you.")));
		player.connection.send(new ClientboundSetTitleTextPacket(msg("message.unkownjar.title", "HsiEpLdP")));
	}

	private static void tickDisconnect(ServerPlayer player, PlayerState st) {
		if (!player.isAlive()) {
			st.disconnectTicks = -1; // gestorben: Sequenz abbrechen
			return;
		}
		st.disconnectTicks--;
		if (st.disconnectTicks == DISCONNECT_TICKS - 20) {
			player.sendSystemMessage(msg("message.unkownjar.chat1", "<HsiEpLdP> i see you"));
		} else if (st.disconnectTicks == DISCONNECT_TICKS - 45) {
			player.sendSystemMessage(msg("message.unkownjar.chat2", "<HsiEpLdP> you should not have looked"));
		} else if (st.disconnectTicks == 12) {
			player.sendSystemMessage(msg("message.unkownjar.chat3", "<HsiEpLdP> ........"));
		}
		if (st.disconnectTicks == 0) {
			st.disconnectTicks = -1;
			st.nextEncounter = player.level().getServer() == null ? -1 : player.level().getServer().getTickCount() + 6000L;
			try {
				player.connection.disconnect(msg("message.unkownjar.disconnect", "Connection lost\n\nunknown.jar found you."));
			} catch (Exception e) {
				UnkownJar.LOGGER.warn("unknown.jar: disconnect failed (ignored)", e);
			}
		}
	}

	private static Component msg(String key, String fallback) {
		return Component.translatableWithFallback(key, fallback);
	}

	private EncounterDirector() {
	}
}
