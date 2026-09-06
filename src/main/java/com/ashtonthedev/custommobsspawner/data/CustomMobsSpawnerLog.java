package com.ashtonthedev.custommobsspawner.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CustomMobsSpawnerLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("cmobs");

    private CustomMobsSpawnerLog() {
    }

    public static void warn(String message) {
        LOGGER.warn(message);
    }

    public static void info(String message) {
        LOGGER.info(message);
    }
}
