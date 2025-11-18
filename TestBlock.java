package local.midrian.redrev.customBlock;

import java.util.EnumMap;
import java.util.Map;

public class TestBlock extends AbstractPipeBlock {

    public TestBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected Map<LocalSide, SideAction> getSideActions() {

        Map<LocalSide, SideAction> map = new EnumMap<>(LocalSide.class);

        map.put(LocalSide.FRONT, SideAction.INPUT);
        map.put(LocalSide.BACK, SideAction.OUTPUT);
        map.put(LocalSide.LEFT, SideAction.OUTPUT);
        map.put(LocalSide.RIGHT, SideAction.OUTPUT);
        map.put(LocalSide.UP, SideAction.OUTPUT);
        map.put(LocalSide.DOWN, SideAction.OUTPUT);

        return map;
    }

}
