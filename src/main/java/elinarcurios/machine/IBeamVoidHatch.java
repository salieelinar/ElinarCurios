package elinarcurios.machine;

/**
 * 两个"虚空仓"的公共标记接口，供存储器统计"绑了几个输入仓 / 输出仓"时反查。
 *
 * <p>
 * 存储器手里只有坐标，统计时会把坐标处的 MTE 拿出来判断：是不是我们的仓 + 它绑的是不是本存储。
 */
public interface IBeamVoidHatch {

    /** 这个仓当前绑的是不是给定的存储器（坐标 + 维度都比）。 */
    boolean isLinkedTo(MTEBeamVoidStorage storage);

    /** true = 束流虚空输入仓（装在多方块的出束位），false = 束流虚空输出仓（装在进束位）。 */
    boolean isVoidInputHatch();
}
