package com.snopseno;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod
implements ModInitializer {
    public static final String MOD_ID = "CpvpPigPlace";
    public static final Logger LOGGER = LoggerFactory.getLogger("cCpvpPigPlace");

    public void onInitialize() {
        LOGGER.info("CpvpPigPlace mod initialized!");
    }
}
