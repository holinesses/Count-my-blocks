package com.countmyblocks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Locale;

public class CountMyBlocksClient implements ClientModInitializer {
    private static BlockPos first = null;
    private static BlockPos second = null;

    @Override
    public void onInitializeClient() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide()) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!player.getItemInHand(hand).is(Items.GOLDEN_AXE)) return InteractionResult.PASS;

            BlockPos pos = hit.getBlockPos();
            if (first == null || second != null) {
                first = pos;
                second = null;
            } else {
                second = pos;
            }
            return InteractionResult.SUCCESS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) {
                first = null;
                second = null;
                return;
            }
            if (!mc.player.getMainHandItem().is(Items.GOLDEN_AXE)) {
                first = null;
                second = null;
            }
        });

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.HOTBAR,
                Identifier.fromNamespaceAndPath("countmyblocks", "counter"),
                (graphics, tickCounter) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null || mc.options.hideGui) return;

                    boolean holdingAxe = mc.player.getMainHandItem().is(Items.GOLDEN_AXE);
                    if (!holdingAxe) return;

                    String text;
                    if (first == null) {
                        text = "Blocks: right-click a corner with the golden axe";
                    } else {
                        BlockPos end = second;
                        boolean preview = false;
                        if (end == null) {
                            HitResult hr = mc.hitResult;
                            if (hr instanceof BlockHitResult bhr && hr.getType() == HitResult.Type.BLOCK) {
                                end = bhr.getBlockPos();
                                preview = true;
                            }
                        }
                        if (end == null) {
                            text = "Blocks: now look at the end block and right-click";
                        } else {
                            long dx = Math.abs(first.getX() - end.getX()) + 1L;
                            long dy = Math.abs(first.getY() - end.getY()) + 1L;
                            long dz = Math.abs(first.getZ() - end.getZ()) + 1L;
                            long total = dx * dy * dz;
                            text = String.format(Locale.US, "Blocks: %,d  (%d x %d x %d)  =  %d stacks + %d",
                                    total, dx, dy, dz, total / 64, total % 64);
                            if (preview) text += "  [preview]";
                        }
                    }

                    int x = graphics.guiWidth() / 2;
                    int y = graphics.guiHeight() - 72;
                    graphics.drawCenteredString(mc.font, Component.literal(text), x, y, 0xFFFFFFFF);
                });
    }
}
