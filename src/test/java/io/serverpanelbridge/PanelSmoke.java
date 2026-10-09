package io.serverpanelbridge;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.nio.file.*;
public final class PanelSmoke {
    public static void main(String[] args) throws Exception {
        Properties p=new Properties();
        p.setProperty("panelUrl",args[0]);p.setProperty("hostId","pz-integration-test");p.setProperty("hostName","Dedicated integration test");
        p.setProperty("stopEnabled","true");p.setProperty("requestTimeoutSeconds","5");
        BridgeConfig config=new BridgeConfig(p,"test");
        Logger logger=Logger.getAnonymousLogger();
        try(PanelReporter reporter=new PanelReporter(config,new JvmMetrics(),()->System.nanoTime()/1_000_000,logger)){reporter.sendOnce();}
        AtomicInteger requested=new AtomicInteger();
        Path directory=Files.createTempDirectory("bridge-panel-command-");
        try(CommandService commands=new CommandService(config,directory,requested::incrementAndGet,System::currentTimeMillis,logger)){
            commands.pollOnce();commands.onGameTick();commands.pollOnce();
            if(requested.get()!=1) throw new AssertionError("Expected one simulated graceful stop");
        } finally {
            try(var paths=Files.walk(directory)){for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(path);}
        }
        System.out.println("PASS actual panel leases/acknowledgements; simulated game action only");
    }
}
