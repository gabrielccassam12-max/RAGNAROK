package com.ragnarok.client;

import com.ragnarok.core.Ability;
import com.ragnarok.core.Elem;
import com.ragnarok.core.PlayerData;
import com.ragnarok.net.SyncPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/** Essence bar, selected ability, cooldown, mastery, combo multiplier and transformation timer. */
public final class RagnarokHud {
    private RagnarokHud() {}

    static int styleColor(int style) {
        if (style < 4) return Elem.COLOR[style];
        int[] c = Elem.COMBO[style - 4];
        int a = Elem.COLOR[c[0]], b = Elem.COLOR[c[1]];
        return (((a >> 16 & 255) + (b >> 16 & 255)) / 2 << 16) | (((a >> 8 & 255) + (b >> 8 & 255)) / 2 << 8) | ((a & 255) + (b & 255)) / 2;
    }

    private static int argb(int rgb, int a) {
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int shade(int rgb, double f) {
        int r = Math.min(255, (int) ((rgb >> 16 & 255) * f)), g = Math.min(255, (int) ((rgb >> 8 & 255) * f)), b = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    public static void render(DrawContext g, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SyncPayload s = ClientState.last;
        if (mc.player == null || mc.options.hudHidden || s == null || s.unlocked() == 0) return;
        long t = ClientState.time();

        ClientState.shownEssence += (s.essence() - ClientState.shownEssence) * 0.2f;
        float pct = Math.max(0f, Math.min(1f, ClientState.shownEssence / Math.max(1, s.max())));

        int style = s.style();
        int col = styleColor(style);
        int w = 124, x = g.getScaledWindowWidth() - w - 12, y = g.getScaledWindowHeight() - 52;
        boolean over = s.overloaded();
        if (over && (t / 4) % 2 == 0) col = 0xFF3030;

        // transformation timer above the bar
        if (s.formElem() >= 0) {
            long left = Math.max(0, ClientState.formEnd - t);
            float fp = Math.min(1f, (float) left / Math.max(1, s.formMax()));
            int fc = Elem.COLOR[s.formElem()];
            Text name = Text.translatable("form.ragnarok." + s.formElem());
            g.drawTextWithShadow(mc.textRenderer, name, x, y - 30, 0xFF000000 | fc);
            g.fill(x, y - 18, x + w, y - 14, argb(0x0B0F14, 0xB0));
            g.fill(x, y - 18, x + (int) (w * fp), y - 14, argb(fc, 0xFF));
        }
        // combo multiplier
        if (s.combo100() > 104) {
            Text c = Text.literal(String.format("x%.2f ", s.combo100() / 100.0)).append(Text.translatable("hud.ragnarok.combo"));
            g.drawTextWithShadow(mc.textRenderer, c, x + w - mc.textRenderer.getWidth(c), y - 12, 0xFFFFD35A);
        }

        // Essence bar
        g.fill(x - 3, y - 3, x + w + 3, y + 13, argb(0x0B0F14, 0xB4));
        g.drawBorder(x - 3, y - 3, w + 6, 16, argb(shade(col, 1.1), 0xFF));
        int fw = (int) (w * pct);
        if (fw > 0) {
            g.fillGradient(x, y, x + fw, y + 5, argb(shade(col, 1.4), 0xFF), argb(shade(col, 0.85), 0xFF));
            g.fillGradient(x, y + 5, x + fw, y + 10, argb(shade(col, 0.85), 0xFF), argb(shade(col, 0.5), 0xFF));
            int gx = x + (int) ((t * 2) % Math.max(1, fw));
            g.fill(gx, y, Math.min(gx + 3, x + fw), y + 10, argb(0xFFFFFF, 0x45));
        }
        for (int i = 1; i < 4; i++) g.fill(x + w * i / 4, y, x + w * i / 4 + 1, y + 10, argb(0x000000, 0x90));
        g.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.ragnarok.essence", s.essence(), s.max()), x, y - 12 - 0, 0xFFE8EEF4);
        if (over) g.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.ragnarok.overload"), x + w - 56, y - 12, 0xFFFF5555);

        // selected ability
        Ability a = Ability.get(style, s.ability());
        PlayerData d = new PlayerData(s.unlocked(), style, s.ability(), s.xp(), s.essence());
        int lvl = d.styleLevel(a.style);
        boolean locked = lvl < a.level;
        boolean afford = s.essence() >= a.cost;
        int ty = y + 17;
        int tc2 = locked ? 0xFF888899 : afford ? 0xFFFFFFFF : 0xFFFF7777;
        g.drawTextWithShadow(mc.textRenderer, Text.literal("< ").append(a.displayName()).append(" >"), x, ty, tc2);
        g.drawTextWithShadow(mc.textRenderer, Text.literal(locked ? "✖" : String.valueOf(a.cost)), x + w - 20, ty, locked ? 0xFFFF6666 : afford ? 0xFFA8E4FF : 0xFFFF7777);

        long left = Math.max(0, ClientState.cdEnd - t);
        if (left > 0 && s.cdMax() > 0) {
            int cw = (int) (w * (1f - (float) left / s.cdMax()));
            g.fill(x, ty + 11, x + w, ty + 13, argb(0x0B0F14, 0xB4));
            g.fill(x, ty + 11, x + cw, ty + 13, argb(0xFFD966, 0xFF));
        }
        // mastery of the current style
        int ref = style < 4 ? style : Elem.COMBO[style - 4][0];
        int xp = d.xp(ref);
        int next = Math.min(3, d.level(ref) + 1);
        float xpPct = d.level(ref) >= 3 ? 1f : (float) (xp - PlayerData.LEVEL_XP[d.level(ref)]) / (PlayerData.LEVEL_XP[next] - PlayerData.LEVEL_XP[d.level(ref)]);
        g.drawTextWithShadow(mc.textRenderer, Ability.styleName(style), x, ty + 16, 0xFF000000 | col);
        Text ln = Ability.levelName(lvl);
        g.drawTextWithShadow(mc.textRenderer, ln, x + w - mc.textRenderer.getWidth(ln), ty + 16, 0xFFB8C4D0);
        g.fill(x, ty + 27, x + w, ty + 29, argb(0x0B0F14, 0xB4));
        g.fill(x, ty + 27, x + (int) (w * Math.max(0, Math.min(1, xpPct))), ty + 29, argb(col, 0xFF));
    }
}
