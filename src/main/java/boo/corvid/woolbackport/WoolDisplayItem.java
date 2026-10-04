package boo.corvid.woolbackport;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import xyz.nucleoid.packettweaker.PacketContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The item carried by a block's display entity. Polymer re-encodes it per viewer, so we branch on
 * that viewer's resource-pack status ({@link PolymerResourcePackUtils#hasMainPack}):
 *
 * <ul>
 *   <li><b>Pack present</b> → the colored wool model (from the stack's ITEM_MODEL component).</li>
 *   <li><b>Pack absent</b> → {@link #fallback}, a generic vanilla stair/slab, instead of the
 *       broken magenta "missing model" cube. Returning a null model makes the client render the
 *       base item's own model.</li>
 * </ul>
 *
 * Slabs pass {@code hideWithoutPack=true}: their invisible donor block is already the exact slab
 * shape (and visible to pack-less clients), so the display entity vanishes entirely rather than
 * doubling it. Stairs keep the display (their donor is only a slab), so pack-less clients still see
 * a stair shape.
 */
public final class WoolDisplayItem extends Item implements PolymerItem {
    private final Item fallback;
    private final boolean hideWithoutPack;

    public WoolDisplayItem(Properties properties, Item fallback, boolean hideWithoutPack) {
        super(properties);
        this.fallback = fallback;
        this.hideWithoutPack = hideWithoutPack;
    }

    @Override
    public Item getPolymerItem(ItemStack stack, PacketContext context) {
        if (hideWithoutPack && !PolymerResourcePackUtils.hasMainPack(context)) {
            return Items.AIR; // donor block already shows the exact slab; don't render a second one
        }
        return fallback;
    }

    @Override
    public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
        return PolymerResourcePackUtils.hasMainPack(context) ? stack.get(DataComponents.ITEM_MODEL) : null;
    }
}
