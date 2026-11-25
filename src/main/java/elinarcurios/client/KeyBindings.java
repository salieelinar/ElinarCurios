package elinarcurios.client;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

public class KeyBindings {

    private static final String KEYS_CATEGORY = "keys.elinarcurios.category";

    /**
     * 「打开无尽背包」: no GUI open -&gt; open the full backpack GUI (the one merged with the
     * player inventory).
     */
    public static KeyBinding openBag = new KeyBinding("keys.elinarcurios.open", Keyboard.KEY_B, KEYS_CATEGORY);

    /**
     * 「打开无尽背包悬窗」: GUI open -&gt; toggle the floating backpack panel on top of it.
     */
    public static KeyBinding openBagOverlay = new KeyBinding(
        "keys.elinarcurios.open.overlay",
        Keyboard.KEY_F6,
        KEYS_CATEGORY);
}
