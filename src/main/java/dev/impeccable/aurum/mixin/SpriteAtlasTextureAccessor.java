package dev.impeccable.aurum.mixin;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Reason: the normal atlas baker needs every sprite's rectangle in the block atlas. Read only. */
@Mixin(SpriteAtlasTexture.class)
public interface SpriteAtlasTextureAccessor {
    @Accessor("sprites")
    Map<Identifier, Sprite> aurum$getSprites();
}
