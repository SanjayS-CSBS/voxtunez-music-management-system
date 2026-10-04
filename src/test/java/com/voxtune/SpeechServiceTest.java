package com.voxtune;

import com.voxtune.service.SpeechService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SpeechServiceTest {
    @Test
    public void testSpeechExecution() {
        SpeechService service = new SpeechService();
        assertDoesNotThrow(() -> {
            service.speakAsync("VoxTune audio verification");
            Thread.sleep(1000); // Allow thread to dispatch
        });
        service.shutdown();
    }
}
