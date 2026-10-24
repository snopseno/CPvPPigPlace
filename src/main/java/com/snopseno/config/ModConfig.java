package com.snopseno.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("cpvppigplace");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir();
    private static final Path PATH = DIR.resolve("cpvppigplace.json");
    private static final String[] LEGACY = {"CpvpPigPlace.json", "cpvpmouseplace.json"};

    private static ModConfig instance;

    public boolean fastRightClick = true;
    public boolean fastLeftClick = false;
    public int fastRightClickDelay = 1;
    public int fastLeftClickDelay = 1;
    public boolean tickAttack = false;
    public int tickAttackDelay = 20;
    public boolean anchorSwitch = false;
    public int anchorSwitchDelay = 1;
    public List<String> excluded = new ArrayList<>();

    public static synchronized ModConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static ModConfig load() {
        Path source = null;
        if (Files.exists(PATH)) {
            source = PATH;
        } else {
            for (String name : LEGACY) {
                Path p = DIR.resolve(name);
                if (Files.exists(p)) {
                    source = p;
                    break;
                }
            }
        }

        if (source != null) {
            try {
                String json = Files.readString(source, StandardCharsets.UTF_8);
                ModConfig cfg = GSON.fromJson(json, ModConfig.class);
                if (cfg != null) {
                    cfg.sanitize();
                    return cfg;
                }
                LOGGER.warn("Config {} is empty, using defaults", source);
                backupBroken(source);
            } catch (Exception e) {
                LOGGER.error("Could not read config {}, using defaults", source, e);
                backupBroken(source);
            }
        }

        ModConfig cfg = new ModConfig();
        cfg.save();
        return cfg;
    }

    private static void backupBroken(Path source) {
        try {
            Files.copy(source, DIR.resolve(source.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    private void sanitize() {
        if (fastRightClickDelay < 1) fastRightClickDelay = 1;
        if (fastLeftClickDelay < 1) fastLeftClickDelay = 1;
        if (tickAttackDelay < 1) tickAttackDelay = 1;
        if (anchorSwitchDelay < 1) anchorSwitchDelay = 1;
        if (excluded == null) excluded = new ArrayList<>();
    }

    public synchronized void save() {
        try {
            Path tmp = PATH.resolveSibling("cpvppigplace.json.tmp");
            Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Could not save config", e);
        }
    }
}