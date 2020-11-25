package xyz.nucleoid.leukocyte.shape;

import com.mojang.serialization.Codec;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.List;

public final class UnionShape implements ProtectionShape {
    public static final Codec<UnionShape> CODEC = ProtectionShape.CODEC.listOf().xmap(
            UnionShape::new,
            union -> Arrays.asList(union.scopes)
    );

    private final ProtectionShape[] scopes;

    public UnionShape(ProtectionShape... scopes) {
        this.scopes = scopes;
    }

    private UnionShape(List<ProtectionShape> scopes) {
        this(scopes.toArray(new ProtectionShape[0]));
    }

    @Override
    public boolean intersects(RegistryKey<World> dimension) {
        for (ProtectionShape scope : this.scopes) {
            if (scope.intersects(dimension)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean contains(RegistryKey<World> dimension, BlockPos pos) {
        for (ProtectionShape scope : this.scopes) {
            if (scope.contains(dimension, pos)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Codec<? extends ProtectionShape> getCodec() {
        return CODEC;
    }

    @Override
    public MutableText display() {
        MutableText text = new LiteralText("(");
        for (int i = 0; i < this.scopes.length; i++) {
            text = text.append(this.scopes[i].display());
            if (i <= this.scopes.length - 1) {
                text = text.append("U");
            }
        }
        return text.append(")");
    }

    @Override
    public ProtectionShape union(ProtectionShape other) {
        ProtectionShape[] scopes = new ProtectionShape[this.scopes.length + 1];
        System.arraycopy(this.scopes, 0, scopes, 0, this.scopes.length);
        scopes[scopes.length - 1] = other;
        return new UnionShape(scopes);
    }
}