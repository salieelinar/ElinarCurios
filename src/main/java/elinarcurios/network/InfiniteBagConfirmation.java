package elinarcurios.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import elinarcurios.ElinarCurios;
import gnu.trove.map.TIntShortMap;
import gnu.trove.map.hash.TIntShortHashMap;
import io.netty.buffer.ByteBuf;

/**
 * Click transaction confirmation. Derived from Avaritiaddons' InfinityChestConfirmation (MPL-2.0).
 */
public class InfiniteBagConfirmation implements IMessage {

    private int windowID;
    private short transactionID;
    private boolean confirmed;

    public InfiniteBagConfirmation() {}

    public InfiniteBagConfirmation(final int windowID, final short transactionID, boolean confirmed) {
        this.windowID = windowID;
        this.transactionID = transactionID;
        this.confirmed = confirmed;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        windowID = ByteBufUtils.readVarInt(buf, 4);
        transactionID = (short) ByteBufUtils.readVarShort(buf);
        confirmed = ByteBufUtils.readVarShort(buf) == 1;
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        ByteBufUtils.writeVarInt(buf, windowID, 4);
        ByteBufUtils.writeVarShort(buf, transactionID);
        ByteBufUtils.writeVarShort(buf, confirmed ? 1 : 0);
    }

    public static class Handler implements IMessageHandler<InfiniteBagConfirmation, InfiniteBagConfirmation> {

        static final TIntShortMap transactionMap = new TIntShortHashMap(10, 0.5f, -1, (short) -1);

        @Override
        public InfiniteBagConfirmation onMessage(final InfiniteBagConfirmation message, final MessageContext ctx) {
            final EntityPlayer entityPlayer = ctx.side == Side.SERVER ? ctx.getServerHandler().playerEntity
                : ElinarCurios.proxy.getEntityPlayerFromContext(ctx);
            switch (ctx.side) {
                case CLIENT:
                    Container container = null;
                    if (message.windowID == 0) container = entityPlayer.inventoryContainer;
                    else if (message.windowID == entityPlayer.openContainer.windowId)
                        container = entityPlayer.openContainer;
                    return container != null && !message.confirmed
                        ? new InfiniteBagConfirmation(message.windowID, message.transactionID, true)
                        : null;
                case SERVER:
                    final short transactionId = transactionMap.get(message.windowID);
                    if (transactionId != -1 && message.transactionID == transactionId
                        && entityPlayer.openContainer.windowId == message.windowID
                        && !entityPlayer.openContainer.isPlayerNotUsingContainer(entityPlayer))
                        entityPlayer.openContainer.setPlayerIsPresent(entityPlayer, true);
                    return new InfiniteBagConfirmation(message.windowID, message.transactionID, true);
                default:
                    return null;
            }
        }
    }
}
