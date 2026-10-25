package com.nitro.gui;

import com.nitro.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Nitro click GUI. Immediate-mode, mouse/keys polled through GLFW so it does not depend on the
 * Screen input-event signatures that changed in 1.21.9+. Opens on Right Shift.
 */
public final class ClickGui extends Screen {
    public static long lastClosedMs;
    private static final int ROW = 22, SROW = 18, SIDE = 104;

    private DrawContext g;
    private int mx, my, px, py, pw, ph, cx0, cy0, cx1, cy1, cur, scroll, maxScroll;
    private boolean down, pressed, rpressed, prevDown, prevR, prevMenu = true, dirty;
    private Category cat = Category.COMBAT;
    private Mod binding;
    private String active;

    public ClickGui() { super(Text.literal("Nitro Client")); }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return binding == null; }

    // ------------------------------------------------------------------ frame
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        g = ctx; mx = mouseX; my = mouseY;
        long win = this.client.getWindow().getHandle();
        down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        pressed = down && !prevDown; rpressed = r && !prevR; prevDown = down; prevR = r;

        boolean menu = GLFW.glfwGetKey(win, ModuleManager.menuKey) == GLFW.GLFW_PRESS;
        if (menu && !prevMenu && binding == null) { close(); return; }
        prevMenu = menu;

        if (binding != null) captureKey(win);

        pw = Math.min(width - 16, 560); ph = Math.min(height - 16, 340);
        px = (width - pw) / 2; py = (height - ph) / 2;
        g.fill(0, 0, width, height, 0x88070410);
        Gfx.glass(g, px, py, pw, ph);

        // ---- sidebar
        g.fill(px + SIDE, py + 10, px + SIDE + 1, py + ph - 10, 0x30FFFFFF);
        Gfx.text(g, "NITRO", px + 16, py + 14, Theme.accent());
        Gfx.text(g, "CLIENT", px + 16, py + 25, Theme.DIM);
        int y = py + 46;
        for (Category c : Category.values()) {
            boolean h = inside(px + 6, y, px + SIDE - 6, y + 22), sel = c == cat;
            if (sel) Gfx.rrect(g, px + 6, y, SIDE - 12, 22, Theme.accentA(0x40));
            else if (h) Gfx.rrect(g, px + 6, y, SIDE - 12, 22, 0x18FFFFFF);
            if (sel) g.fill(px + 6, y + 4, px + 8, y + 18, Theme.accent());
            int n = 0; for (Mod m : ModuleManager.ALL) if (m.cat == c && m.enabled) n++;
            Gfx.text(g, Lang.t(c.label), px + 16, y + 7, sel ? Theme.TEXT : Theme.DIM);
            if (n > 0) { String s = "" + n; Gfx.text(g, s, px + SIDE - 14 - Gfx.tw(s), y + 7, Theme.accentA(200)); }
            if (h && pressed && !sel) { cat = c; scroll = 0; active = null; expandReset(); }
            y += 24;
        }

        // ---- header
        cx0 = px + SIDE + 12; cx1 = px + pw - 12; cy0 = py + 36; cy1 = py + ph - 22;
        Gfx.text(g, Lang.t(cat.label).toUpperCase(), cx0, py + 14, Theme.accent());
        String hb = Lang.t("HUD Editor");
        int bw = Gfx.tw(hb) + 14, bx = cx1 - bw;
        boolean hh = inside(bx, py + 8, bx + bw, py + 26);
        Gfx.rrect(g, bx, py + 8, bw, 18, hh ? Theme.accentA(0x90) : Theme.accentA(0x50));
        Gfx.text(g, hb, bx + 7, py + 13, Theme.TEXT);
        if (hh && pressed) { ModuleManager.save(); this.client.setScreen(new HudEditor()); return; }

        // ---- module list
        g.enableScissor(cx0 - 2, cy0, cx1 + 2, cy1);
        cur = cy0 - scroll;
        for (Mod m : ModuleManager.ALL) if (m.cat == cat) module(m);
        if (cat == Category.CLIENT) {
            int yy = cur; cur += ROW + 2;
            boolean h = hov(cx0, yy, cx1, yy + ROW);
            Gfx.rrect(g, cx0, yy, cx1 - cx0, ROW, h ? 0x70802030 : 0x50602030);
            String t = Lang.t("Reset all settings");
            Gfx.text(g, t, cx0 + (cx1 - cx0 - Gfx.tw(t)) / 2, yy + 7, Theme.TEXT);
            if (h && pressed) ModuleManager.resetAll();
        }
        int contentH = cur - (cy0 - scroll);
        g.disableScissor();

        int viewH = cy1 - cy0;
        maxScroll = Math.max(0, contentH - viewH);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        if (maxScroll > 0) {
            int barH = Math.max(16, viewH * viewH / contentH);
            int barY = cy0 + (int) ((viewH - barH) * (scroll / (double) maxScroll));
            g.fill(px + pw - 7, cy0, px + pw - 4, cy1, 0x30FFFFFF);
            g.fill(px + pw - 7, barY, px + pw - 4, barY + barH, Theme.accent());
        }

        Gfx.text(g, Lang.t("Right-click a module for settings") + "  |  Esc", cx0, py + ph - 15, Theme.DIM);

        if (dirty && !down) { ModuleManager.save(); dirty = false; }
    }

    private void expandReset() { for (Mod m : ModuleManager.ALL) m.expanded = false; }

    private void captureKey(long win) {
        for (int k = 32; k <= 348; k++) {
            if (GLFW.glfwGetKey(win, k) == GLFW.GLFW_PRESS) {
                if (k == GLFW.GLFW_KEY_ESCAPE) binding = null;
                else { binding.key = (k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_DELETE) ? 0 : k; binding.keyWasDown = true; binding = null; dirty = true; }
                return;
            }
        }
    }

    // ------------------------------------------------------------------ module row
    private void module(Mod m) {
        int x = cx0, w = cx1 - cx0, y = cur; cur += ROW + 2;
        boolean h = hov(x, y, x + w, y + ROW);
        Gfx.rrect(g, x, y, w, ROW, h ? 0x30FFFFFF : 0x1CFFFFFF);

        // toggle pill
        int tx = x + 8, ty = y + 6;
        Gfx.rrect(g, tx, ty, 24, 10, m.enabled ? Theme.accent() : 0xFF3A3450);
        int kx = m.enabled ? tx + 14 : tx + 2;
        g.fill(kx, ty + 2, kx + 8, ty + 8, 0xFFFFFFFF);

        Gfx.text(g, m.name, x + 40, y + 7, m.enabled ? Theme.TEXT : 0xFFC8C2E0);

        // right side: bind box + arrow
        String bn = binding == m ? "..." : Keys.name(m.key);
        int bw = Math.max(26, Gfx.tw(bn) + 10), bx2 = x + w - 22, bx1 = bx2 - bw;
        boolean hbind = hov(bx1, y + 3, bx2, y + ROW - 3);
        Gfx.rrect(g, bx1, y + 3, bw, ROW - 6, hbind || binding == m ? Theme.accentA(0x70) : 0x30FFFFFF);
        Gfx.text(g, bn, bx1 + (bw - Gfx.tw(bn)) / 2, y + 7, Theme.TEXT);
        boolean harrow = hov(x + w - 20, y, x + w, y + ROW);
        Gfx.text(g, m.expanded ? "v" : ">", x + w - 14, y + 7, harrow ? Theme.TEXT : Theme.DIM);

        // description (truncate to fit)
        int dx = x + 40 + Gfx.tw(m.name) + 10, avail = bx1 - dx - 6;
        if (avail > 30) {
            String d = m.desc;
            while (d.length() > 3 && Gfx.tw(d) > avail) d = d.substring(0, d.length() - 2);
            if (d.length() < m.desc.length()) d += "..";
            Gfx.text(g, d, dx, y + 7, 0xFF6F688A);
        }

        if (h) {
            if (pressed) {
                if (hbind) binding = m;
                else if (harrow) m.expanded = !m.expanded;
                else { m.toggle(); dirty = true; }
            } else if (rpressed) m.expanded = !m.expanded;
        }

        if (m.expanded) {
            for (Setting s : m.settings) setting(s);
            cur += 4;
        }
    }

    // ------------------------------------------------------------------ settings
    private void setting(Setting s) {
        int x = cx0 + 12, w = cx1 - cx0 - 12, y = cur;
        if (s instanceof Setting.Bool b) {
            cur += SROW;
            boolean h = hov(x, y, x + w, y + SROW - 2);
            g.fill(x, y, x + w, y + SROW - 2, h ? 0x26FFFFFF : 0x14FFFFFF);
            Gfx.text(g, b.name, x + 6, y + 4, Theme.TEXT);
            int sx = x + w - 28;
            Gfx.rrect(g, sx, y + 3, 22, 10, b.v ? Theme.accent() : 0xFF3A3450);
            int kx = b.v ? sx + 12 : sx + 2;
            g.fill(kx, y + 5, kx + 8, y + 11, 0xFFFFFFFF);
            if (h && pressed) { b.v = !b.v; dirty = true; }
        } else if (s instanceof Setting.Num n) {
            cur += SROW;
            n.v = slider("n" + System.identityHashCode(n), n.name, n.v, n.min, n.max, n.step, n.dec, y, x, w);
        } else if (s instanceof Setting.Mode m) {
            cur += SROW;
            boolean h = hov(x, y, x + w, y + SROW - 2);
            g.fill(x, y, x + w, y + SROW - 2, h ? 0x26FFFFFF : 0x14FFFFFF);
            Gfx.text(g, m.name, x + 6, y + 4, Theme.TEXT);
            String v = "< " + m.get() + " >";
            Gfx.text(g, v, x + w - 6 - Gfx.tw(v), y + 4, Theme.accent());
            if (h && pressed) { m.next(1); dirty = true; }
            else if (h && rpressed) { m.next(-1); dirty = true; }
        } else if (s instanceof Setting.Col c) {
            cur += SROW;
            g.fill(x, y, x + w, y + SROW - 2, 0x14FFFFFF);
            Gfx.text(g, c.name, x + 6, y + 4, Theme.TEXT);
            g.fill(x + w - 30, y + 3, x + w - 6, y + SROW - 5, 0xFF000000 | c.rgb());
            Gfx.outline(g, x + w - 31, y + 2, x + w - 5, y + SROW - 4, 0x60FFFFFF);
            int id = System.identityHashCode(c);
            cur += SROW - 2;
            c.r = (int) slider("r" + id, "R", c.r, 0, 255, 1, 0, cur - (SROW - 2), x + 8, w - 8);
            cur += SROW - 2;
            c.g = (int) slider("g" + id, "G", c.g, 0, 255, 1, 0, cur - (SROW - 2), x + 8, w - 8);
            cur += SROW - 2;
            c.b = (int) slider("b" + id, "B", c.b, 0, 255, 1, 0, cur - (SROW - 2), x + 8, w - 8);
        }
    }

    private double slider(String id, String label, double v, double min, double max, double step, int dec,
                          int y, int x, int w) {
        int hgt = SROW - 2;
        boolean h = hov(x, y, x + w, y + hgt);
        g.fill(x, y, x + w, y + hgt, h ? 0x26FFFFFF : 0x14FFFFFF);
        double t = Math.max(0, Math.min(1, (v - min) / (max - min)));
        int fx = x + (int) (w * t);
        g.fill(x, y, fx, y + hgt, Theme.accentA(0x55));
        g.fill(Math.max(x, fx - 1), y, Math.min(x + w, fx + 1), y + hgt, Theme.accent());
        Gfx.text(g, label, x + 6, y + 4, Theme.TEXT);
        String val = String.format("%." + dec + "f", v);
        Gfx.text(g, val, x + w - 6 - Gfx.tw(val), y + 4, Theme.TEXT);
        if (h && pressed) active = id;
        if (id.equals(active)) {
            if (down) {
                double nt = Math.max(0, Math.min(1, (mx - x) / (double) w));
                double nv = Math.round((min + nt * (max - min)) / step) * step;
                nv = Math.max(min, Math.min(max, Math.round(nv * 10000.0) / 10000.0));
                if (nv != v) { v = nv; dirty = true; }
            } else active = null;
        }
        return v;
    }

    // ------------------------------------------------------------------ misc
    private boolean inside(int x1, int y1, int x2, int y2) { return mx >= x1 && mx < x2 && my >= y1 && my < y2; }
    private boolean hov(int x1, int y1, int x2, int y2) { return inside(x1, y1, x2, y2) && my >= cy0 && my < cy1; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (verticalAmount * 20)));
        return true;
    }

    @Override
    public void removed() {
        ModuleManager.save();
        lastClosedMs = System.currentTimeMillis();
    }
}
