package io.serverpanelbridge;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;
public final class ControlTests {
    static int passed;
    static void check(boolean value,String name) { if(!value) throw new AssertionError(name);passed++;System.out.println("PASS "+name); }
    interface Checked {void run() throws Exception;}
    static void fails(Checked test) throws Exception {try{test.run();}catch(Exception expected){return;}throw new AssertionError("Expected refusal");}
    static String command(String id,String action,String token,long expiry) {
        return "{\"id\":\""+id+"\",\"action\":\""+action+"\",\"leaseToken\":\""+token+"\",\"leaseExpiresAtUtc\":\""+Instant.ofEpochMilli(expiry)+"\"}";
    }
    public static void main(String[] args) throws Exception {
        fails(()->Json.parse("{\"id\":1,\"id\":2}")); fails(()->Json.parse("[1,]")); fails(()->Json.parse("true x"));
        check(((Map<?,?>)Json.parse("{\"escaped\":\"a\\n\\u0041\",\"extra\":null}")).get("escaped").equals("a\nA"),"strict JSON escapes and invalid input refusal");
        check(WindowsLaunch.parse("\"C:\\Java Path\\java.exe\" \"a b\" \"\" tail\\").equals(List.of("C:\\Java Path\\java.exe","a b","","tail\\")),"Windows arguments preserve spaces, empty values and trailing slashes");
        check(WindowsLaunch.parse("java.exe \"ab\\\"c\" \"\\\\\" d").equals(List.of("java.exe","ab\"c","\\","d")),"Windows arguments preserve escaped quotes and slashes");
        fails(()->WindowsLaunch.parse("java.exe \"unfinished"));
        check(RestartHelper.parent(ProcessHandle.current().pid(),Instant.EPOCH)==null,"restart rejects reused or mismatched parent identity");
        zombie.network.GameServer.server=true;
        zombie.network.GameServer.players=1;
        fails(()->new PzShutdownAdapter(false).requestStop());
        check(zombie.network.ServerMap.quitRequests==0,"occupied server stop refused");
        new PzShutdownAdapter(true).requestStop();
        check(zombie.network.ServerMap.quitRequests==1,"explicit occupied-server permission uses QueueQuit");
        zombie.network.GameServer.players=0;
        new PzShutdownAdapter(false).requestStop();
        check(zombie.network.ServerMap.quitRequests==2,"empty server graceful stop");
        zombie.network.GameServer.server=false;
        fails(()->new PzShutdownAdapter(true).requestStop());
        check(zombie.network.ServerMap.quitRequests==2,"client cannot request stop");
        HttpServer http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicLong now=new AtomicLong(System.currentTimeMillis());
        AtomicReference<String> commands=new AtomicReference<>("[]"),ack=new AtomicReference<>("");
        AtomicInteger posts=new AtomicInteger(),calls=new AtomicInteger(),stops=new AtomicInteger();
        AtomicLong bodyDelay=new AtomicLong();
        http.createContext("/", exchange->{
            if(exchange.getRequestMethod().equals("GET")) {
                calls.incrementAndGet(); byte[] body=commands.get().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200,body.length);
                if(bodyDelay.get()>0) {
                    exchange.getResponseBody().flush();
                    try{Thread.sleep(bodyDelay.get());}catch(InterruptedException error){Thread.currentThread().interrupt();}
                }
                exchange.getResponseBody().write(body);
            } else {ack.set(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));posts.incrementAndGet();exchange.sendResponseHeaders(204,-1);}
            exchange.close();
        });
        http.start();
        Path root=Files.createTempDirectory("bridge-control-");
        Logger log=Logger.getAnonymousLogger();log.setLevel(Level.OFF);
        Properties p=new Properties();p.setProperty("panelUrl","http://127.0.0.1:"+http.getAddress().getPort());p.setProperty("requestTimeoutSeconds","5");
        long future=now.get()+30000;
        try {
            try(CommandService service=new CommandService(new BridgeConfig(p,"test"),root.resolve("disabled"),stops::incrementAndGet,now::get,log)) {
                commands.set("["+command("disabled","stop","token0",future)+"]");service.pollOnce();service.onGameTick();
                check(stops.get()==0 && ack.get().contains("stopEnabled=false") && ack.get().contains("\"succeeded\":false"),"disabled stop acknowledged as failed");
            }
            p.setProperty("commandsEnabled","false");
            int before=calls.get();
            try(CommandService service=new CommandService(new BridgeConfig(p,"test"),root.resolve("no-poll"),stops::incrementAndGet,now::get,log)) {service.pollOnce();}
            check(calls.get()==before,"commandsEnabled=false makes no command request");
            p.setProperty("commandsEnabled","true");p.setProperty("stopEnabled","true");
            Path journal=root.resolve("enabled");
            try(CommandService service=new CommandService(new BridgeConfig(p,"test"),journal,stops::incrementAndGet,now::get,log)) {
                commands.set("["+command("first","stop","token1",future)+"]");service.pollOnce();
                check(stops.get()==0,"HTTP worker never executes game action");
                service.pollOnce();service.onGameTick();
                check(stops.get()==1,"duplicate delivery executes stop once on game tick");
                service.pollOnce();service.pollOnce();
                check(stops.get()==1 && ack.get().contains("\"leaseToken\":\"token1\"") && ack.get().contains("\"succeeded\":true"),"outcome acknowledged with lease token without replay");
            }
            try(CommandService service=new CommandService(new BridgeConfig(p,"test"),journal,stops::incrementAndGet,now::get,log)) {
                commands.set("["+command("first","stop","newToken",future)+"]");service.pollOnce();service.onGameTick();
                check(stops.get()==1 && ack.get().contains("newToken"),"restart journal prevents replay and uses new lease token");
                commands.set("["+command("start","start","s",future)+","+command("restart","restart","r",future)+"]");service.pollOnce();service.onGameTick();
                check(stops.get()==1 && ack.get().contains("restartEnabled=false"),"restart disabled by default and start rejected");
            }
            try(CommandService service=new CommandService(new BridgeConfig(p,"test"),root.resolve("expired"),stops::incrementAndGet,now::get,log)) {
                commands.set("["+command("expire","stop","x",future)+"]");service.pollOnce();now.set(future+1);service.onGameTick();service.pollOnce();
                check(stops.get()==1,"expired lease cannot stop server");
            }
            now.set(System.currentTimeMillis());
            Path uncertain=root.resolve("uncertain");
            try(CommandService service=new CommandService(new BridgeConfig(p,"test"),uncertain,stops::incrementAndGet,now::get,log)) {
                commands.set("["+command("reserved","stop","t",now.get()+30000)+"]");service.pollOnce();
            }
            try(CommandService service=new CommandService(new BridgeConfig(p,"test"),uncertain,stops::incrementAndGet,now::get,log)) {
                service.pollOnce();service.onGameTick();
                check(stops.get()==1 && ack.get().contains("uncertain outcome"),"interrupted reserved delivery refuses automatic replay");
            }
            Path blocked=root.resolve("blocked");Files.writeString(blocked,"not a directory");
            try(CommandService service=new CommandService(new BridgeConfig(p,"test"),blocked,stops::incrementAndGet,now::get,log)) {
                commands.set("["+command("blocked","stop","b",now.get()+30000)+"]");
                fails(service::pollOnce);service.onGameTick();check(stops.get()==1,"journal failure prevents game action");
            }
            try(CommandService service=new CommandService(new BridgeConfig(p,"test"),root.resolve("oversize"),stops::incrementAndGet,now::get,log)) {
                commands.set("[\""+"a".repeat(140000)+"\"]");
                fails(service::pollOnce);service.onGameTick();check(stops.get()==1,"oversized command body rejected before action");
            }
            p.setProperty("requestTimeoutSeconds","1");commands.set("[]");bodyDelay.set(1800);
            try(CommandService service=new CommandService(new BridgeConfig(p,"test"),root.resolve("timeout"),stops::incrementAndGet,now::get,log)) {
                long start=System.nanoTime();fails(service::pollOnce);
                check(System.nanoTime()-start<java.util.concurrent.TimeUnit.SECONDS.toNanos(3),"command body timeout is bounded after headers");
            }
            bodyDelay.set(0);p.setProperty("requestTimeoutSeconds","5");
            p.setProperty("restartEnabled","true");
            AtomicInteger prepared=new AtomicInteger(),armed=new AtomicInteger(),cancelled=new AtomicInteger();
            RestartAdapter restart=()-> {prepared.incrementAndGet();return new RestartAdapter.Prepared() {
                boolean active;
                public void arm(){active=true;armed.incrementAndGet();}
                public void close(){if(!active)cancelled.incrementAndGet();}
            };};
            int stopBefore=stops.get();
            Path restartJournal=root.resolve("restart-enabled");
            commands.set("["+command("restart-once","restart","rr",now.get()+30000)+"]");
            try(CommandService service=new CommandService(new BridgeConfig(p,"test"),restartJournal,stops::incrementAndGet,restart,now::get,log)) {
                service.pollOnce();service.pollOnce();
                check(prepared.get()==1 && armed.get()==0 && stops.get()==stopBefore,"restart prepared off game thread without premature shutdown");
                service.onGameTick();service.pollOnce();
                check(armed.get()==1 && stops.get()==stopBefore+1 && ack.get().contains("Restart armed"),"restart arms once after graceful stop with lease acknowledgement");
            }
            try(CommandService service=new CommandService(new BridgeConfig(p,"test"),restartJournal,stops::incrementAndGet,restart,now::get,log)) {
                service.pollOnce();service.onGameTick();
                check(prepared.get()==1 && armed.get()==1,"restart outcome prevents replay across JVM sessions");
            }
            try(CommandService service=new CommandService(new BridgeConfig(p,"test"),root.resolve("restart-refused"),()->{throw new IllegalStateException("Occupied");},restart,now::get,log)) {
                commands.set("["+command("refused","restart","rf",now.get()+30000)+"]");service.pollOnce();service.onGameTick();service.pollOnce();
                check(armed.get()==1 && cancelled.get()==1 && ack.get().contains("server was not stopped"),"player-policy refusal cancels prepared restart");
            }
            try(CommandService service=new CommandService(new BridgeConfig(p,"test"),root.resolve("restart-prepare-failed"),stops::incrementAndGet,()->{throw new java.io.IOException("secret launch parameter");},now::get,log)) {
                commands.set("["+command("prepare-failed","restart","pf",now.get()+30000)+"]");service.pollOnce();service.onGameTick();
                check(stops.get()==stopBefore+1 && ack.get().contains("preparation failed") && !ack.get().contains("secret"),"failed restart preparation keeps server running and redacts arguments");
            }
            try(CommandService service=new CommandService(new BridgeConfig(p,"test"),root.resolve("restart-expired"),stops::incrementAndGet,restart,now::get,log)) {
                long expires=now.get()+30000;commands.set("["+command("restart-expired","restart","re",expires)+"]");service.pollOnce();now.set(expires+1);service.onGameTick();
                check(armed.get()==1 && stops.get()==stopBefore+1 && cancelled.get()==2,"expired restart lease cancels helper without shutdown");
            }
            now.set(System.currentTimeMillis());
            try(CommandService service=new CommandService(new BridgeConfig(p,"test"),root.resolve("helper-dead"),stops::incrementAndGet,()->new RestartAdapter.Prepared(){
                public void validate(){throw new IllegalStateException("Helper exited");}
                public void arm(){throw new AssertionError("Must not arm dead helper");}
                public void close(){}
            },now::get,log)) {
                commands.set("["+command("dead-helper","restart","dh",now.get()+30000)+"]");service.pollOnce();service.onGameTick();service.pollOnce();
                check(stops.get()==stopBefore+1 && ack.get().contains("Helper exited"),"dead prepared helper refuses shutdown before QueueQuit");
            }
            Path armFailure=root.resolve("arm-failed");
            RestartAdapter broken=()->new RestartAdapter.Prepared(){public void arm(){throw new IllegalStateException("Pipe unavailable");}public void close(){}};
            try(CommandService service=new CommandService(new BridgeConfig(p,"test"),armFailure,stops::incrementAndGet,broken,now::get,log)) {
                commands.set("["+command("arm-failed","restart","af",now.get()+30000)+"]");service.pollOnce();service.onGameTick();service.pollOnce();
                check(stops.get()==stopBefore+2 && ack.get().contains("shutdown was already queued") && ack.get().contains("\"succeeded\":false"),"arming failure explicitly reports queued shutdown without claiming restart success");
            }
            String records=Files.readString(journal.resolve("first.properties"));
            check(!records.contains("token1") && !records.contains("newToken"),"journal contains no lease tokens");
        } finally {
            http.stop(0);
            try(var paths=Files.walk(root)) {for(Path path:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) Files.delete(path);}
        }
        System.out.println("RESULT: "+passed+" control assertions passed; no real server stopped");
    }
}
