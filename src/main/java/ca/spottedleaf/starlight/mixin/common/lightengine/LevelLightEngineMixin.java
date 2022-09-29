package ca.spottedleaf.starlight.mixin.common.lightengine;

import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEngine;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEventListener;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLightEngine.class)
public abstract class LevelLightEngineMixin implements LightEventListener, StarLightLightingProvider {

    @Shadow
    @Nullable
    private LayerLightEngine<?, ?> blockEngine;

    @Shadow
    @Nullable
    private LayerLightEngine<?, ?> skyEngine;

    @Unique
    protected StarLightInterface lightEngine;

    @Override
    public final StarLightInterface getLightEngine() {
        return this.lightEngine;
    }

    /**
     *
     * TODO since this is a constructor inject, check on update for new constructors
     */
    @Inject(
            method = "<init>", at = @At("TAIL")
    )
    public void construct(final LightChunkGetter chunkProvider, final boolean hasBlockLight, final boolean hasSkyLight,
                          final CallbackInfo ci) {
        // avoid ClassCastException in cases where custom LightChunkGetters do not return a Level from getLevel()
        if (chunkProvider.getLevel() instanceof Level) {
            this.lightEngine = new StarLightInterface(chunkProvider, hasSkyLight, hasBlockLight, (LevelLightEngine)(Object)this);
        } else {
            this.lightEngine = new StarLightInterface(null, hasSkyLight, hasBlockLight, (LevelLightEngine)(Object)this);
        }
        // intentionally destroy mods hooking into old light engine state
        this.blockEngine = null;
        this.skyEngine = null;
    }

    /**
     * @reason Route to new light engine
     * @author Spottedleaf
     */
    @Overwrite
    public void checkBlock(final BlockPos pos) {
        this.lightEngine.blockChange(pos.immutable());
    }

    /**
     * @reason Avoid messing with vanilla light engine state
     * @author Spottedleaf
     */
    @Overwrite
    public void onBlockEmissionIncrease(final BlockPos pos, final int level) {
        // this light engine only reads levels from blocks, so this is a no-op
    }

    /**
     * @reason Route to new light engine
     * @author Spottedleaf
     */
    @Overwrite
    public boolean hasLightWork() {
        // route to new light engine
        return this.lightEngine.hasUpdates();
    }

    /**
     * @reason Hook into new light engine for light updates
     * @author Spottedleaf
     */
    @Overwrite
    public int runUpdates(final int maxUpdateCount, final boolean doSkylight, final boolean skipEdgeLightPropagation) {
        // replace impl
        final boolean hadUpdates = this.hasLightWork();
        this.lightEngine.propagateChanges();
        return hadUpdates ? 1 : 0;
    }

    /**
     * @reason New light engine hook for handling empty section changes
     * @author Spottedleaf
     */
    @Overwrite
    public void updateSectionStatus(final SectionPos pos, final boolean notReady) {
        this.lightEngine.sectionChange(pos, notReady);
    }

    /**
     * @reason Avoid messing with the vanilla light engine state
     * @author Spottedleaf
     */
    @Overwrite
    public void enableLightSources(final ChunkPos pos, final boolean lightEnabled) {

    }

    /**
     * @reason Replace light views with our own that hook into the new light engine instead of vanilla's
     * @author Spottedleaf
     */
    @Overwrite
    public LayerLightEventListener getLayerListener(final LightLayer lightType) {
        return lightType == LightLayer.BLOCK ? this.lightEngine.getBlockReader() : this.lightEngine.getSkyReader();
    }

    /**
     * @reason Avoid messing with the vanilla light engine state
     * @author Spottedleaf
     */
    @Overwrite
    public void queueSectionData(final LightLayer lightType, final SectionPos pos, @Nullable final DataLayer nibble,
                                 final boolean trustEdges) {}

    /**
     * @reason Avoid messing with the vanilla light engine state
     * @author Spottedleaf
     */
    @Overwrite
    public void retainData(final ChunkPos pos, final boolean retainData) {
        // not used by new light impl
    }

    /**
     * @reason Need to use our own hooks for retrieving light data
     * @author Spottedleaf
     */
    @Overwrite
    public int getRawBrightness(final BlockPos pos, final int ambientDarkness) {
        // need to use new light hooks for this
        return this.lightEngine.getRawBrightness(pos, ambientDarkness);
    }

    @Unique
    protected final Long2ObjectOpenHashMap<SWMRNibbleArray[]> blockLightMap = new Long2ObjectOpenHashMap<>();

    @Unique
    protected final Long2ObjectOpenHashMap<SWMRNibbleArray[]> skyLightMap = new Long2ObjectOpenHashMap<>();

    @Override
    public void clientUpdateLight(final LightLayer lightType, final SectionPos pos,
                                  final DataLayer nibble, final boolean trustEdges) {
        throw new IllegalStateException("This hook is for the CLIENT ONLY");
        // data storage changed with new light impl
    }

    @Override
    public void clientRemoveLightData(final ChunkPos chunkPos) {
        throw new IllegalStateException("This hook is for the CLIENT ONLY");
    }

    @Override
    public void clientChunkLoad(final ChunkPos pos, final LevelChunk chunk) {
        throw new IllegalStateException("This hook is for the CLIENT ONLY");
    }
}
