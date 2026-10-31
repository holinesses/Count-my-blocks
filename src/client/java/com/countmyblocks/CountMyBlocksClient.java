package com.countmyblocks;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public class CountMyBlocksClient implements ClientModInitializer {
    private static BlockPos first = null;
    private static BlockPos second = null;
    private static Item tool = Items.GOLDEN_AXE;

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("countmyblocks.properties");
    }

    private static void loadTool() {
        try {
            Path p = configPath();
            if (!Files.exists(p)) return;
            Properties props = new Properties();
            try (Reader r = Files.newBufferedReader(p)) {
                props.load(r);
            }
            Identifier id = Identifier.tryParse(props.getProperty("item", "minecraft:golden_axe"));
            if (id == null) return;
            Item found = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.GOLDEN_AXE);
            if (found != Items.AIR) tool = found;
        } catch (Exception ignored) {
            tool = Items.GOLDEN_AXE;
        }
    }

    private static void saveTool() {
        try {
            Properties props = new Properties();
            props.setProperty("item", BuiltInRegistries.ITEM.getKey(tool).toString());
            try (Writer w = Files.newBufferedWriter(configPath())) {
                props.store(w, "Count my blocks: the item that turns the counter on");
            }
        } catch (IOException ignored) {
        }
    }

    private static void setTool(Item item) {
        tool = item;
        first = null;
        second = null;
        saveTool();
    }

    private static String toolName() {
        return new ItemStack(tool).getHoverName().getString();
    }

    @Override
    public void onInitializeClient() {
        loadTool();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("countmyblocks")
                        .then(ClientCommandManager.literal("set").executes(ctx -> {
                            Item held = ctx.getSource().getPlayer().getMainHandItem().getItem();
                            if (held == Items.AIR) {
                                ctx.getSource().sendFeedback(Component.literal("Hold the item you want to use first, then run /countmyblocks set"));
                                return 0;
                            }
                            setTool(held);
                            ctx.getSource().sendFeedback(Component.literal("Count my blocks tool is now: " + toolName()));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("reset").executes(ctx -> {
                            setTool(Items.GOLDEN_AXE);
                            ctx.getSource().sendFeedback(Component.literal("Count my blocks tool reset to: " + toolName()));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("show").executes(ctx -> {
                            ctx.getSource().sendFeedback(Component.literal("Count my blocks tool: " + toolName()));
                            return Command.SINGLE_SUCCESS;
                        }))));

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide()) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!player.getItemInHand(hand).is(tool)) return InteractionResult.PASS;

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
            if (mc.player == null || !mc.player.getMainHandItem().is(tool)) {
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
                    if (!mc.player.getMainHandItem().is(tool)) return;

                    String text;
                    if (first == null) {
                        text = "Blocks: right-click a corner with the " + toolName();
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
