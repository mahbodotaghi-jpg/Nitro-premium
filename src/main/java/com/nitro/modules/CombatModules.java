package com.nitro.modules;

import com.nitro.core.*;
import com.nitro.gui.HudEditor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

// ======================================================================= Target HUD
final class TargetHud extends HudMod {
    final Setting.Bool auto = add(new Setting.Bool("Auto PvP mode", true));
    final Setting.Bool showDist = add(new Setting.Bool("Show distance", true));
    final Setting.Bool showStats = add(new Setting.Bool("Show pops / combo", true));
    private float shown = -1;

    TargetHud() { super("Target HUD", "Health, armour, pops and combo of your target", Category.COMBAT, 300, 190); }

    private LivingEntity pick() {
        long now = System.currentTimeMillis();
        if (State.target != null && now - State.targetTime < 6000) return State.target;
        if (mc.targetedEntity instanceof LivingEntity l) return l;
        return null;
    }

    static String mode(PlayerEntity p, LivingEntity t) {
        ItemStack m = p.getMainHandStack();
        if (m.isOf(Items.MACE)) return "MACE";
        if (m.isIn(ItemTags.AXES)) return "AXE";
        if (m.isIn(ItemTags.SWORDS)) return "SWORD";
        if (m.isOf(Items.END_CRYSTAL)) return "CRYSTAL";
        if (m.isOf(Items.BOW)) return "BOW";
        if (m.isOf(Items.CROSSBOW)) return "CROSSBOW";
        if (m.isOf(Items.TRIDENT)) return "TRIDENT";
        if (m.isOf(Items.SPLASH_POTION) || m.isOf(Items.LINGERING_POTION)) return "POTION";
        if (p.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) || t.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) return "ELYTRA";
        return "FIST";
    }

    public void draw(DrawContext c, float td) {
        LivingEntity t = pick();
        String name; float hp, max, abs; boolean dead; double dist; int arm, pops; String md;
        if (t == null) {
            if (!HudEditor.editing) { w = 0; h = 0; return; }
            name = "Target"; hp = 20; max = 20; abs = 0; dead = false; dist = 3.1; arm = 20; pops = 0; md = "SWORD";
        } else {
            name = NickHider.apply(t.getName().getString()); hp = t.getHealth(); max = Math.max(1, t.getMaxHealth());
            abs = t.getAbsorptionAmount(); dead = t.isDead() || hp <= 0; dist = mc.player.distanceTo(t); arm = t.getArmor();
            pops = State.pops.getOrDefault(t.getUuid(), 0); md = auto.v ? mode(mc.player, t) : "";
        }
        if (shown < 0) shown = hp;
        shown += (hp - shown) * 0.18f;
        w = 164; h = 48;
        Gfx.glass(c, x, y, w, h);
        Gfx.text(c, name, x + 8, y + 6, dead ? Theme.DIM : Theme.accent());
        if (!md.isEmpty()) Gfx.text(c, md, x + w - 8 - Gfx.tw(md), y + 6, Theme.DIM);

        int bx = x + 8, by = y + 19, bw = w - 16;
        c.fill(bx, by, bx + bw, by + 6, 0x40FFFFFF);
        float ratio = Math.max(0, Math.min(1, shown / max));
        c.fill(bx, by, bx + (int) (bw * ratio), by + 6, Gfx.lerpColor(0xFFFF4060, 0xFF60FFA0, ratio));
        if (abs > 0) c.fill(bx, by + 6, bx + (int) (bw * Math.min(1, abs / max)), by + 8, 0xFFFFD040);

        if (dead) {
            Gfx.text(c, "ELIMINATED", x + 8, y + 32, Theme.accent());
        } else {
            String l = String.format("%.1f", hp + abs) + (showDist.v ? "   " + String.format("%.1f m", dist) : "") + "   ARM " + arm;
            Gfx.text(c, l, x + 8, y + 32, Theme.TEXT);
        }
        if (showStats.v) {
            String r = "P" + pops + "  C" + State.combo;
            Gfx.text(c, r, x + w - 8 - Gfx.tw(r), y + 32, Theme.DIM);
        }
    }
}

// ======================================================================= W-Tap
final class WTap extends Mod {
    final Setting.Num chance = add(new Setting.Num("Chance %", 100, 0, 100, 5, 0));
    final Setting.Num delay = add(new Setting.Num("Release ticks", 1, 1, 3, 1, 0));
    private int ticks; private boolean forced;

    WTap() { super("W-Tap", "Resets your sprint after every hit for full knockback", Category.COMBAT); }

    @Override public void onAttack(Entity e, Vec3d hit) {
        if (mc.player != null && mc.player.isSprinting() && ThreadLocalRandom.current().nextDouble() * 100 < chance.v) ticks = delay.i();
    }
    @Override public void onStartTick(MinecraftClient m) {
        if (ticks > 0) { m.options.forwardKey.setPressed(false); ticks--; forced = true; }
        else if (forced) { forced = false; m.options.forwardKey.setPressed(Keys.isDown(m.options.forwardKey)); }
    }
    @Override public void onDisable() {
        if (forced) { forced = false; mc.options.forwardKey.setPressed(Keys.isDown(mc.options.forwardKey)); }
        ticks = 0;
    }
}

// ======================================================================= Ripple
final class Ripple extends Mod {
    final Setting.Num size = add(new Setting.Num("Max radius", 1.6, 0.5, 4, 0.1, 1));
    private final List<double[]> waves = new ArrayList<>(); // x,y,z,age

    Ripple() { super("Ripple", "A wave of light over whoever you hit", Category.COMBAT); }

    @Override public void onAttack(Entity e, Vec3d hit) { waves.add(new double[]{hit.x, hit.y, hit.z, 0}); }
    @Override public void onTick(MinecraftClient m) {
        for (int i = waves.size() - 1; i >= 0; i--) {
            double[] w = waves.get(i);
            w[3]++;
            double r = size.v * w[3] / 10.0;
            for (int k = 0; k < 18; k++) {
                double a = k * Math.PI * 2 / 18;
                m.particleManager.addParticle(ParticleTypes.END_ROD, w[0] + Math.cos(a) * r, w[1], w[2] + Math.sin(a) * r,
                    Math.cos(a) * 0.01, 0.0, Math.sin(a) * 0.01);
            }
            if (w[3] >= 10) waves.remove(i);
        }
    }
    @Override public void onWorldChange() { waves.clear(); }
}

// ======================================================================= Hit Splash
final class HitSplash extends Mod {
    final Setting.Num amount = add(new Setting.Num("Droplets", 18, 4, 60, 2, 0));
    HitSplash() { super("Hit Splash", "Every hit splashes glowing ink where it lands", Category.COMBAT); }
    @Override public void onAttack(Entity e, Vec3d hit) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < amount.i(); i++)
            mc.particleManager.addParticle(ParticleTypes.GLOW_SQUID_INK, hit.x, hit.y, hit.z,
                (r.nextDouble() - 0.5) * 0.25, r.nextDouble() * 0.18, (r.nextDouble() - 0.5) * 0.25);
    }
}

// ======================================================================= Hitboxes
final class Hitboxes extends Mod {
    final Setting.Bool players = add(new Setting.Bool("Players", true));
    final Setting.Bool mobs = add(new Setting.Bool("Mobs", true));
    final Setting.Bool items = add(new Setting.Bool("Items", false));
    final Setting.Num range = add(new Setting.Num("Range", 64, 8, 256, 8, 0));
    final Setting.Num width = add(new Setting.Num("Line width", 1, 1, 4, 1, 0));
    final Setting.Col color = add(new Setting.Col("Color", 0xFFFFFF));

    Hitboxes() { super("Hitboxes", "Clean outlines around entities", Category.COMBAT); }

    @Override public void onHud(DrawContext c, float td) {
        if (!Proj.ready) return;
        for (Entity e : mc.world.getEntities()) {
            if (!Targets.wanted(e, players.v, mobs.v, items.v) || Targets.dist(e) > range.v) continue;
            Gfx.wire(c, Proj.box(e, td), color.argb(230), width.i());
        }
    }
}

// ======================================================================= Radiance
final class Radiance extends Mod {
    final Setting.Bool players = add(new Setting.Bool("Players", true));
    final Setting.Bool mobs = add(new Setting.Bool("Mobs", true));
    final Setting.Bool items = add(new Setting.Bool("Items", true));
    final Setting.Num range = add(new Setting.Num("Range", 48, 8, 128, 8, 0));
    final Setting.Col color = add(new Setting.Col("Color", 0xC9A0FF));

    Radiance() { super("Radiance", "A glowing outline on players, mobs and items", Category.COMBAT); }

    @Override public void onHud(DrawContext c, float td) {
        if (!Proj.ready) return;
        for (Entity e : mc.world.getEntities()) {
            if (!Targets.wanted(e, players.v, mobs.v, items.v) || Targets.dist(e) > range.v) continue;
            Box b = Proj.box(e, td);
            Gfx.wire(c, b, color.argb(0x16), 7);
            Gfx.wire(c, b, color.argb(0x30), 4);
            Gfx.wire(c, b, color.argb(0xE0), 1);
        }
    }
}

// ======================================================================= Combo / Totem counters
final class ComboHud extends HudMod {
    ComboHud() { super("Combo Counter", "Your hit streak", Category.COMBAT, 300, 150); }
    public void draw(DrawContext c, float td) {
        if (State.combo <= 0 && !HudEditor.editing) { w = 0; h = 0; return; }
        chip(c, "COMBO  x" + Math.max(State.combo, HudEditor.editing ? 7 : 0));
    }
}

final class TotemHud extends HudMod {
    TotemHud() { super("Totem Counter", "Totems popped around you", Category.COMBAT, 300, 220); }
    public void draw(DrawContext c, float td) {
        List<Map.Entry<java.util.UUID, Integer>> top = new ArrayList<>(State.pops.entrySet());
        top.sort((a, b) -> b.getValue() - a.getValue());
        if (top.isEmpty() && !HudEditor.editing) { w = 0; h = 0; return; }
        List<String> lines = new ArrayList<>();
        lines.add("TOTEMS  " + State.totalPops);
        for (int i = 0; i < Math.min(4, top.size()); i++)
            lines.add(NickHider.apply(State.popNames.getOrDefault(top.get(i).getKey(), "?")) + "  " + top.get(i).getValue());
        int mw = 0; for (String s : lines) mw = Math.max(mw, Gfx.tw(s));
        w = mw + 14; h = lines.size() * 11 + 7;
        Gfx.glass(c, x, y, w, h);
        int yy = y + 4;
        for (int i = 0; i < lines.size(); i++) { Gfx.text(c, lines.get(i), x + 7, yy, i == 0 ? Theme.accent() : Theme.TEXT); yy += 11; }
    }
}

// ======================================================================= Custom crosshair
final class CustomCrosshair extends Mod {
    final Setting.Mode style = add(new Setting.Mode("Style", new String[]{"Dot", "Cross", "Plus", "Circle", "Square", "Diamond"}, 2));
    final Setting.Num size = add(new Setting.Num("Size", 6, 2, 20, 1, 0));
    final Setting.Num gap = add(new Setting.Num("Gap", 3, 0, 10, 1, 0));
    final Setting.Num thick = add(new Setting.Num("Thickness", 1, 1, 4, 1, 0));
    final Setting.Col color = add(new Setting.Col("Color", 0xFFFFFF));
    final Setting.Bool hit = add(new Setting.Bool("Hit confirmation", true));
    final Setting.Bool cooldown = add(new Setting.Bool("Attack cooldown", true));

    CustomCrosshair() { super("Custom Crosshair", "Six styles, hit confirmation and a clear cooldown", Category.COMBAT); }

    @Override public boolean replaceCrosshair(DrawContext c, float td) {
        if (!mc.options.getPerspective().isFirstPerson()) return true;
        int cx = c.getScaledWindowWidth() / 2, cy = c.getScaledWindowHeight() / 2;
        int col = color.argb(255), s = size.i(), g = gap.i(), t = thick.i(), h = t / 2;
        switch (style.get()) {
            case "Dot" -> c.fill(cx - t, cy - t, cx + t, cy + t, col);
            case "Cross" -> { Gfx.line(c, cx - s, cy - s, cx - g, cy - g, t, col); Gfx.line(c, cx + s, cy - s, cx + g, cy - g, t, col);
                              Gfx.line(c, cx - s, cy + s, cx - g, cy + g, t, col); Gfx.line(c, cx + s, cy + s, cx + g, cy + g, t, col); }
            case "Circle" -> Gfx.ring(c, cx, cy, s, t, col);
            case "Square" -> Gfx.outline(c, cx - s, cy - s, cx + s, cy + s, col);
            case "Diamond" -> { Gfx.line(c, cx, cy - s, cx + s, cy, t, col); Gfx.line(c, cx + s, cy, cx, cy + s, t, col);
                                Gfx.line(c, cx, cy + s, cx - s, cy, t, col); Gfx.line(c, cx - s, cy, cx, cy - s, t, col); }
            default -> { c.fill(cx - s, cy - h, cx - g, cy - h + t, col); c.fill(cx + g, cy - h, cx + s, cy - h + t, col);
                         c.fill(cx - h, cy - s, cx - h + t, cy - g, col); c.fill(cx - h, cy + g, cx - h + t, cy + s, col); }
        }
        if (hit.v) {
            long age = System.currentTimeMillis() - State.hitMarkMs;
            if (age < 220) {
                int a = (int) (255 * (1 - age / 220.0)), rc = (a << 24) | 0xFF4060;
                Gfx.line(c, cx - 4, cy - 4, cx - 9, cy - 9, 1, rc); Gfx.line(c, cx + 4, cy - 4, cx + 9, cy - 9, 1, rc);
                Gfx.line(c, cx - 4, cy + 4, cx - 9, cy + 9, 1, rc); Gfx.line(c, cx + 4, cy + 4, cx + 9, cy + 9, 1, rc);
            }
        }
        if (cooldown.v && mc.player != null) {
            float cd = mc.player.getAttackCooldownProgress(0f);
            if (cd < 1f) {
                int bw = 20, by = cy + Math.max(s, 8) + 6;
                c.fill(cx - bw / 2, by, cx + bw / 2, by + 2, 0x60FFFFFF);
                c.fill(cx - bw / 2, by, cx - bw / 2 + (int) (bw * cd), by + 2, Theme.accent());
            }
        }
        return true;
    }
}
