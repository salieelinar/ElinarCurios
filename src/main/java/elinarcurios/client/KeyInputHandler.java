package elinarcurios.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.ElinarCurios;
import elinarcurios.client.overlay.BagOverlayState;
import elinarcurios.gui.GuiInfiniteBackpack;
import elinarcurios.item.ItemInfiniteBackpack;
import elinarcurios.network.OpenBagPacket;
import elinarcurios.network.RequestWornBagPacket;
import elinarcurios.util.BagUtils;

@SideOnly(Side.CLIENT)
public class KeyInputHandler {

    /**
     * The client player entity we last saw. 1.7.10 rebuilds EntityClientPlayerMP on
     * dimension change (Minecraft.setDimensionAndSpawnPlayer), discarding its extended
     * properties (the worn-bag property lives on the entity), so we re-request the worn
     * backpack whenever the entity instance changes.
     */
    private EntityPlayer lastTrackedPlayer;

    /** Rising-edge latches for the two backpack hotkeys (both are polled physically). */
    private boolean lastOpenBagKeyDown;
    private boolean lastOpenOverlayKeyDown;

    @SubscribeEvent
    public void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        final EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null || player == lastTrackedPlayer) return;
        lastTrackedPlayer = player;
        // Player entity (re)created - ask the server to resend the worn backpack.
        ElinarCurios.networkWrapper.sendToServer(new RequestWornBagPacket.Message());
    }

    /**
     * Two independent hotkeys:
     * <ul>
     * <li>{@code openBag}: only without any GUI open, asks the server to open the combined
     * backpack GUI (never replaces a GUI the player is using);</li>
     * <li>{@code openBagOverlay}: only with a GUI open, toggles the floating panel above it.</li>
     * </ul>
     *
     * <p>
     * Polled in the client tick instead of {@code InputEvent.KeyInputEvent}: that event is not
     * dispatched while a GUI screen has focus, and the overlay is exactly the "GUI already open"
     * case. LWJGL's physical key state is unaffected by GUI key consumption.
     */
    @SubscribeEvent
    public void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        final Minecraft mc = Minecraft.getMinecraft();
        final EntityPlayer player = mc.thePlayer;

        final boolean openBagDown = isKeyDown(KeyBindings.openBag);
        final boolean openBagPressed = openBagDown && !lastOpenBagKeyDown;
        lastOpenBagKeyDown = openBagDown;

        final boolean openOverlayDown = isKeyDown(KeyBindings.openBagOverlay);
        final boolean openOverlayPressed = openOverlayDown && !lastOpenOverlayKeyDown;
        lastOpenOverlayKeyDown = openOverlayDown;

        if (player == null || player.isDead) return;
        if (openBagPressed) openBackpackGui(mc, player);
        if (openOverlayPressed) toggleBackpackOverlay(mc, player);
    }

    /**
     * 「打开无尽背包」: no GUI open -> server opens the combined backpack GUI. While any GUI is
     * open this hotkey stays idle, so it can never replace the screen the player is using.
     */
    private static void openBackpackGui(final Minecraft mc, final EntityPlayer player) {
        if (mc.currentScreen != null || !mc.inGameHasFocus) return;
        final byte from;
        if (BagUtils.clientIsWearingInfiniteBackpack(player)) {
            from = OpenBagPacket.FROM_WEARING;
        } else {
            final ItemStack held = player.getHeldItem();
            if (held == null || !ItemInfiniteBackpack.isInfiniteBackpack(held)) return;
            from = OpenBagPacket.FROM_HOLDING;
        }
        ElinarCurios.networkWrapper.sendToServer(new OpenBagPacket.Message(from));
    }

    /**
     * 「打开无尽背包悬窗」: toggles the floating panel above the current GUI. Without a GUI there
     * is nothing to float above (the panel only exists while a GUI is open), so it does nothing.
     */
    private static void toggleBackpackOverlay(final Minecraft mc, final EntityPlayer player) {
        if (mc.currentScreen == null) return;
        // The combined backpack GUI already shows the storage, a floating copy would duplicate it.
        if (mc.currentScreen instanceof GuiInfiniteBackpack) return;
        if (BagOverlayState.isOpen()) {
            BagOverlayState.close();
            return;
        }
        final int source = bagSource(player);
        if (source >= 0) BagOverlayState.requestOpen(source);
    }

    /** 0 = worn, 1 = held, -1 = not available. */
    private static int bagSource(final EntityPlayer player) {
        if (BagUtils.clientIsWearingInfiniteBackpack(player)) return 0;
        final ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof ItemInfiniteBackpack) return 1;
        return -1;
    }

    private static boolean isKeyDown(final KeyBinding binding) {
        final int keyCode = binding.getKeyCode();
        return keyCode > 0 && Keyboard.isKeyDown(keyCode);
    }
}
