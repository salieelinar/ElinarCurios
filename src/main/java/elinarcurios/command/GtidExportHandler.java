package elinarcurios.command;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Drives {@link GtidExport} from the server tick; registered on the FML bus in CommonProxy. */
public class GtidExportHandler {

    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) GtidExport.tick();
    }
}
