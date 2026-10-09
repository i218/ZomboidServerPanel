package io.serverpanelbridge;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.io.*;
final class CommandJournal {
    private final Path directory;
    CommandJournal(Path directory) { this.directory=directory; }
    Properties read(String id) throws IOException {
        Path file=directory.resolve(id+".properties");
        if(!Files.exists(file)) return null;
        Properties values=new Properties();
        try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) { values.load(reader); }
        return values;
    }
    void write(String id, String state, boolean success, String message) throws IOException {
        Files.createDirectories(directory);
        if(!Files.exists(directory.resolve(id+".properties"))) {
            try(var files=Files.list(directory)) {
                if(files.filter(p->p.toString().endsWith(".properties")).limit(1000).count()>=1000)
                    throw new IOException("Command journal capacity reached");
            }
        }
        Properties values=new Properties();
        values.setProperty("state",state); values.setProperty("succeeded",Boolean.toString(success)); values.setProperty("result",message);
        StringWriter text=new StringWriter(); values.store(text,"ServerPanelBridge command outcome; no lease tokens");
        Path temp=Files.createTempFile(directory,"command-",".tmp");
        try {
            byte[] bytes=text.toString().getBytes(StandardCharsets.UTF_8);
            try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)) {
                ByteBuffer buffer=ByteBuffer.wrap(bytes);
                while(buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temp,directory.resolve(id+".properties"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
