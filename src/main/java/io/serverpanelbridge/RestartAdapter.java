package io.serverpanelbridge;
/** Prepare off the game thread; arm only after graceful shutdown was accepted. */
@FunctionalInterface
public interface RestartAdapter {
    Prepared prepare() throws Exception;
    interface Prepared extends AutoCloseable {
        default void validate() throws Exception {}
        void arm() throws Exception;
        @Override void close();
    }
}
