package io.serverpanelbridge;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Bundled Windows launcher prevents native game sockets reaching the helper. */
final class WindowsRestartLauncher {
    private WindowsRestartLauncher() {}
    static ProcessBuilder helper(Path logs,long pid,String executable,Path jar) throws Exception {
        byte[] bytes;
        try(InputStream resource=WindowsRestartLauncher.class.getResourceAsStream("/windows/RestartLauncher.exe")) {
            if(resource==null) throw new IOException("Bundled Windows restart launcher unavailable");
            bytes=resource.readAllBytes();
        }
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder name=new StringBuilder("restart-launcher-");for(byte b:hash) name.append(String.format(Locale.ROOT,"%02x",b&255));
        Path launcher=logs.resolve(name+".exe").toAbsolutePath();
        if(!Files.isRegularFile(launcher) || !MessageDigest.isEqual(hash,MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(launcher)))) {
            Path temporary=Files.createTempFile(logs,"restart-launcher-",".tmp");
            try {Files.write(temporary,bytes);Files.move(temporary,launcher,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            finally {Files.deleteIfExists(temporary);}
        }
        return new ProcessBuilder(launcher.toString(),Long.toString(pid),executable,jar.toString());
    }
}
