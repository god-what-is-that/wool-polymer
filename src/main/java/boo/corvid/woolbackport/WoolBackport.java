package boo.corvid.woolbackport;

import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.soundpatcher.api.SoundPatcher;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Backports 26.3's wool and concrete stairs and slabs (all 16 colors each) to 26.2, server-side,
 * via Polymer.
 *
 * Blocks are registered under their native {@code minecraft:} ids, so world data is identical to
 * 26.3 and native takes over with zero migration once the server updates. The mod refuses to load
 * on 26.3 (fabric.mod.json depends "minecraft": ">=26.2 <26.3"); the registry guard below is a
 * second safety net against re-registering an id that already exists natively.
 *
 * Vanilla clients can't render 16 colors of stairs/slabs through Polymer's tiny donor-state pools
 * (~4 slab / ~3 stair colors max), so each placed block is shown via a per-block item-display
 * entity whose item-model is the block model (no pool limit). The real server-side StairBlock/
 * SlabBlock keeps correct collision; the client block is an invisible, shape-correct donor state.
 */
public class WoolBackport implements ModInitializer {
    public static final String MOD_ID = "woolbackport";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Vanilla dye colors, matching minecraft:&lt;color&gt;_wool / _concrete ids. */
    static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
    };

    /**
     * Block families backported here. Ids are {@code <color>_<material>_stairs} / {@code _slab},
     * and the source block each one copies is {@code <color>_<material>}.
     */
    static final String[] MATERIALS = {"wool", "concrete"};

    /** Identity rotation, shared (setLeftRotation copies it). */
    static final Quaternionf NO_ROTATION = new Quaternionf();

    /**
     * Items carried by the display entities. They render the colored wool model for pack-having
     * viewers and a generic vanilla stair/slab for pack-less ones (see {@link WoolDisplayItem}).
     * Shared across all 16 colors — the per-block model rides in the stack's ITEM_MODEL component.
     */
    static WoolDisplayItem STAIRS_DISPLAY;
    static WoolDisplayItem SLAB_DISPLAY;

    @Override
    public void onInitialize() {
        // Forward-compat, per family: skip any material that already exists natively. Wool landed in
        // 26.3-snapshot-2 and concrete in snapshot-7, so a dev build can have one and not the other.
        List<String> pending = new ArrayList<>();
        for (String material : MATERIALS) {
            if (!BuiltInRegistries.BLOCK.containsKey(
                    Identifier.withDefaultNamespace("white_" + material + "_stairs"))) {
                pending.add(material);
            }
        }
        if (pending.isEmpty()) {
            return; // everything exists natively (26.3+); nothing to polyfill
        }

        PolymerResourcePackUtils.markAsRequired();
        PolymerResourcePackUtils.addModAssets(MOD_ID);

        // Stairs keep their display without the pack (falling back to oak stairs); slabs hide it and
        // let the exact slab-shaped donor show through.
        STAIRS_DISPLAY = registerDisplayItem("stairs_display", Items.OAK_STAIRS, false);
        SLAB_DISPLAY = registerDisplayItem("slab_display", Items.OAK_SLAB, true);

        Set<SoundType> donorSounds = new HashSet<>();
        for (String material : pending) {
            for (String color : COLORS) {
                String base = color + "_" + material;
                Block source = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(base));
                // Inventory-item fallback is the matching FULL block: a vanilla client predicts
                // placement from the item it holds, reads that block's SoundType back, and plays it —
                // so holding a wool item makes the placer hear block.wool.place (no resource pack, no
                // sound-patcher). Pack-less clients then see a full-block icon; the world block still
                // falls back to a stair shape via STAIRS_DISPLAY/SLAB_DISPLAY.
                Block stairs = new WoolStairsBlock(source.defaultBlockState(), props(source, base + "_stairs"), base);
                Block slab = new WoolSlabBlock(props(source, base + "_slab"), source, base);
                register(base + "_stairs", stairs, source.asItem());
                register(base + "_slab", slab, source.asItem());
                collectDonorSounds(stairs, donorSounds);
                collectDonorSounds(slab, donorSounds);
            }
        }

        makeDonorStepSoundsServerAuthoritative(donorSounds);
    }

    /**
     * Step / mining-hit / fall are predicted client-side from the invisible donor block (waxed cut
     * copper stairs → metallic, petrified oak slab → stone), which no per-packet override can reach.
     * polymer-sound-patcher silences the donor's predicted sounds (empty entries in the resource pack)
     * and makes the server send our real sound instead. Only step/hit/fall need this; place/break
     * already sound right. Blast radius: every block of the donor's material becomes
     * server-authoritative for those three sounds (same sound, just server-driven).
     */
    private static void makeDonorStepSoundsServerAuthoritative(Set<SoundType> donorSounds) {
        for (SoundType donor : donorSounds) {
            SoundPatcher.convertIntoServerSound(donor.getStepSound());
            SoundPatcher.convertIntoServerSound(donor.getHitSound());
            SoundPatcher.convertIntoServerSound(donor.getFallSound());
            SoundPatcher.convertIntoServerSound(donor.getBreakSound());
            LOGGER.info("[{}] donor sound '{}' is now server-authoritative so our blocks step/hit/fall correctly",
                    MOD_ID, donor.getStepSound().location());
        }
    }

    /**
     * Adds the SoundTypes of a Polymer block's client donor states (what a vanilla client predicts).
     *
     * Skips donors that already sound like the real block — the client's prediction is correct there,
     * so there is nothing to silence and no reason to widen the blast radius. This is per-state, not
     * per-material: a concrete slab's donor (petrified oak slab) is already stone, while a WOOL slab's
     * donor is that same stone donor and very much does need converting.
     */
    private static void collectDonorSounds(Block block, Set<SoundType> out) {
        if (!(block instanceof PolymerTexturedBlock textured)) {
            return;
        }
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            BlockState donor = textured.getPolymerBlockState(state, null);
            if (donor != null && donor.getSoundType() != state.getSoundType()) {
                out.add(donor.getSoundType());
            }
        }
    }

    private static BlockBehaviour.Properties props(Block source, String path) {
        // ofFullCopy carries the source block's sound, hardness and tool requirement.
        return BlockBehaviour.Properties.ofFullCopy(source)
                .setId(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace(path)));
    }

    private static void register(String path, Block block, Item fallback) {
        Identifier id = Identifier.withDefaultNamespace(path);
        Registry.register(BuiltInRegistries.BLOCK, id, block);
        // useBlockDescriptionPrefix() makes the item's default name key "block.minecraft.<path>"
        // (matching our lang + native 26.3); without it a BlockItem defaults to "item.minecraft.<path>",
        // which our block.* translations never match — so the client shows the raw item.minecraft key.
        var itemProps = new Item.Properties()
                .useBlockDescriptionPrefix()
                .setId(ResourceKey.create(Registries.ITEM, id));
        Registry.register(BuiltInRegistries.ITEM, id,
                new WoolBlockItem(block, itemProps, Identifier.fromNamespaceAndPath(MOD_ID, path), fallback));
    }

    /** Registers a shared display-entity item under a woolbackport: id (never in a creative tab). */
    private static WoolDisplayItem registerDisplayItem(String path, Item fallback, boolean hideWithoutPack) {
        Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, path);
        var props = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id));
        return Registry.register(BuiltInRegistries.ITEM, id, new WoolDisplayItem(props, fallback, hideWithoutPack));
    }

    /** Implemented by the stairs/slab blocks to drive their display entity per block state. */
    public interface DisplayProvider {
        /** Item to display (its item-model is the block model), or null for no entity (e.g. double slab). */
        @Nullable ItemStack displayStack(BlockState state);

        /** Orientation for the display, matching the vanilla blockstate rotation. */
        Quaternionf displayRotation(BlockState state);
    }

    /**
     * One item-display entity per placed block, rendered flush in the cell (ItemDisplayContext.NONE,
     * holder auto-anchored at block center). Rebuilds itself on block-state changes (e.g. a stair's
     * corner shape changing when a neighbor is placed, or a slab becoming a double).
     * Lifecycle (create on chunk load, destroy on unload/break) is handled by Polymer.
     */
    public static class WoolDisplayHolder extends ElementHolder {
        private final DisplayProvider provider;
        private @Nullable ItemDisplayElement element;

        public WoolDisplayHolder(DisplayProvider provider, BlockState initialState) {
            this.provider = provider;
            refresh(initialState);
        }

        private void refresh(BlockState state) {
            ItemStack stack = provider.displayStack(state);
            if (stack == null) {
                if (element != null) {
                    removeElement(element);
                    element = null;
                }
                return;
            }
            if (element == null) {
                element = new ItemDisplayElement();
                // FIXED is the "placed block" context but bakes a 0.5 scale, so x2 -> flush 1x1x1.
                // (NONE does not sit flush for item displays.) Anchored at block center by the holder.
                element.setItemDisplayContext(ItemDisplayContext.FIXED);
                element.setScale(new Vector3f(2));
                element.setInvisible(true);
                element.setDisplaySize(1, 1);
                addElement(element);
            }
            element.setItem(stack);
            element.setLeftRotation(provider.displayRotation(state));
        }

        @Override
        public void notifyUpdate(HolderAttachment.UpdateType updateType) {
            super.notifyUpdate(updateType);
            if (updateType == BlockAwareAttachment.BLOCK_STATE_UPDATE) {
                var attachment = BlockAwareAttachment.get(this);
                if (attachment != null) {
                    refresh(attachment.getBlockState());
                }
            }
        }
    }
}
