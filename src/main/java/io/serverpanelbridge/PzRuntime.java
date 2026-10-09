package io.serverpanelbridge;

import java.nio.file.*;

public final class PzRuntime {
    private PzRuntime() { }
    public static boolean isDedicatedServer() throws ReflectiveOperationException {
        return Class.forName("zombie.network.GameServer").getField("server").getBoolean(null);
    }
    public static String serverName() throws ReflectiveOperationException {
        String value = (String) Class.forName("zombie.network.GameServer").getField("serverName").get(null);
        if (value == null || value.isBlank() || !value.matches("[A-Za-z0-9_.-]+") || value.equals(".") || value.equals(".."))
            throw new IllegalStateException("Running server name is unavailable or invalid");
        return value;
    }
    public static Path cacheDirectory() throws ReflectiveOperationException {
        String override = System.getProperty("serverpanelbridge.cachedir");
        if (override != null) return Paths.get(override).toAbsolutePath();
        Class<?> type = Class.forName("zombie.ZomboidFileSystem");
        Object instance = type.getField("instance").get(null);
        return Paths.get((String) type.getMethod("getCacheDir").invoke(instance)).toAbsolutePath();
    }
}
