package com.example.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ExampleModClient implements ClientModInitializer {

    static boolean enabled = false;
    static KeyMapping toggleKey;

    // فقط انکرهایی که خودم گذاشتم
    static final List<BlockPos> myAnchors = new ArrayList<>();
    static final Map<BlockPos, Integer> pending = new HashMap<>();
    static int cooldown = 0;
    static int restoreSlot = -1;

    static final double REACH = 4.5;
    static final int DELAY = 1;   // تأخیر بین کارها (تیک)

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.anchoraura.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("anchoraura", "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                enabled = !enabled;
                myAnchors.clear();
                pending.clear();
                restoreSlot = -1;
                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.literal("AnchorAura: " + (enabled ? "ON" : "OFF")), true);
                }
            }

            if (!enabled || client.player == null || client.level == null
                    || client.screen != null || client.gameMode == null) return;

            tick(client, client.player);
        });
    }

    static int findSlot(LocalPlayer me, boolean glowstone) {
        for (int i = 0; i < 9; i++) {
            boolean isGlow = me.getInventory().getItem(i).is(Items.GLOWSTONE);
            if (glowstone && isGlow) return i;
            if (!glowstone && !isGlow) return i;
        }
        return -1;
    }

    static void tick(Minecraft client, LocalPlayer me) {
        // ۱) انکری که دارم می‌ذارم رو ثبت کن
        if (client.options.keyUse.isDown()
                && me.getMainHandItem().is(Items.RESPAWN_ANCHOR)
                && client.hitResult != null
                && client.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult h = (BlockHitResult) client.hitResult;
            BlockPos target = client.level.getBlockState(h.getBlockPos()).canBeReplaced()
                    ? h.getBlockPos()
                    : h.getBlockPos().relative(h.getDirection());
            if (!myAnchors.contains(target)) pending.put(target, 10);
        }

        // ۲) اگه انکر واقعاً اومد، مال منه
        Iterator<Map.Entry<BlockPos, Integer>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Integer> e = it.next();
            BlockPos p = e.getKey();
            if (client.level.getBlockState(p).is(Blocks.RESPAWN_ANCHOR)) {
                if (!myAnchors.contains(p)) myAnchors.add(p);
                it.remove();
            } else {
                e.setValue(e.getValue() - 1);
                if (e.getValue() <= 0) it.remove();
            }
        }

        if (cooldown > 0) { cooldown--; return; }

        // ۳) فقط روی انکرهای خودم: شارژ و بعد انفجار
        for (BlockPos p : new ArrayList<>(myAnchors)) {
            BlockState s = client.level.getBlockState(p);
            if (!s.is(Blocks.RESPAWN_ANCHOR)) { myAnchors.remove(p); continue; }

            Vec3 center = Vec3.atCenterOf(p);
            if (me.getEyePosition().distanceTo(center) > REACH) continue;

            int charge = s.getValue(RespawnAnchorBlock.CHARGE);
            BlockHitResult hit = new BlockHitResult(center, Direction.UP, p, false);

            if (charge == 0) {
                int glow = findSlot(me, true);
                if (glow < 0) continue;
                restoreSlot = me.getInventory().getSelectedSlot();
                if (restoreSlot == glow) restoreSlot = findSlot(me, false);
                me.getInventory().setSelectedSlot(glow);
                client.gameMode.useItemOn(me, InteractionHand.MAIN_HAND, hit);
                me.swing(InteractionHand.MAIN_HAND);
            } else {
                int slot = restoreSlot >= 0 ? restoreSlot : findSlot(me, false);
                if (slot < 0) continue;
                me.getInventory().setSelectedSlot(slot);
                client.gameMode.useItemOn(me, InteractionHand.MAIN_HAND, hit);
                me.swing(InteractionHand.MAIN_HAND);
            }
            cooldown = DELAY;
            return;
        }
    }
					}
