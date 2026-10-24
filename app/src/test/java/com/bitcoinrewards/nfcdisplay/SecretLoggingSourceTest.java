package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import static org.junit.Assert.assertFalse;

public class SecretLoggingSourceTest {
    @Test public void productionHasNoSecretLoggingOrRawErrorPresentation() throws Exception {
        try(Stream<Path> paths=Files.walk(Paths.get("src/main/java"))){paths.filter(p->p.toString().endsWith(".java")).forEach(p->{try{String s=new String(Files.readAllBytes(p),StandardCharsets.UTF_8);assertFalse(p+" logs production data",s.contains("android.util.Log")||s.matches("(?s).*\\bLog\\.[a-z]+\\(.*"));assertFalse(p+" shows exception text",s.contains("getMessage()"));assertFalse(p+" shows raw response",s.contains("setText(response")||s.contains("setText(error"));}catch(Exception e){throw new RuntimeException(e);}});}
    }
}
