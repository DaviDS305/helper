package local.midrian.redrev.customBlock;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public abstract class AbstractPipeBlock extends Block {

    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");

    // Static lookup table: LocalSide → global Direction
    private static final Map<Direction, EnumMap<LocalSide, Direction>> LOCAL_TO_GLOBAL = new HashMap<>();

    static {
        // NORTH
        EnumMap<LocalSide, Direction> northMap = new EnumMap<>(LocalSide.class);
        northMap.put(LocalSide.FRONT, Direction.NORTH);
        northMap.put(LocalSide.BACK, Direction.SOUTH);
        northMap.put(LocalSide.LEFT, Direction.EAST);
        northMap.put(LocalSide.RIGHT, Direction.WEST);
        northMap.put(LocalSide.UP, Direction.UP);
        northMap.put(LocalSide.DOWN, Direction.DOWN);
        LOCAL_TO_GLOBAL.put(Direction.NORTH, northMap);

        // SOUTH
        EnumMap<LocalSide, Direction> southMap = new EnumMap<>(LocalSide.class);
        southMap.put(LocalSide.FRONT, Direction.SOUTH);
        southMap.put(LocalSide.BACK, Direction.NORTH);
        southMap.put(LocalSide.LEFT, Direction.WEST);
        southMap.put(LocalSide.RIGHT, Direction.EAST);
        southMap.put(LocalSide.UP, Direction.UP);
        southMap.put(LocalSide.DOWN, Direction.DOWN);
        LOCAL_TO_GLOBAL.put(Direction.SOUTH, southMap);

        // EAST
        EnumMap<LocalSide, Direction> eastMap = new EnumMap<>(LocalSide.class);
        eastMap.put(LocalSide.FRONT, Direction.EAST);
        eastMap.put(LocalSide.BACK, Direction.WEST);
        eastMap.put(LocalSide.LEFT, Direction.SOUTH);
        eastMap.put(LocalSide.RIGHT, Direction.NORTH);
        eastMap.put(LocalSide.UP, Direction.UP);
        eastMap.put(LocalSide.DOWN, Direction.DOWN);
        LOCAL_TO_GLOBAL.put(Direction.EAST, eastMap);

        // WEST
        EnumMap<LocalSide, Direction> westMap = new EnumMap<>(LocalSide.class);
        westMap.put(LocalSide.FRONT, Direction.WEST);
        westMap.put(LocalSide.BACK, Direction.EAST);
        westMap.put(LocalSide.LEFT, Direction.NORTH);
        westMap.put(LocalSide.RIGHT, Direction.SOUTH);
        westMap.put(LocalSide.UP, Direction.UP);
        westMap.put(LocalSide.DOWN, Direction.DOWN);
        LOCAL_TO_GLOBAL.put(Direction.WEST, westMap);
    }

    public AbstractPipeBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(POWERED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    // ============================================================
    // Detectar INPUT e disparar OUTPUT apenas na transição OFF → ON
    // ============================================================

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos,
                               Block sourceBlock, BlockPos fromPos, boolean notify) {
        if (world.isClient()) return;

        Map<LocalSide, SideAction> actions = getSideActions();

        boolean poweredAlready = state.get(POWERED);
        boolean anyInputPowered = false;

        // Verifica se alguma entrada está energizada
        for (LocalSide side : LocalSide.values()) {
            Direction globalDir = localToGlobal(side, state);
            int power = world.getEmittedRedstonePower(pos.offset(globalDir), globalDir.getOpposite());
            SideAction action = actions.getOrDefault(side, SideAction.OFF);

            if (power > 0 && action == SideAction.INPUT) {
                anyInputPowered = true;
                break;
            }
        }

        // Dispara OUTPUTS somente se entrada ativada e ainda não estava marcada como POWERED
        if (anyInputPowered && !poweredAlready) {
            for (Entry<LocalSide, SideAction> e : actions.entrySet()) {
                if (e.getValue() == SideAction.OUTPUT) {
                    sendMessage(world, "Output: " + e.getKey());
                }
            }
            world.setBlockState(pos, state.with(POWERED, true), Block.NOTIFY_NEIGHBORS | Block.NOTIFY_LISTENERS);
        }
        // Reset POWERED se nenhuma entrada energizada
        else if (!anyInputPowered && poweredAlready) {
            world.setBlockState(pos, state.with(POWERED, false), Block.NOTIFY_NEIGHBORS | Block.NOTIFY_LISTENERS);
        }
    }

    private void sendMessage(World world, String msg) {
        world.getPlayers().forEach(player ->
                player.sendMessage(net.minecraft.text.Text.literal(msg), false)
        );
    }

    // ============================================================
    // REDSTONE
    // ============================================================

    @Override
    public boolean emitsRedstonePower(BlockState state) {
        return state.get(POWERED); // Só emite quando POWERED
    }

    @Override
    public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        if (!state.get(POWERED)) return 0;

        // Converte direção global para LocalSide
        LocalSide side = globalToLocal(direction, state);
        SideAction action = getSideActions().getOrDefault(side, SideAction.OFF);

        return action == SideAction.OUTPUT ? 15 : 0; // OUTPUT emite potência máxima
    }

    @Override
    public int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return getWeakRedstonePower(state, world, pos, direction);
    }


    // ============================================================
    // Conversão Local <-> Global
    // ============================================================

    protected Direction localToGlobal(LocalSide local, BlockState state) {
        EnumMap<LocalSide, Direction> map = LOCAL_TO_GLOBAL.get(state.get(FACING));
        return map != null ? map.get(local) : Direction.NORTH;
    }

    protected LocalSide globalToLocal(Direction global, BlockState state) {
        EnumMap<LocalSide, Direction> map = LOCAL_TO_GLOBAL.get(state.get(FACING));
        if (map == null) return LocalSide.FRONT;
        for (Entry<LocalSide, Direction> e : map.entrySet()) {
            if (e.getValue() == global) return e.getKey();
        }
        return LocalSide.FRONT;
    }

    // ============================================================
    // MÉTODOS QUE A CLASSE FILHA DEVE IMPLEMENTAR
    // ============================================================

    protected abstract Map<LocalSide, SideAction> getSideActions();
}
