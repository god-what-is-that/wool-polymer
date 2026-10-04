package boo.corvid.woolbackport;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import xyz.nucleoid.packettweaker.PacketContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * BlockItem whose client-side inventory icon is the block model (stairs/slab shape) supplied by the
 * resource pack. The client never knows the real {@code minecraft:<color>_wool_stairs} item, so we
 * hand it a base vanilla item plus an item-model override pointing at our pack asset — but only for
 * players who have the pack. Pack-less players get {@code fallback} (a generic vanilla stair/slab)
 * so the held/inventory item is never a broken "missing model" cube.
 */
public class WoolBlockItem extends BlockItem implements PolymerItem {
    private final Identifier model;
    private final Item fallback;

    public WoolBlockItem(Block block, Properties properties, Identifier model, Item fallback) {
        super(block, properties);
        this.model = model;
        this.fallback = fallback;
    }

    @Override
    public Item getPolymerItem(ItemStack stack, PacketContext context) {
        return fallback; // generic oak stairs/slab; the item-model override (below) recolors it
    }

    @Override
    public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
        // Only pack-having clients can resolve our custom model; others fall back to the base item.
        return PolymerResourcePackUtils.hasMainPack(context) ? this.model : null;
    }
}
