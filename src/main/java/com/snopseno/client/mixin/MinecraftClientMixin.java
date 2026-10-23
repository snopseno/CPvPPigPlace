package com.snopseno.client.mixin;

import com.snopseno.config.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow public GameOptions options;
    @Shadow private int itemUseCooldown;
    @Shadow private int attackCooldown;
    @Shadow public HitResult crosshairTarget;
    @Shadow public ClientPlayerEntity player;
    @Shadow public ClientWorld world;
    @Shadow public ClientPlayerInteractionManager interactionManager;

    @Shadow abstract boolean doAttack();

    private int rightCounter = 1000;
    private int leftCounter = 1000;
    private int tickAttackCounter = 1000;

    private BlockPos anchorPos = null;
    private int anchorTimeout = 0;
    private int anchorWait = -1;

    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void onHandleInputEvents(CallbackInfo ci) {
        ModConfig config = ModConfig.get();
        this.tickAnchorSwitch(config);

        boolean useHeld = this.options.useKey.isPressed();
        boolean attackHeld = this.options.attackKey.isPressed();

        if (this.isExcluded()) {
            this.rightCounter = 1000;
            this.leftCounter = 1000;
            this.tickAttackCounter = 1000;
            if (useHeld) {
                // не даём ванили повторять использование при зажатой ПКМ: один клик за нажатие
                this.itemUseCooldown = Math.max(this.itemUseCooldown, 1);
            }
            return;
        }

        this.tickAttack(config, attackHeld);

        int rightDelay = Math.max(1, config.fastRightClickDelay);
        if (this.shouldPreferCrystalUse()) {
            this.cancelMining();
            this.applyRightDelay(rightDelay);
        } else if (config.fastRightClick && useHeld) {
            this.applyRightDelay(rightDelay);
        } else {
            this.rightCounter = 1000;
        }

        int leftDelay = Math.max(1, config.fastLeftClickDelay);
        if (config.fastLeftClick && attackHeld && this.shouldFastLeftClick()) {
            this.leftCounter++;
            if (this.leftCounter >= leftDelay) {
                this.leftCounter = 0;
                this.attackCooldown = 0;
                if (this.crosshairTarget == null || this.crosshairTarget.getType() != HitResult.Type.BLOCK) {
                    this.doAttack();
                }
            }
        } else {
            this.leftCounter = 1000;
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void onHandleBlockBreaking(boolean breaking, CallbackInfo ci) {
        if (!this.shouldPreferCrystalUse()) return;
        this.cancelMining();
        ci.cancel();
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void onDoAttack(CallbackInfoReturnable<Boolean> cir) {
        if (!this.shouldPreferCrystalUse()) return;
        if (this.crosshairTarget instanceof BlockHitResult
                && this.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            this.cancelMining();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void onDoItemUse(CallbackInfo ci) {
        if (!ModConfig.get().anchorSwitch || this.player == null) return;
        if (!this.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) return;
        if (this.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            this.anchorPos = hit.getBlockPos().offset(hit.getSide());
            this.anchorTimeout = 10;
            this.anchorWait = -1;
        }
    }

    private void applyRightDelay(int delay) {
        this.rightCounter++;
        if (this.rightCounter >= delay) {
            this.rightCounter = 0;
            this.itemUseCooldown = 0;
        } else {
            this.itemUseCooldown = 1;
        }
    }

    private void tickAttack(ModConfig config, boolean attackHeld) {
        boolean active = config.tickAttack
                && attackHeld
                && this.isHoldingAxeOrMace()
                && this.crosshairTarget instanceof EntityHitResult;
        if (!active) {
            this.tickAttackCounter = 1000;
            return;
        }
        this.tickAttackCounter++;
        if (this.tickAttackCounter >= Math.max(1, config.tickAttackDelay)) {
            this.tickAttackCounter = 0;
            this.attackCooldown = 0;
            this.doAttack();
        }
    }

    private void tickAnchorSwitch(ModConfig config) {
        if (!config.anchorSwitch || this.player == null || this.world == null) {
            this.anchorPos = null;
            this.anchorWait = -1;
            return;
        }

        if (this.anchorWait > 0) {
            this.anchorWait--;
            if (this.anchorWait == 0) {
                this.switchToGlowstone();
                this.anchorWait = -1;
            }
            return;
        }

        if (this.anchorPos != null) {
            if (this.world.getBlockState(this.anchorPos).isOf(Blocks.RESPAWN_ANCHOR)) {
                this.anchorPos = null;
                int delay = Math.max(1, config.anchorSwitchDelay);
                if (delay == 1) {
                    this.switchToGlowstone();
                } else {
                    this.anchorWait = delay - 1;
                }
            } else if (--this.anchorTimeout <= 0) {
                this.anchorPos = null;
            }
        }
    }

    private void switchToGlowstone() {
        if (this.player == null) return;
        PlayerInventory inv = this.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(Items.GLOWSTONE)) {
                inv.setSelectedSlot(i);
                return;
            }
        }
    }

    private boolean shouldFastLeftClick() {
        return this.isHoldingEndCrystal() && this.isLookingAtObsidianOrCrystalOnObsidian();
    }

    private boolean isHoldingEndCrystal() {
        if (this.player == null) return false;
        return this.player.getMainHandStack().isOf(Items.END_CRYSTAL)
                || this.player.getOffHandStack().isOf(Items.END_CRYSTAL);
    }

    private boolean isHoldingAxeOrMace() {
        if (this.player == null) return false;
        for (Item item : new Item[]{
                this.player.getMainHandStack().getItem(),
                this.player.getOffHandStack().getItem()}) {
            String id = Registries.ITEM.getId(item).toString();
            if (id.endsWith("_axe") || id.equals("minecraft:mace")) return true;
        }
        return false;
    }

    private boolean isLookingAtObsidianOrCrystalOnObsidian() {
        if (this.world == null || this.crosshairTarget == null) return false;
        if (this.crosshairTarget instanceof BlockHitResult blockHit) {
            if (blockHit.getType() != HitResult.Type.BLOCK) return false;
            return this.isObsidian(blockHit.getBlockPos());
        }
        if (this.crosshairTarget instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            if (!(entity instanceof EndCrystalEntity)) return false;
            return this.isObsidian(entity.getBlockPos().down());
        }
        return false;
    }

    private boolean isObsidian(BlockPos pos) {
        return this.world.getBlockState(pos).isOf(Blocks.OBSIDIAN);
    }

    private boolean shouldPreferCrystalUse() {
        return !this.isExcluded() && this.options.useKey.isPressed() && this.isHoldingEndCrystal();
    }

    private void cancelMining() {
        if (this.interactionManager != null) {
            this.interactionManager.cancelBlockBreaking();
        }
    }

    private boolean isExcluded() {
        List<String> ex = ModConfig.get().excluded;
        if (ex == null || ex.isEmpty() || this.player == null) return false;

        for (Item item : new Item[]{
                this.player.getMainHandStack().getItem(),
                this.player.getOffHandStack().getItem()}) {
            if (ex.contains(Registries.ITEM.getId(item).toString())) return true;
        }
        if (this.world != null && this.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            var block = this.world.getBlockState(hit.getBlockPos()).getBlock();
            if (ex.contains(Registries.BLOCK.getId(block).toString())) return true;
        }
        if (this.crosshairTarget instanceof EntityHitResult entityHit) {
            var type = entityHit.getEntity().getType();
            if (ex.contains(Registries.ENTITY_TYPE.getId(type).toString())) return true;
        }
        return false;
    }
}