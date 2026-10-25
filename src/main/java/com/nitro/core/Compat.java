package com.nitro.core;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.render.RenderTickCounter;

import java.util.UUID;

/**
 * Every Minecraft/authlib call that changed name between 1.21.x releases lives here.
 * If the build fails, the error will most likely point at one of these lines - just rename the call.
 */
public final class Compat {
    private Compat() {}
    public static float tickProgress(RenderTickCounter c) { return c.getTickProgress(false); }
    public static String name(GameProfile p) { return p.name(); }
    public static UUID id(GameProfile p) { return p.id(); }
}
