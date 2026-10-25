package com.nitro.modules;

import com.nitro.core.*;
import com.nitro.gui.HudEditor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class FpsHud extends HudMod {
    FpsHud() { super("FPS", "Frames per second", Category.HUD, 6, 6); }
    public void draw(DrawContext c, float td) { chip(c, "FPS  " + mc.getCurrentFps()); }
}

final class CoordsHud extends HudMod {
    CoordsHud() { super("Coordinates", "Your XYZ position", Category.HUD, 6, 26); }
    public void draw(DrawContext c, float td) {
        chip(c, "XYZ  " + mc.player.getBlockX() + "  " + mc.player.getBlockY() + "  " + mc.player.getBlockZ());
    }
}

final class DirectionHud extends HudMod {
    DirectionHud() { super("Direction", "Facing direction and yaw", Category.HUD, 6, 46); }
    public void draw(DrawContext c, float td) {
        String f = mc.player.getHorizontalFacing().asString();
        f = Character.toUpperCase(f.charAt(0)) + f.substring(1);
        float yaw = ((mc.player.getYaw() % 360) + 540) % 360 - 180;
        chip(c, f + "  " + Math.round(yaw) + "°");
    }
}

final class PingHud extends HudMod {
    PingHud() { super("Ping", "Latency to the server", Category.HUD, 6, 66); }
    public void draw(DrawContext c, float td) {
        int ping = 0;
        if (mc.getNetworkHandler() != null) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (e != null) ping = e.getLatency();
        }
        chip(c, "PING  " + ping + " ms");
    }
}

final class TimeHud extends HudMod {
    final Setting.Bool seconds = add(new Setting.Bool("Show seconds", false));
    TimeHud() { super("Time", "Current real-world time", Category.HUD, 6, 86); }
    public void draw(DrawContext c, float td) {
        chip(c, LocalTime.now().format(DateTimeFormatter.ofPattern(seconds.v ? "HH:mm:ss" : "HH:mm")));
    }
}

final class ProfileHud extends HudMod {
    ProfileHud() { super("Profile", "Name, server and session time", Category.HUD, 6, 106); }
    public void draw(DrawContext c, float td) {
        long s = (System.currentTimeMillis() - State.SESSION) / 1000;
        String srv = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Singleplayer";
        String name = NickHider.apply(mc.player.getName().getString());
        chip(c, name + "  |  " + srv + "  |  " + (s / 60) + ":" + String.format("%02d", s % 60));
    }
}

final class CpsHud extends HudMod {
    CpsHud() { super("CPS", "Clicks per second", Category.HUD, 6, 126); }
    public void draw(DrawContext c, float td) { chip(c, "CPS  " + State.cps(true) + " | " + State.cps(false)); }
}

final class Keystrokes extends HudMod {
    Keystrokes() { super("Keystrokes", "WASD, mouse buttons and CPS", Category.HUD, 6, 160); }
    private void key(DrawContext c, int x, int y, int kw, int kh, String s, boolean d) {
        Gfx.rrect(c, x, y, kw, kh, d ? Theme.accentA(0xB0) : 0x90100C1C);
        c.fill(x + 1, y, x + kw - 1, y + 1, d ? 0x60FFFFFF : 0x28FFFFFF);
        Gfx.text(c, s, x + (kw - Gfx.tw(s)) / 2, y + (kh - 8) / 2, Theme.TEXT);
    }
    public void draw(DrawContext c, float td) {
        int k = 22, g = 2;
        key(c, x + k + g, y, k, k, "W", Keys.isDown(mc.options.forwardKey));
        key(c, x, y + k + g, k, k, "A", Keys.isDown(mc.options.leftKey));
        key(c, x + k + g, y + k + g, k, k, "S", Keys.isDown(mc.options.backKey));
        key(c, x + 2 * (k + g), y + k + g, k, k, "D", Keys.isDown(mc.options.rightKey));
        int bw = (3 * k + 2 * g - g) / 2;
        key(c, x, y + 2 * (k + g), bw, 20, "L " + State.cps(true), Keys.mouse(0));
        key(c, x + bw + g, y + 2 * (k + g), bw, 20, "R " + State.cps(false), Keys.mouse(1));
        key(c, x, y + 2 * (k + g) + 22, 3 * k + 2 * g, 12, "", Keys.down(32));
        w = 3 * k + 2 * g; h = 2 * (k + g) + 34;
    }
}

final class ArmorHud extends HudMod {
    ArmorHud() { super("Armor Status", "Armor and held item durability", Category.HUD, 6, 270); }
    public void draw(DrawContext c, float td) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
        int count = 0;
        for (EquipmentSlot sl : slots) if (!mc.player.getEquippedStack(sl).isEmpty()) count++;
        if (count == 0 && !HudEditor.editing) { w = 0; h = 0; return; }
        w = 58; h = Math.max(24, count * 18 + 8);
        Gfx.glass(c, x, y, w, h);
        int yy = y + 4;
        for (EquipmentSlot sl : slots) {
            ItemStack s = mc.player.getEquippedStack(sl);
            if (s.isEmpty()) continue;
            c.drawItem(s, x + 4, yy);
            String t = s.isDamageable() ? (100 * (s.getMaxDamage() - s.getDamage()) / s.getMaxDamage()) + "%" : (s.getCount() > 1 ? "x" + s.getCount() : "");
            Gfx.text(c, t, x + 24, yy + 4, Theme.TEXT);
            yy += 18;
        }
    }
}

final class PotionHud extends HudMod {
    PotionHud() { super("Potion Effects", "Active status effects", Category.HUD, 6, 360); }
    public void draw(DrawContext c, float td) {
        List<String> lines = new ArrayList<>();
        for (StatusEffectInstance e : mc.player.getStatusEffects()) {
            String n = e.getEffectType().value().getName().getString();
            if (e.getAmplifier() > 0) n += " " + (e.getAmplifier() + 1);
            String t = e.isInfinite() ? "inf" : (e.getDuration() / 20 / 60) + ":" + String.format("%02d", (e.getDuration() / 20) % 60);
            lines.add(n + "  " + t);
        }
        if (lines.isEmpty()) { if (HudEditor.editing) lines.add("Speed II  1:30"); else { w = 0; h = 0; return; } }
        int mw = 0; for (String s : lines) mw = Math.max(mw, Gfx.tw(s));
        w = mw + 12; h = lines.size() * 11 + 6;
        Gfx.glass(c, x, y, w, h);
        int yy = y + 4; for (String s : lines) { Gfx.text(c, s, x + 6, yy, Theme.TEXT); yy += 11; }
    }
}

final class ActiveModulesHud extends HudMod {
    ActiveModulesHud() { super("Active Modules", "List of enabled modules", Category.HUD, 9999, 6); }
    public void draw(DrawContext c, float td) {
        List<String> names = new ArrayList<>();
        for (Mod m : ModuleManager.ALL) if (m.enabled && !m.isHud() && m.cat != Category.CLIENT) names.add(m.name);
        names.sort(Comparator.comparingInt((String s) -> -Gfx.tw(s)));
        if (names.isEmpty()) { if (HudEditor.editing) names.add("Example"); else { w = 0; h = 0; return; } }
        w = Gfx.tw(names.get(0)) + 14; h = names.size() * 12 + 4;
        int yy = y + 2;
        for (String s : names) {
            int tw = Gfx.tw(s);
            int xx = x + w - tw - 10;
            Gfx.rrect(c, xx - 2, yy, tw + 10, 12, Theme.bg());
            c.fill(xx + tw + 6, yy + 1, xx + tw + 8, yy + 11, Theme.accent());
            Gfx.text(c, s, xx + 2, yy + 2, Theme.TEXT);
            yy += 12;
        }
    }
}

final class Notifications extends HudMod {
    private record N(String title, String msg, long t) {}
    static Notifications I;
    private final List<N> list = new ArrayList<>();

    Notifications() { super("Notifications", "Pop-up alerts from modules", Category.HUD, 9999, 40); I = this; }

    static void push(String title, String msg) {
        if (I == null || !I.enabled) return;
        I.list.add(new N(title, msg, System.currentTimeMillis()));
        if (I.list.size() > 6) I.list.remove(0);
    }

    public void draw(DrawContext c, float td) {
        long now = System.currentTimeMillis();
        list.removeIf(n -> now - n.t() > 4000);
        List<N> show = new ArrayList<>(list);
        if (show.isEmpty()) { if (HudEditor.editing) show.add(new N("Nitro", "Example notification", now)); else { w = 0; h = 0; return; } }
        w = 150; int yy = y;
        for (N n : show) {
            long age = now - n.t();
            int off = age < 250 ? (int) ((1 - age / 250.0) * 40) : 0;
            Gfx.glass(c, x + off, yy, w, 26);
            Gfx.text(c, n.title(), x + off + 6, yy + 4, Theme.accent());
            Gfx.text(c, n.msg(), x + off + 6, yy + 14, Theme.TEXT);
            yy += 30;
        }
        h = Math.max(26, yy - y);
    }
}
