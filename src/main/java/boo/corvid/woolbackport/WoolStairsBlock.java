package boo.corvid.woolbackport;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import xyz.nucleoid.packettweaker.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A real wool/concrete stair. The client sees an invisible, stair-shaped donor state matching this
 * state's orientation, so collision and targeting line up with the real block: you walk up smoothly
 * and can click/place on the whole stair, including the top step. An item-display entity carries the
 * right shape model (straight/inner/outer), rotated to match the vanilla blockstate.
 */
public class WoolStairsBlock extends StairBlock
        implements PolymerTexturedBlock, BlockWithElementHolder, WoolBackport.DisplayProvider {
    private final Map<BlockState, BlockState> clientStates = new IdentityHashMap<>();
    private final BlockState sourceState;
    private final Identifier straightModel;
    private final Identifier innerModel;
    private final Identifier outerModel;

    /** {@code base} is the id stem, e.g. "white_wool" or "white_concrete". */
    public WoolStairsBlock(BlockState baseState, Properties properties, String base) {
        super(baseState, properties);
        this.sourceState = baseState; // the matching full block; drives break particles + sound
        this.straightModel = Identifier.fromNamespaceAndPath(WoolBackport.MOD_ID, base + "_stairs");
        this.innerModel = Identifier.fromNamespaceAndPath(WoolBackport.MOD_ID, base + "_stairs_inner");
        this.outerModel = Identifier.fromNamespaceAndPath(WoolBackport.MOD_ID, base + "_stairs_outer");

        // Client donor is the matching invisible STAIR state (facing/half/shape/waterlogged): the
        // client's collision and outline are the true stair shape, so walking up is accurate and the
        // whole stair — including the top step — is clickable to break or place against. These donors
        // are invisible (requestEmpty), so unlike a visible textured block they don't draw from
        // Polymer's tiny donor pool; we can hand out the exact orientation for every state. A stair
        // donor's full faces do cull neighbours, but the display entity renders the stair flush
        // over the cell, so there's no see-through void (that only happened in round 1, before the
        // display was covering it). The real stair shape stays authoritative server-side.
        for (BlockState state : getStateDefinition().getPossibleStates()) {
            clientStates.put(state, PolymerBlockResourceUtils.requestEmpty(
                    BlockModelType.getStairs(state.getValue(FACING), state.getValue(HALF), state.getValue(SHAPE),
                            state.getValue(WATERLOGGED))));
        }
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
        return clientStates.get(state);
    }

    /**
     * The block-break effect (particles + sound, LevelEvent 2001) is derived by the client from
     * whatever state we send here. Default Polymer would send the stair donor -> generic (copper)
     * sound; send the real source block so the client plays its break sound with right particles.
     */
    @Override
    public BlockState getPolymerBreakEventBlockState(BlockState state, @Nullable PacketContext context) {
        return sourceState;
    }

    @Override
    public @Nullable ItemStack displayStack(BlockState state) {
        StairsShape shape = state.getValue(SHAPE);
        Identifier model = switch (shape) {
            case STRAIGHT -> straightModel;
            case INNER_LEFT, INNER_RIGHT -> innerModel;
            case OUTER_LEFT, OUTER_RIGHT -> outerModel;
        };
        ItemStack stack = new ItemStack(WoolBackport.STAIRS_DISPLAY);
        stack.set(DataComponents.ITEM_MODEL, model);
        return stack;
    }

    @Override
    public Quaternionf displayRotation(BlockState state) {
        return stairRotation(state.getValue(FACING), state.getValue(HALF), state.getValue(SHAPE));
    }

    @Override
    public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
        return new WoolBackport.WoolDisplayHolder(this, initialBlockState);
    }

    /**
     * Rotation to orient the item-display model like a vanilla stair.
     *
     * Base rotation = the vanilla oak_stairs.json (x,y) for this state, converted
     * to MC's matrix
     * R_Y(-y)*R_X(-x) (== OctahedralGroup math; x,y unit-verified against all 40
     * blockstate variants).
     *
     * Item-displays render a block model rotated a global 180 about Y relative to
     * how a blockstate
     * renders it (observed in-game: stairs faced "away"; the offset hits every
     * facing equally, so it
     * is a single global flip, not a per-state error). We correct it by
     * post-multiplying R_Y(180):
     * R = R_Y(-y) * R_X(-x) * R_Y(180).
     */
    static Quaternionf stairRotation(Direction facing, Half half, StairsShape shape) {
        int base = switch (facing) {
            case NORTH -> 270;
            case EAST -> 0;
            case SOUTH -> 90;
            case WEST -> 180;
            default -> 0; // stairs are always horizontal
        };
        int x;
        int y;
        if (half == Half.BOTTOM) {
            x = 0;
            boolean left = shape == StairsShape.INNER_LEFT || shape == StairsShape.OUTER_LEFT;
            y = left ? (base + 270) % 360 : base;
        } else {
            x = 180;
            boolean right = shape == StairsShape.INNER_RIGHT || shape == StairsShape.OUTER_RIGHT;
            y = right ? (base + 90) % 360 : base;
        }
        return new Quaternionf()
                .rotateY((float) Math.toRadians(-y))
                .rotateX((float) Math.toRadians(-x))
                .rotateY((float) Math.PI); // global item-display vs blockstate 180 Y correction
    }
}
