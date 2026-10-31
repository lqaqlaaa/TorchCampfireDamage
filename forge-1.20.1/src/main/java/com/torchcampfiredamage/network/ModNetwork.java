package com.torchcampfiredamage.network;

import com.torchcampfiredamage.TorchCampfireDamage;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络通道（Forge 1.20.1 用 SimpleChannel）。
 * <p>
 * 版本号变了要同步改 PROTOCOL_VERSION，否则客户端连不上服务端。
 */
public class ModNetwork {

    public static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TorchCampfireDamage.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextId = 0;

    public static void register() {
        // 方块参数包
        CHANNEL.registerMessage(nextId++,
                UpdateWardPayload.class,
                UpdateWardPayload::encode,
                UpdateWardPayload::decode,
                UpdateWardPayload::handle);

        // 星环设置包
        CHANNEL.registerMessage(nextId++,
                UpdateOrbitPayload.class,
                UpdateOrbitPayload::encode,
                UpdateOrbitPayload::decode,
                UpdateOrbitPayload::handle);
    }
}