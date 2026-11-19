package elinarcurios.player;

import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Drives {@link WandPoolStorage}: periodic flush for crash safety and a flush on world save.
 * Server-stopping is handled from the mod lifecycle handler, which runs before the world is gone.
 */
public final class WandPoolSaveHandler {

    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        WandPoolStorage.tick();
    }

    @SubscribeEvent
    public void onWorldSave(final WorldEvent.Save event) {
        WandPoolStorage.saveIfDirty();
    }
}
