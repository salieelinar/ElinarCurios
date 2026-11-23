package elinarcurios.client;

import net.minecraft.client.Minecraft;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Refreshes the cached "virtual goggles active" state once per client tick, so the per-tile-entity
 * render hook only has to read two cached fields instead of doing NBT lookups every frame.
 */
public final class ClientTickHandler {

    @SubscribeEvent
    public void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // Guard at the call site: ThaumGogglesSwap references Thaumcraft types.
        if (!Loader.isModLoaded("Thaumcraft")) return;
        elinarcurios.compat.ThaumGogglesSwap.refresh(Minecraft.getMinecraft().thePlayer);
    }
}
