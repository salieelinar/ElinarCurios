package elinarcurios.compat;

import com.gtnllib.text.TextEffects;

/**
 * GTNLLib 文字着色器的软接入层：拼出物品 tooltip 末行的「添加模组」标识。
 *
 * <p>
 * ★<b>调用点必须写 {@code Loader.isModLoaded("gtnllib")} 守卫，守卫不能挪进本类。</b>
 * 本类在类加载阶段就会解析 {@code com.gtnllib.text.TextEffects} 的符号，没装 GTNLLib 时
 * 光加载它就会 NoClassDefFoundError —— 与 {@code ThaumcraftCompat} 同一个坑（DEV_GUIDE §3.5）。
 *
 * <p>
 * GTNLLib 的行内格式是 {@code \u2063[gtnl:<rendererId>;<speed>;<colors>;...]<文本>\u2063[/gtnl]\u2063}
 * （见 {@code EffectTextParser.OPEN/CLOSE}），所以 {@code TextEffects.apply(text, style)} 可以把
 * 任意一段文字包成一个着色 span；一行里可以混排「纯文本 + 多个 span」，本标识就是
 * 「纯文本前缀 + GENESIS 着色的中文名 + 棱镜扫光着色的英文 id」。
 */
public final class GtnlTextCompat {

    /** 「添加模组：」前缀，纯文本（不着色）。 */
    private static final String PREFIX = "添加模组：";

    /** 模组中文名，结尾留一个空格与英文 id 隔开（与芷若给的样板逐字一致）。 */
    private static final String NAME_CN = "天玄造物 ";

    private static final String NAME_EN = "ElinarCurios";

    /** 拼好的整行，只算一次：字符串形态与状态无关，所有物品共用同一份（GTNLLib 按文本缓存布局）。 */
    private static String badgeLine;

    /**
     * 「添加模组：天玄造物 ElinarCurios」整行，带 GTNLLib 的两段着色。
     *
     * <p>
     * 返回值里带 U+2063 与 {@code [gtnl:...]} 标记，只能交给 GTNLLib 的字体渲染路径
     * （它 hook 了 {@code FontRenderer.drawString} / {@code drawStringWithShadow} /
     * {@code getStringWidth}）；放到别处当普通字符串处理会露出标记字符。
     */
    public static String badgeLine() {
        if (badgeLine == null) {
            badgeLine = PREFIX + TextEffects.apply(NAME_CN, TextEffects.GENESIS_COMPONENT_RARITY_SHADER)
                + TextEffects.apply(NAME_EN, TextEffects.PRISMATIC_SCAN);
        }
        return badgeLine;
    }

    private GtnlTextCompat() {}
}
