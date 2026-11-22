package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import elinarcurios.ElinarCurios;
import elinarcurios.inventory.ContainerInfiniteBackpack;
import elinarcurios.util.BagSettings;
import elinarcurios.util.BagUtils;
import io.netty.buffer.ByteBuf;

/** Server -> client: full "extra config" state of the open / worn backpack. */
public class BagSettingsStatePacket implements IMessage {

    private float[] values = new float[BagSettings.COUNT];

    public BagSettingsStatePacket() {}

    public BagSettingsStatePacket(final float[] values) {
        this.values = values;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        final int count = Math.min(BagSettings.COUNT, buf.readByte());
        values = new float[BagSettings.COUNT];
        for (int i = 0; i < count; i++) values[i] = buf.readFloat();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeByte(BagSettings.COUNT);
        for (int i = 0; i < BagSettings.COUNT; i++) buf.writeFloat(values[i]);
    }

    public static class Handler implements IMessageHandler<BagSettingsStatePacket, IMessage> {

        @Override
        public IMessage onMessage(final BagSettingsStatePacket message, final MessageContext ctx) {
            if (!ctx.side.isClient()) return null;
            final EntityPlayer player = ElinarCurios.proxy.getEntityPlayerFromContext(ctx);
            if (player == null) return null;

            ItemStack bag = null;
            if (player.openContainer instanceof ContainerInfiniteBackpack) {
                bag = ((ContainerInfiniteBackpack) player.openContainer).getBagStack();
            }
            if (bag == null) bag = BagUtils.getWornInfiniteBackpack(player);
            if (bag != null) BagSettings.writeAll(bag, message.values);

            ElinarCurios.proxy.refreshExtraConfigIfOpen();
            return null;
        }
    }
}
