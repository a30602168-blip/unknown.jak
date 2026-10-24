package unkown.jar.entity;

import net.minecraft.server.level.ServerLevel;
import unkown.jar.UnkownConfig;

/** Alle phasenabhaengigen Werte an einem Ort - hier kann man das Balancing anpassen. */
public final class Phases {
	public static final String[] NAMES = {"", "unknown", "unnowk", "ukwonoa", "HsiEpLdP"};

	/** Ingame-Tag, Tag 1 = erster Tag der Welt. Falls sich die Zeit-API aendert: nur diese Methode anpassen. */
	public static int day(ServerLevel level) {
		return (int) (level.getDayTime() / 24000L) + 1;
	}

	/** Tag 1 -> Phase 1, Tag 2 -> 2, Tag 3 -> 3, Tag 4 und danach -> 4. */
	public static int current(ServerLevel level) {
		int forced = UnkownConfig.get().forcePhase;
		if (forced >= 1 && forced <= 4) {
			return forced;
		}
		return Math.max(1, Math.min(4, day(level)));
	}

	private static int idx(int phase) {
		return Math.max(1, Math.min(4, phase));
	}

	public static String name(int phase) { return NAMES[idx(phase)]; }
	public static double scale(int phase) { return new double[]{0, 1.0, 1.2, 1.55, 2.3}[idx(phase)]; }
	public static double speed(int phase) { return new double[]{0, 0.26, 0.31, 0.38, 0.44}[idx(phase)]; }
	public static double damage(int phase) { return new double[]{0, 0, 3, 5, 7}[idx(phase)]; }
	public static double health(int phase) { return new double[]{0, 40, 60, 100, 200}[idx(phase)]; }
	public static int heightBlocks(int phase) { return (int) Math.ceil(1.95 * scale(phase)); }

	public static double minSpawnDist(int phase) { return new double[]{0, 28, 22, 30, 34}[idx(phase)]; }
	public static double maxSpawnDist(int phase) { return new double[]{0, 42, 34, 42, 48}[idx(phase)]; }

	/** Maximale Lebensdauer einer Begegnung in Ticks (20 Ticks = 1 Sekunde). */
	public static int minLife(int phase) { return new int[]{0, 400, 600, 1400, 1800}[idx(phase)]; }
	public static int maxLife(int phase) { return new int[]{0, 800, 1000, 1500, 1900}[idx(phase)]; }

	/** Pause bis zur naechsten Begegnung in Ticks. */
	public static int minGap(int phase) { return new int[]{0, 4800, 3600, 3600, 3000}[idx(phase)]; }
	public static int maxGap(int phase) { return new int[]{0, 10800, 8400, 7200, 6000}[idx(phase)]; }

	/** Lines the entity sometimes "says" in chat. */
	public static String[] chatLines(int phase) {
		return switch (idx(phase)) {
			case 1 -> new String[]{"...", "behind you", "i see you", "do not look away", "\u00a7kxxxxxx"};
			case 2 -> new String[]{"you saw me", "why are you looking", "i am closer than you think", "let us play", "ben was curious too"};
			case 3 -> new String[]{"run", "i will find you", "ben was slower", "you cannot leave", "i am not alone"};
			default -> new String[]{"IT IS TOO LATE", "i have you", "come back", "\u00a7kHsiEpLdP\u00a7r i see everything", "your game belongs to me"};
		};
	}

	private Phases() {
	}
}
