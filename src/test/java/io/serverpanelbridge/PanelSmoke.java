package io.serverpanelbridge;
import java.util.Properties;
import java.util.logging.Logger;
public final class PanelSmoke {
    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        p.setProperty("panelUrl", args[0]);
        p.setProperty("hostId", "pz-integration-test");
        p.setProperty("hostName", "Dedicated integration test");
        try (PanelReporter reporter = new PanelReporter(new BridgeConfig(p, "test"), new JvmMetrics(),
                () -> System.nanoTime() / 1_000_000, Logger.getAnonymousLogger())) {
            reporter.sendOnce();
        }
    }
}
