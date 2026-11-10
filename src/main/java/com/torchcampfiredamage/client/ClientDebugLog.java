package com.torchcampfiredamage.client;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Small diagnostic trace for the settings-screen startup/render pipeline.
 * Writes to .minecraft/logs/torchcampfiredamage-debug.log and latest.log.
 */
public final class ClientDebugLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("TorchCampfireDamage/ClientDebug");
    private static final Object LOCK = new Object();

    private ClientDebugLog() {}

    public static void info(String message) {
        String line = Instant.now() + " [TorchCampfireDamage] " + message;
        LOGGER.info(message);
        synchronized (LOCK) {
            try {
                Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
                Path logFile = gameDir.resolve("logs").resolve("torchcampfiredamage-debug.log");
                Files.createDirectories(logFile.getParent());
                Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            } catch (Exception exception) {
                LOGGER.warn("Unable to write dedicated debug log: {}", exception.toString());
            }
        }
    }

    public static void error(String message, Throwable throwable) {
        info(message + " | exception=" + throwable);
        LOGGER.error("Settings UI diagnostic failure", throwable);
        synchronized (LOCK) {
            try {
                Path logFile = Minecraft.getInstance().gameDirectory.toPath()
                        .resolve("logs").resolve("torchcampfiredamage-debug.log");
                Files.createDirectories(logFile.getParent());
                StringWriterSupport.appendStackTrace(logFile, throwable);
            } catch (Exception exception) {
                LOGGER.warn("Unable to append diagnostic stack trace: {}", exception.toString());
            }
        }
    }

    private static final class StringWriterSupport {
        private static void appendStackTrace(Path file, Throwable throwable) throws IOException {
            java.io.StringWriter buffer = new java.io.StringWriter();
            throwable.printStackTrace(new java.io.PrintWriter(buffer));
            Files.writeString(file, buffer + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        }
    }
}
