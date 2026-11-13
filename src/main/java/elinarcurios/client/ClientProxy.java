package elinarcurios.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.CommonProxy;
import elinarcurios.compat.ThaumcraftCompat;
import elinarcurios.gui.GuiEternalChest;
import elinarcurios.gui.GuiInfiniteBackpack;
import elinarcurios.gui.GuiLunchBox;
import elinarcurios.item.ItemElinarWand;
import elinarcurios.player.BagProperty;
import thaumcraft.client.renderers.item.ItemWandRenderer;

@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        super.preInit();
    }

    @Override
    public void init() {
        super.init();
        // TC soft-integration: register the TC 3D wand renderer for our own wand item.
        // TC's ClientProxy only registers the renderer for its own itemWandCasting instance;
        // without this, ItemElinarWand falls back to flat 2D rendering with no model/texture.
        if (ThaumcraftCompat.isAvailable()) {
            MinecraftForgeClient.registerItemRenderer(ItemElinarWand.instance, new ItemWandRenderer());
        }
        ClientRegistry.registerKeyBinding(KeyBindings.openBag);
        FMLCommonHandler.instance()
            .bus()
            .register(new KeyInputHandler());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new BackRenderHandler());
    }

    @Override
    public EntityPlayer getPlayerEntity(final MessageContext context) {
        return context.side.isClient() ? Minecraft.getMinecraft().thePlayer : super.getPlayerEntity(context);
    }

    @Override
    public EntityPlayer getEntityPlayerFromContext(final MessageContext ctx) {
        return Minecraft.getMinecraft().thePlayer;
    }

    @Override
    public void handleSyncBagMessage(final int entityId, final NBTTagCompound data) {
        final World world = Minecraft.getMinecraft().theWorld;
        if (world == null) return;
        // Prefer the entity by id; fall back to the local player (the client player
        // entity may have just been recreated on dimension change, before the world
        // lookup finds it - or the old entity was already removed).
        final Entity entity = world.getEntityByID(entityId);
        final EntityPlayer player = entity instanceof EntityPlayer ? (EntityPlayer) entity
            : Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            if (BagProperty.get(player) == null) BagProperty.register(player);
            BagProperty.get(player)
                .loadNBTData(data);
        }
    }

    @Override
    public void refreshBuffButtonsIfOpen() {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiInfiniteBackpack) {
            ((GuiInfiniteBackpack) Minecraft.getMinecraft().currentScreen).refreshBuffButtons();
        }
    }

    @Override
    public void refreshPageButtonsIfOpen(final int page) {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiInfiniteBackpack) {
            ((GuiInfiniteBackpack) Minecraft.getMinecraft().currentScreen).refreshPageButtons(page);
        } else if (Minecraft.getMinecraft().currentScreen instanceof GuiEternalChest) {
            ((GuiEternalChest) Minecraft.getMinecraft().currentScreen).refreshPageButtons(page);
        } else if (Minecraft.getMinecraft().currentScreen instanceof GuiLunchBox) {
            ((GuiLunchBox) Minecraft.getMinecraft().currentScreen).refreshPageButtons(page);
        }
    }
}
