package elinarcurios.machine;

/**
 * 装配线样板分配接口的公共契约。
 *
 * <p>
 * 物品版与流体版的父类是<b>两个不相关的类</b>（{@code MTEHatchInputBus} / {@code MTEHatchInput}，
 * 因为 GT5U 用两个不同的 adder 列表分别接它们，见 {@code MTEMultiBlockBase#addInputBusToMachineList} /
 * {@code #addInputHatchToMachineList}），所以共享逻辑只能抽成接口。
 * GUI（{@link elinarcurios.gui.MirrorGui}）与闪存交互都按这个契约写。
 */
public interface IPatternMirror {

    /** 当前分配的格号（1-based）：对应装配线读的第 {@code slotIndex} 条输入总线 / 第 {@code slotIndex} 个输入仓。 */
    int getSlotIndex();

    void setSlotIndex(int index);

    /** 格号上限：物品版 16、流体版 4（对应装配线两套容器的数量上限）。 */
    int getMaxSlot();

    boolean isBound();

    /** 绑定的样板总成；未绑定、或总成已被拆掉时返回 null。 */

    /** 绑定的总成坐标 {@code {x,y,z}}；未绑定返回 null。 */
    int[] getBoundCoords();

    /** 界面上那行"当前提供什么"的文本（空手 / 正在提供某种材料）。 */
    String getProvidedText();
}
