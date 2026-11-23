package elinarcurios.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.CommonProxy;
import elinarcurios.client.overlay.BagOverlayHandler;
import elinarcurios.client.overlay.BagOverlayNEICompat;
import elinarcurios.client.overlay.BagOverlayState;
import elinarcurios.compat.ThaumcraftCompat;
import elinarcurios.gui.GuiEternalChest;
import elinarcurios.gui.GuiInfiniteBackpack;
import elinarcurios.gui.GuiLunchBox;
import elinarcurios.item.ItemElinarWand;
import elinarcurios.network.BagOverlaySyncPacket;
import elinarcurios.player.BagProperty;
import elinarcurios.util.BagSettings;

@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        super.preInit();
        // 本 mod 所有物品 tooltip 的末行标识（纯客户端事件）。preInit 里挂上，早于任何 GUI 打开。
        TooltipBadgeHandler.register();
        // 每 tick 缓存一次护目镜展示状态，渲染路径只读缓存。
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientTickHandler());
    }

    @Override
    public void init() {
        super.init();
        // TC soft-integration: register the TC 3D wand renderer for our own wand item.
        // TC's ClientProxy only registers the renderer for its own itemWandCasting instance;
        // without this, ItemElinarWand falls back to flat 2D rendering with no model/texture.
        if (ThaumcraftCompat.isAvailable()) {
            MinecraftForgeClient.registerItemRenderer(ItemElinarWand.instance, new ElinarWandRenderer());
        }
        ClientRegistry.registerKeyBinding(KeyBindings.openBag);
        FMLCommonHandler.instance()
            .bus()
            .register(new KeyInputHandler());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new BackRenderHandler());
        // 无尽背包浮窗:Forge 事件(绘制/鼠标/键盘)与 FML 事件(tick)分属两条总线,同一个实例都挂。
        final BagOverlayHandler bagOverlayHandler = new BagOverlayHandler();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(bagOverlayHandler);
        FMLCommonHandler.instance()
            .bus()
            .register(bagOverlayHandler);
        // NEI 物品面板与浮窗重叠时让 NEI 退让（hideItemPanelSlot）。守卫写调用点：compat 类
        // 直接引用 NEI 类型，无 NEI 时整个类不加载。
        if (Loader.isModLoaded("NotEnoughItems")) {
            BagOverlayNEICompat.register();
        }
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
    public void refreshExtraConfigIfOpen() {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiInfiniteBackpack) {
            ((GuiInfiniteBackpack) Minecraft.getMinecraft().currentScreen).refreshExtraConfig();
        }
    }

    @Override
    public void applyBackpackClientFlight(final EntityPlayer player, final ItemStack bag) {
        final float speed = BagSettings.getFloat(bag, BagSettings.FLY_SPEED);
        player.capabilities.setFlySpeed(0.05F + 0.05F * speed);
        if (!player.capabilities.isFlying) return;

        final float vertical = BagSettings.getFloat(bag, BagSettings.FLY_VERTICAL);
        if (vertical > 0F && player.motionY != 0D && !player.onGround) {
            final boolean jump = Minecraft.getMinecraft().gameSettings.keyBindJump.getIsKeyPressed();
            final boolean sneak = Minecraft.getMinecraft().gameSettings.keyBindSneak.getIsKeyPressed();
            if (jump && !sneak) {
                player.motionY = 0.225F * vertical;
            } else if (sneak && !jump) {
                player.motionY = -0.225F * vertical;
            }
        }

        if (BagSettings.getBoolean(bag, BagSettings.FLY_INERTIA) && player.moveForward == 0F
            && player.moveStrafing == 0F) {
            player.motionX *= 0.5D;
            player.motionZ *= 0.5D;
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

    @Override
    public void handleBagOverlaySync(final BagOverlaySyncPacket message) {
        BagOverlayState.onSync(message);
    }

    @Override
    public void handleWandPoolSync(final java.util.Map<String, Long> values, final String ownerName) {
        WandPoolClient.apply(values, ownerName);
    }

    @Override
    public boolean hasClientWandPool() {
        return WandPoolClient.isValid();
    }

    @Override
    public long getClientWandPool(final String tag) {
        return WandPoolClient.get(tag);
    }

    @Override
    public java.util.Map<String, Long> getClientWandPoolMap() {
        return WandPoolClient.snapshot();
    }

    @Override
    public String getClientWandPoolOwner() {
        return WandPoolClient.ownerName();
    }
}
