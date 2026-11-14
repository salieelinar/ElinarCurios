package elinarcurios.machine;

import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** 世界卸载时把该世界的存储器从 UUID 表里摘掉（避免跨存档/换维度留下死引用）。 */
public final class BeamVoidRegistryHandler {

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        BeamVoidStorageRegistry.clearWorld(event.world);
    }
}
