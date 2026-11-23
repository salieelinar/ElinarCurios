package elinarcurios.client;

import net.minecraft.client.Minecraft;
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

    /** Rising-edge latch for the backpack hotkey (works while a GUI screen is open too). */
    private boolean lastBagKeyDown;

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
     * Single hotkey path for both cases: no GUI open -&gt; vanilla behaviour (server opens the
     * combined backpack GUI); GUI open -&gt; toggle the floating overlay.
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
        final int keyCode = KeyBindings.openBag.getKeyCode();
        final boolean down = keyCode > 0 && Keyboard.isKeyDown(keyCode);
        final boolean pressed = down && !lastBagKeyDown;
        lastBagKeyDown = down;
        if (!pressed || player == null || player.isDead) return;

        if (mc.currentScreen != null) {
            // A GUI is open: never replace it, toggle the floating panel instead. The vanilla
            // backpack GUI already shows the bag, so the hotkey is a no-op there.
            if (mc.currentScreen instanceof GuiInfiniteBackpack) return;
            if (BagOverlayState.isOpen()) {
                BagOverlayState.close();
                return;
            }
            final int source = bagSource(player);
            if (source >= 0) BagOverlayState.requestOpen(source);
            return;
        }

        if (!mc.inGameHasFocus) return;
        final byte from = bagSource(player) == 1 ? OpenBagPacket.FROM_HOLDING : OpenBagPacket.FROM_WEARING;
        ElinarCurios.networkWrapper.sendToServer(new OpenBagPacket.Message(from));
    }

    /** 0 = worn, 1 = held, -1 = not available. */
    private static int bagSource(final EntityPlayer player) {
        if (BagUtils.clientIsWearingInfiniteBackpack(player)) return 0;
        final ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof ItemInfiniteBackpack) return 1;
        return -1;
    }
}
