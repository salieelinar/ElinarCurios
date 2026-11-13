package elinarcurios.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.ElinarCurios;
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

    @SubscribeEvent
    public void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        final EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null || player == lastTrackedPlayer) return;
        lastTrackedPlayer = player;
        // Player entity (re)created - ask the server to resend the worn backpack.
        ElinarCurios.networkWrapper.sendToServer(new RequestWornBagPacket.Message());
    }

    @SubscribeEvent
    public void handleKeyInputEvent(final InputEvent.KeyInputEvent event) {
        onInput();
    }

    @SubscribeEvent
    public void handleMouseInputEvent(final InputEvent.MouseInputEvent event) {
        onInput();
    }

    private void onInput() {
        if (!KeyBindings.openBag.isPressed()) return;
        final Minecraft mc = Minecraft.getMinecraft();
        if (!mc.inGameHasFocus) return;
        final EntityPlayer player = mc.thePlayer;
        if (player == null || player.isDead) return;

        byte from = OpenBagPacket.FROM_WEARING;
        if (!BagUtils.clientIsWearingInfiniteBackpack(player)) {
            final ItemStack held = player.getHeldItem();
            if (held != null && held.getItem() instanceof ItemInfiniteBackpack) from = OpenBagPacket.FROM_HOLDING;
        }
        ElinarCurios.networkWrapper.sendToServer(new OpenBagPacket.Message(from));
    }
}
