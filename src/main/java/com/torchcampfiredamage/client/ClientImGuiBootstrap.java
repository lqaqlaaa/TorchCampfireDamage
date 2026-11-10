package com.torchcampfiredamage.client;

import foundry.imgui.api.ImGuiMCEvents;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;

/**
 * Minimal ImGuiMC integration probe. Registered from the mod constructor,
 * as required by ImGuiMC's onImGuiLoad API.
 */
public final class ClientImGuiBootstrap {
    private static long preFrames;
    private static long postFrames;
    private static boolean firstWindowLogged;

    private ClientImGuiBootstrap() {}

    public static void register() {
        ClientDebugLog.info("Registering ImGuiMC lifecycle and frame callbacks from mod constructor.");
        try {
            ImGuiMCEvents.INSTANCE.onImGuiLoad(() ->
                    ClientDebugLog.info("ImGuiMC onImGuiLoad fired: ImGui handler initialized successfully."));
            ImGuiMCEvents.INSTANCE.preRenderImGuiEvent(() -> {
                preFrames++;
                if (preFrames == 1 || preFrames % 300 == 0) {
                    ClientDebugLog.info("ImGuiMC PRE frame callback received; count=" + preFrames);
                }
            });
            ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(() -> {
                postFrames++;
                if (postFrames == 1 || postFrames % 300 == 0) {
                    ClientDebugLog.info("ImGuiMC POST frame callback received; count=" + postFrames);
                }
                ImGuiSettingsScreen.renderMinimalWindow();
            });
            ClientDebugLog.info("ImGuiMC on-load, PRE and POST callbacks registered.");
        } catch (Throwable throwable) {
            ClientDebugLog.error("Failed to register ImGuiMC callbacks.", throwable);
        }
    }
}
