package elinarcurios.client;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.common.Reference;
import elinarcurios.compat.GtnlTextCompat;
import elinarcurios.machine.ElinarMachines;

/**
 * 给本 mod 所有物品的 tooltip 追加末行标识「添加模组：天玄造物 ElinarCurios」。
 *
 * <p>
 * 挂点选 {@link ItemTooltipEvent}：它在 {@code ItemStack.getTooltip()} 的**最后**触发（见 Forge 的
 * {@code ForgeEventFactory.onItemTooltip}），本 mod 物品自己的 tooltip、GT 机器的 tooltip（由 GT 的
 * {@code ItemMachines.addInformation} 生成，同样走 getTooltip）都在它之前加完，所以追加的行天然是最后一行。
 * 优先级取 {@link EventPriority#LOWEST}：让别家（例如 GT 的危险品提示）先加，我们永远垫底。
 *
 * <p>
 * 覆盖范围（「所有物品」按字面理解为两种来源）：
 * <ul>
 * <li>注册在 {@code elinarcurios} 名下的物品与方块物品（含 Thaumcraft 软联动的天玄之杖、SpiceOfLife 的午餐盒）；</li>
 * <li>本 mod 的 13 台 GT 机器 —— 它们的 ItemStack 属于 GT 的 {@code sBlockMachines}，靠 MTE 号识别
 * （见 {@link ElinarMachines#isOwnMachineStack(ItemStack)}）。</li>
 * </ul>
 *
 * <p>
 * 标识文本的两种形态：装了 GTNLLib 走它的文字着色器（{@link GtnlTextCompat}，守卫写在调用点）；
 * 没装则退化成原版烫金斜体的一行字 —— 保证任何环境都能看见这行，且不会露出格式标记。
 */
@SideOnly(Side.CLIENT)
public final class TooltipBadgeHandler {

    /** 没有 GTNLLib 时的退路：同样文案，原版 § 样式（烫金 + 斜体）。 */
    private static final String PLAIN_BADGE = EnumChatFormatting.GOLD + ""
        + EnumChatFormatting.ITALIC
        + "添加模组：天玄造物 ElinarCurios";

    /** 有没有 GTNLLib，只在首次出 tooltip 时探一次（Loader 的查询在 preInit 之后才可靠）。 */
    private static boolean gtnllibChecked;

    private static String badge;

    /** 客户端 preInit 调用一次。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(new TooltipBadgeHandler());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemTooltip(final ItemTooltipEvent event) {
        if (!isElinarItem(event.itemStack)) return;
        event.toolTip.add(badge());
    }

    /** 该物品是不是本 mod 的内容（自注册物品 / 本 mod 的 GT 机器）。 */
    private static boolean isElinarItem(final ItemStack stack) {
        if (stack == null) return false;
        final Item item = stack.getItem();
        if (item == null) return false;
        final GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(item);
        if (id != null && Reference.MOD_ID.equals(id.modId)) return true;
        // GT 不在时机器本就不存在，直接放过。
        if (!Loader.isModLoaded("gregtech")) return false;
        return ElinarMachines.isOwnMachineStack(stack);
    }

    private static String badge() {
        if (!gtnllibChecked) {
            gtnllibChecked = true;
            // ★守卫写在调用点：没装 GTNLLib 时 GtnlTextCompat 这个类绝不能被加载。
            badge = Loader.isModLoaded("gtnllib") ? GtnlTextCompat.badgeLine() : PLAIN_BADGE;
        }
        return badge;
    }

    private TooltipBadgeHandler() {}
}
