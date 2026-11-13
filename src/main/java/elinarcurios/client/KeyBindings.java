package elinarcurios.client;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

public class KeyBindings {

    private static final String KEYS_CATEGORY = "keys.elinarcurios.category";
    private static final String OPEN_BAG = "keys.elinarcurios.open";

    public static KeyBinding openBag = new KeyBinding(OPEN_BAG, Keyboard.KEY_B, KEYS_CATEGORY);
}
