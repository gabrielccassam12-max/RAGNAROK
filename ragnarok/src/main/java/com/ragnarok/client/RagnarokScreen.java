package com.ragnarok.client;

import com.ragnarok.core.Ability;
import com.ragnarok.core.Elem;
import com.ragnarok.core.PlayerData;
import com.ragnarok.net.SelectPayload;
import com.ragnarok.net.SyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Menu (key M): choose element/combination, pick the ability, see mastery and unlock requirements. */
public class RagnarokScreen extends Screen {
    private int viewStyle = -1;

    public RagnarokScreen() {
        super(Text.translatable("screen.ragnarok.title"));
    }

    private PlayerData data() {
        SyncPayload s = ClientState.last;
        if (s == null) return PlayerData.DEFAULT;
        return new PlayerData(s.unlocked(), s.style(), s.ability(), s.xp(), s.essence());
    }

    @Override
    protected void init() {
        PlayerData d = data();
        if (viewStyle < 0) viewStyle = d.style();
        int left = width / 2 - 200, top = 42;
        // styles
        for (int i = 0; i < 8; i++) {
            final int st = i;
            boolean unlocked = d.styleUnlocked(i);
            Text label = Text.literal(i == viewStyle ? "> " : "").append(Ability.styleName(i)).append(unlocked ? "" : "  ✖");
            ButtonWidget b = ButtonWidget.builder(label, btn -> {
                viewStyle = st;
                clearAndInit();
            }).dimensions(left, top + i * 22, 150, 20).build();
            b.active = unlocked;
            addDrawableChild(b);
        }
        // abilities of the viewed style
        List<Ability> list = Ability.of(viewStyle);
        int level = d.styleLevel(viewStyle);
        for (int i = 0; i < list.size(); i++) {
            final int idx = i;
            Ability a = list.get(i);
            boolean ok = d.styleUnlocked(viewStyle) && level >= a.level;
            boolean sel = d.style() == viewStyle && d.ability() == i;
            Text label = Text.literal(sel ? "▶ " : "").append(a.displayName()).append(Text.literal("   " + a.cost + " ⚡"))
                    .append(ok ? Text.empty() : Text.literal("   ").append(Ability.levelName(a.level)));
            ButtonWidget b = ButtonWidget.builder(label, btn -> {
                ClientPlayNetworking.send(new SelectPayload(viewStyle, idx));
                close();
            }).dimensions(left + 170, top + i * 22, 230, 20).build();
            b.active = ok;
            addDrawableChild(b);
        }
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta); // draws the background and the buttons
        g.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFFFFFFF);
        PlayerData d = data();
        int left = width / 2 - 200;
        // mastery bars
        int y = 42 + 8 * 22 + 8;
        g.drawTextWithShadow(textRenderer, Text.translatable("screen.ragnarok.mastery"), left, y, 0xFFFFD35A);
        for (int e = 0; e < 4; e++) {
            int yy = y + 14 + e * 14;
            int lvl = d.level(e);
            int xp = d.xp(e);
            int next = Math.min(3, lvl + 1);
            float pct = lvl >= 3 ? 1f : (float) (xp - PlayerData.LEVEL_XP[lvl]) / (PlayerData.LEVEL_XP[next] - PlayerData.LEVEL_XP[lvl]);
            g.drawTextWithShadow(textRenderer, Text.translatable("element.ragnarok." + e), left, yy, 0xFF000000 | Elem.COLOR[e]);
            g.fill(left + 110, yy + 2, left + 290, yy + 8, 0xFF1A1F26);
            g.fill(left + 110, yy + 2, left + 110 + (int) (180 * Math.max(0, Math.min(1, pct))), yy + 8, 0xFF000000 | Elem.COLOR[e]);
            g.drawTextWithShadow(textRenderer, d.has(e) ? Ability.levelName(lvl) : Text.translatable("screen.ragnarok.locked"), left + 296, yy, 0xFFB8C4D0);
        }
        g.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.ragnarok.hint"), width / 2, height - 18, 0xFF8A94A0);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
