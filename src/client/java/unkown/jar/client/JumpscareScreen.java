package unkown.jar.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import unkown.jar.entity.Phases;

import java.util.Random;

/**
 * Vollbild-Jumpscare, komplett aus gefuellten Rechtecken gezeichnet (keine Textur noetig):
 * Blitz, hineinzoomendes Gesicht mit leeren Augen und aufklappendem Maul, Zittern, Rauschen.
 */
public class JumpscareScreen extends Screen {
	public static final int DURATION_TICKS = 70;

	private final int phase;
	private int ticks;

	public JumpscareScreen(int phase) {
		super(Component.literal("unknown"));
		this.phase = Math.max(2, Math.min(4, phase));
	}

	@Override
	public void tick() {
		this.ticks++;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		int w = this.width;
		int h = this.height;
		float time = this.ticks + partialTick;
		float progress = Math.min(1.0F, time / DURATION_TICKS);
		Random r = new Random(this.ticks * 7919L + (long) (partialTick * 3));

		// Hintergrund + Blitze in den ersten Ticks
		int bg = 0xFF000000;
		if (this.ticks < 8 && (this.ticks % 2 == 0)) {
			bg = 0xFFFFFFFF;
		} else if (this.ticks < 14 && r.nextInt(4) == 0) {
			bg = 0xFF550000;
		}
		g.fill(0, 0, w, h, bg);

		float u = Math.min(w, h) / 30.0F * (0.9F + progress * 1.1F);
		int cx = w / 2 + (int) ((r.nextFloat() - 0.5F) * u * 1.4F);
		int cy = h / 2 + (int) ((r.nextFloat() - 0.5F) * u * 1.4F);

		int skin = switch (this.phase) {
			case 2 -> 0xFF86868C;
			case 3 -> 0xFF707A72;
			default -> 0xFFE4E2D6;
		};
		int dark = 0xFF000000;
		int tooth = 0xFFEBE8DC;

		// Kopf
		rect(g, cx, cy, -9, -12, 9, 10, u, skin);
		rect(g, cx, cy, -7, 10, 7, 14, u, skin);
		rect(g, cx, cy, -10, -14, 10, -11, u, 0xFF101010);          // Haaransatz/Kapuze

		// Augenhoehlen
		float wobble = (float) Math.sin(time * 0.6F);
		rect(g, cx, cy, -7.5F, -7, -2, 0, u, dark);
		rect(g, cx, cy, 2, -7, 7.5F, 0, u, dark);
		int pupil = this.phase == 3 ? 0xFFFFFFFF : 0xFFCC0000;
		float pw = 0.8F + progress * 0.9F;
		rect(g, cx, cy, -5 + wobble, -4, -5 + wobble + pw, -4 + pw, u, pupil);
		rect(g, cx, cy, 4 + wobble, -4, 4 + wobble + pw, -4 + pw, u, pupil);

		// Nase
		rect(g, cx, cy, -1.2F, 1, -0.3F, 4, u, 0xFF222222);
		rect(g, cx, cy, 0.3F, 1, 1.2F, 4, u, 0xFF222222);

		// Maul klappt auf
		float open = 2.0F + 7.0F * (float) Math.min(1.0, time / 18.0F) + (float) Math.abs(Math.sin(time * 1.3F)) * 1.5F;
		float top = 5.0F;
		rect(g, cx, cy, -8, top, 8, top + open, u, dark);
		for (int i = -8; i < 8; i += 2) {
			rect(g, cx, cy, i, top, i + 1.1F, top + 2.0F, u, tooth);
			rect(g, cx, cy, i + 1, top + open - 2.0F, i + 2.1F, top + open, u, tooth);
		}

		// Blutige Traenen
		int blood = 0xFF8A0000;
		rect(g, cx, cy, -5.5F, 0, -4.8F, 3 + progress * 10, u, blood);
		rect(g, cx, cy, 4.8F, 0, 5.5F, 4 + progress * 8, u, blood);

		// Rauschen
		for (int i = 0; i < 140; i++) {
			int x = r.nextInt(w);
			int y = r.nextInt(h);
			int c = r.nextBoolean() ? 0x88FFFFFF : 0x88000000;
			g.fill(x, y, x + 1 + r.nextInt(4), y + 1 + r.nextInt(2), c);
		}
		// Zeilen-Glitch
		if (r.nextInt(3) == 0) {
			int y = r.nextInt(h);
			g.fill(0, y, w, y + 2 + r.nextInt(6), 0x66FF0000);
		}
		// Rote Vignette
		int edge = (int) (Math.min(w, h) * (0.10F + 0.12F * progress));
		g.fill(0, 0, w, edge, 0x66AA0000);
		g.fill(0, h - edge, w, h, 0x66AA0000);
		g.fill(0, 0, edge, h, 0x66AA0000);
		g.fill(w - edge, 0, w, h, 0x66AA0000);

		// Name am Ende
		if (this.ticks > 40) {
			g.drawCenteredString(this.font, Component.literal(Phases.name(this.phase)), w / 2, h - 28, 0xFFAA0000);
		}
	}

	private static void rect(GuiGraphics g, int cx, int cy, float x0, float y0, float x1, float y1, float u, int color) {
		g.fill(cx + Math.round(x0 * u), cy + Math.round(y0 * u), cx + Math.round(x1 * u), cy + Math.round(y1 * u), color);
	}
}
