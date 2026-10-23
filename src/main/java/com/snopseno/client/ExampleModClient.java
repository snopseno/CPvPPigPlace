package com.snopseno.client;

import com.snopseno.config.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public class ExampleModClient
implements ClientModInitializer {
    public void onInitializeClient() {
        ModConfig.get();
    }
}
