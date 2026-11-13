package elinarcurios.util;

import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * Rainbow-gradient animated text.
 *
 * <p>
 * Ported from Avaritia's {@code LudicrousText.makeFabulous} (MIT License,
 * Copyright (c) 2015-2018 Spiteful Fox): every character is prefixed with a colour
 * code from a 7-colour wheel, and the wheel rotates over time so the text shimmers.
 * Safe to call on the server (returns the plain text there).
 */
public final class FabulousText {

    private static final EnumChatFormatting[] FABULOUSNESS = new EnumChatFormatting[] { EnumChatFormatting.RED,
        EnumChatFormatting.GOLD, EnumChatFormatting.YELLOW, EnumChatFormatting.GREEN, EnumChatFormatting.AQUA,
        EnumChatFormatting.BLUE, EnumChatFormatting.LIGHT_PURPLE };

    private FabulousText() {}

    public static String makeFabulous(final String input) {
        if (input == null || input.isEmpty()) return input;
        if (FMLCommonHandler.instance()
            .getEffectiveSide() != Side.CLIENT) return input;

        final double delay = 80.0D;
        final int step = 1;
        final int posstep = 1;

        final StringBuilder sb = new StringBuilder(input.length() * 3);
        final int offset = (int) Math.floor(net.minecraft.client.Minecraft.getSystemTime() / delay)
            % FABULOUSNESS.length;

        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);
            final int col = ((i * posstep) + FABULOUSNESS.length - offset) % FABULOUSNESS.length;
            sb.append(FABULOUSNESS[col].toString());
            sb.append(c);
        }
        return sb.toString();
    }
}
