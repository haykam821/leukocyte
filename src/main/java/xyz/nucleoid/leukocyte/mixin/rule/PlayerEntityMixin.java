package xyz.nucleoid.leukocyte.mixin.rule;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.leukocyte.Leukocyte;
import xyz.nucleoid.leukocyte.RuleQuery;
import xyz.nucleoid.leukocyte.rule.ProtectionRule;
import xyz.nucleoid.leukocyte.rule.RuleResult;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity {
    private PlayerEntityMixin(EntityType<? extends LivingEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void isInvulnerableTo(DamageSource source, CallbackInfoReturnable<Boolean> ci) {
        if (this.world.isClient || source != DamageSource.FALL) {
            return;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        Leukocyte leukocyte = Leukocyte.get(player.server);

        RuleQuery query = RuleQuery.forPlayer(player);
        RuleResult result = leukocyte.test(query, ProtectionRule.FALL_DAMAGE);
        if (result == RuleResult.ALLOW) {
            ci.setReturnValue(false);
        } else if (result == RuleResult.DENY) {
            ci.setReturnValue(true);
        }
    }

    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void dropSelectedItem(boolean dropEntireStack, CallbackInfoReturnable<Boolean> ci) {
        if (this.world.isClient) {
            return;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        Leukocyte leukocyte = Leukocyte.get(player.server);

        // TODO: duplication with plasmid event?
        RuleQuery query = RuleQuery.forPlayer(player);
        RuleResult result = leukocyte.test(query, ProtectionRule.THROW_ITEMS);
        if (result == RuleResult.DENY) {
            int slot = player.inventory.selectedSlot;
            ItemStack stack = player.inventory.getStack(slot);
            player.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(-2, slot, stack));
            ci.setReturnValue(false);
        }
    }
}
