package io.serverpanelbridge;
@FunctionalInterface
public interface ShutdownAdapter { void requestStop() throws Exception; }
