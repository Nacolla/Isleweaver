package com.thebeyond.isleweaver.compat;

import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.world.level.biome.Climate;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class PhantasmSamplerSeed {
    private static final MethodHandle GET_SEED;
    private static final MethodHandle SET_SEED;
    private static volatile boolean warned;

    static {
        MethodHandle get = null;
        MethodHandle set = null;
        try {
            Class<?> hooks = Class.forName("net.lyof.phantasm.world.gen.SamplerHooks", false, Climate.Sampler.class.getClassLoader());
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            get = lookup.findVirtual(hooks, "getSeed", MethodType.methodType(long.class))
                    .asType(MethodType.methodType(long.class, Object.class));
            set = lookup.findVirtual(hooks, "setSeed", MethodType.methodType(void.class, long.class))
                    .asType(MethodType.methodType(void.class, Object.class, long.class));
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        GET_SEED = get;
        SET_SEED = set;
    }

    private PhantasmSamplerSeed() {}

    public static void copy(Climate.Sampler from, Climate.Sampler to) {
        if (GET_SEED == null || from == null || to == null || from == to) return;
        try {
            SET_SEED.invokeExact((Object) to, (long) GET_SEED.invokeExact((Object) from));
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Isleweaver.LOGGER.warn("[Isleweaver] Could not carry the Phantasm sampler seed over to Blueprint's sampler", t);
            }
        }
    }
}
