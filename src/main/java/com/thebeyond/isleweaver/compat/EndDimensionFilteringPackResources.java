package com.thebeyond.isleweaver.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

// hides the End dimension/dimension_type jsons from foreign packs so Beyond's pack wins those slots; everything else passes through
public class EndDimensionFilteringPackResources implements PackResources {

    private static final ResourceLocation END_DIMENSION = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "dimension/the_end.json");
    private static final ResourceLocation END_DIMENSION_TYPE = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "dimension_type/the_end.json");

    private static final Set<ResourceLocation> HIDDEN = Set.of(END_DIMENSION, END_DIMENSION_TYPE);

    private final PackResources delegate;

    public EndDimensionFilteringPackResources(PackResources delegate) {
        this.delegate = delegate;
    }

    @Override
    @Nullable
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type == PackType.SERVER_DATA && HIDDEN.contains(location)) {
            return null;
        }
        return delegate.getResource(type, location);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type == PackType.SERVER_DATA && "minecraft".equals(namespace)) {
            // drop only the exact hidden ids; the rest of the minecraft namespace still passes through
            delegate.listResources(type, namespace, path, (loc, supplier) -> {
                if (!HIDDEN.contains(loc)) {
                    output.accept(loc, supplier);
                }
            });
            return;
        }
        delegate.listResources(type, namespace, path, output);
    }

    @Override
    @Nullable
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return delegate.getRootResource(elements);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return delegate.getNamespaces(type);
    }

    @Override
    @Nullable
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) throws IOException {
        return delegate.getMetadataSection(deserializer);
    }

    @Override
    public PackLocationInfo location() {
        return delegate.location();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
