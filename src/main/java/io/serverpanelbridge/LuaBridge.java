package io.serverpanelbridge;

public final class LuaBridge {
    private LuaBridge() { }
    public static boolean isClientOptionalReady() { return Main.isClientOptionalReady(); }
    public static void onTick() { Main.onTick(); }
}
