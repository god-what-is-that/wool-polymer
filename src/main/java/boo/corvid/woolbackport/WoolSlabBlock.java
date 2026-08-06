package boo.corvid.woolbackport;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A real wool/concrete slab. Double slabs are shown to the client as the plain full source block (it
 * already exists in 26.2 and looks identical); single slabs are an invisible, shape-correct donor
 * state on the client plus an item-display entity carrying the slab model.
 */
public class WoolSlabBlock extends SlabBlock implements PolymerTexturedBlock, BlockWithElementHolder, WoolBackport.DisplayProvider {
    private final Map<BlockState, BlockState> clientStates = new IdentityHashMap<>();
    private final BlockState sourceState;
    private final Identifier bottomModel;
    private final Identifier topModel;

    /** {@code base} is the id stem, e.g. "white_wool" or "white_concrete". */
    public WoolSlabBlock(Properties properties, Block sourceBlock, String base) {
        super(properties);
        this.bottomModel = Identifier.fromNamespaceAndPath(WoolBackport.MOD_ID, base + "_slab");
        this.topModel = Identifier.fromNamespaceAndPath(WoolBackport.MOD_ID, base + "_slab_top");

        BlockState source = sourceBlock.defaultBlockState();
        this.sourceState = source; // drives break particles + sound (e.g. block.wool.break)
        for (BlockState state : getStateDefinition().getPossibleStates()) {
            boolean wl = state.getValue(WATERLOGGED);
            BlockState client = switch (state.getValue(TYPE)) {
                case DOUBLE -> source; // full cube = a real vanilla block, no entity needed
                case BOTTOM -> PolymerBlockResourceUtils.requestEmpty(BlockModelType.getSlab(true, wl));
                case TOP -> PolymerBlockResourceUtils.requestEmpty(BlockModelType.getSlab(false, wl));
            };
            clientStates.put(state, client);
        }
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
        return clientStates.get(state);
    }

    /** Send the real source block for the break effect so the client plays its break sound. */
    @Override
    public BlockState getPolymerBreakEventBlockState(BlockState state, @Nullable PacketContext context) {
        return sourceState;
    }

    @Override
    public @Nullable ItemStack displayStack(BlockState state) {
        SlabType type = state.getValue(TYPE);
        if (type == SlabType.DOUBLE) {
            return null; // rendered as a real full vanilla block
        }
        ItemStack stack = new ItemStack(WoolBackport.SLAB_DISPLAY);
        stack.set(DataComponents.ITEM_MODEL, type == SlabType.TOP ? topModel : bottomModel);
        return stack;
    }

    @Override
    public Quaternionf displayRotation(BlockState state) {
        return WoolBackport.NO_ROTATION; // slab/slab_top models are pre-shaped
    }

    @Override
    public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
        if (initialBlockState.getValue(TYPE) == SlabType.DOUBLE) {
            return null;
        }
        return new WoolBackport.WoolDisplayHolder(this, initialBlockState);
    }
}
