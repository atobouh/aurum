package dev.impeccable.aurum.mixin;

import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reason: vanilla caches the bound GL program in a static and skips glUseProgram when it thinks its program is
 * still bound. After Aurum draws with its own program, the cache must be invalidated or vanilla's next draw
 * would run with Aurum's program.
 */
@Mixin(ShaderProgram.class)
public interface ShaderProgramAccessor {
    @Accessor("activeProgramGlRef")
    static void aurum$setActiveProgramGlRef(int ref) {
        throw new AssertionError();
    }
}
