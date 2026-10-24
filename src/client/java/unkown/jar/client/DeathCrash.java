package unkown.jar.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import unkown.jar.UnkownConfig;
import unkown.jar.UnkownJar;
import unkown.jar.entity.ModSounds;
import unkown.jar.entity.Phases;

/**
 * Wenn ein unknown-Entity den lokalen Spieler toetet, stuerzt das Spiel kurz nach dem Todesbildschirm ab.
 * Erkennung ueber die Todesnachricht ("death.attack.*" mit dem Namen des Entities als Toeter).
 * Es werden keine Dateien veraendert; im Einzelspieler wird die Welt vorher gespeichert.
 * Abschaltbar ueber "crashOnDeath": false in config/unkownjar.json.
 */
public final class DeathCrash {
	private static int countdown = -1;

	public static void init() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay || countdown >= 0) {
				return;
			}
			UnkownConfig cfg = UnkownConfig.get();
			int phase = killerPhase(message);
			if (phase == 0 || !(cfg.crashOnDeath || cfg.jumpscare)) {
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			if (cfg.jumpscare) {
				mc.execute(() -> {
					mc.setScreen(new JumpscareScreen(phase));
					// Mehrfach uebereinander = noch lauter; laeuft ueber MASTER, ignoriert also den Monster-Regler.
					mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.JUMPSCARE, 1.0F, 1.0F));
					mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.JUMPSCARE, 0.92F, 1.0F));
					mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.JUMPSCARE, 0.7F, 1.0F));
				});
			}
			if (cfg.crashOnDeath) {
				countdown = cfg.jumpscare ? JumpscareScreen.DURATION_TICKS + 15 : 60;
				saveWorldIfSingleplayer();
			} else {
				countdown = -1;
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (countdown < 0) {
				return;
			}
			if (client.player == null || client.level == null) {
				countdown = -1; // Welt schon verlassen
				return;
			}
			if (--countdown == 0) {
				countdown = -1;
				CrashReport report = CrashReport.forThrowable(
						new IllegalStateException("unknown.jar: It found you. This was not a bug."),
						"unknown.jar took your game");
				throw new ReportedException(report);
			}
		});
	}

	/** @return Phase (1-4) des Toeters, oder 0 wenn der lokale Spieler nicht von einem unknown-Entity getoetet wurde. */
	private static int killerPhase(Component message) {
		if (!(message.getContents() instanceof TranslatableContents tc) || !tc.getKey().startsWith("death.attack.")) {
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return 0;
		}
		String self = mc.player.getName().getString();
		boolean selfIsVictim = false;
		int killerPhase = 0;
		for (Object arg : tc.getArgs()) {
			String text = arg instanceof Component c ? c.getString() : String.valueOf(arg);
			if (text.equals(self)) {
				selfIsVictim = true;
			}
			for (int i = 1; i <= 4; i++) {
				if (text.equals(Phases.name(i))) {
					killerPhase = i;
				}
			}
		}
		return selfIsVictim ? killerPhase : 0;
	}

	private static void saveWorldIfSingleplayer() {
		try {
			MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
			if (server != null) {
				server.execute(() -> server.saveEverything(true, false, true));
			}
		} catch (Exception e) {
			UnkownJar.LOGGER.warn("unknown.jar: saving before the crash failed (ignored)", e);
		}
	}

	private DeathCrash() {
	}
}
