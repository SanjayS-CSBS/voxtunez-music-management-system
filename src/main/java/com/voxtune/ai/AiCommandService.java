package com.voxtune.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voxtune.db.SongRepository;
import com.voxtune.model.CommandIntent;
import com.voxtune.model.MusicQuery;
import com.voxtune.model.Song;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

/**
 * Service orchestrating command parsing with optional AI cloud integration and local fallback.
 * Demonstrates:
 * - Java 11/17 HttpClient for asynchronous HTTP requests
 * - Jackson ObjectMapper for JSON serialization and deserialization
 * - CompletableFuture for non-blocking asynchronous pipeline
 * - Graceful Fallback Pattern (Cloud AI -> Local Regex NLP)
 * - Environment variable configuration without hardcoding secrets
 */
public class AiCommandService {
    private static final String DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final CommandParser localParser;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    private final String apiKey;
    private final String apiUrl;
    private final String model;

    public AiCommandService() {
        this.localParser = new CommandParser();
        this.mapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();

        this.apiKey = System.getenv("VOXTUNE_AI_KEY");
        String envUrl = System.getenv("VOXTUNE_AI_URL");
        this.apiUrl = (envUrl != null && !envUrl.isBlank()) ? envUrl : DEFAULT_API_URL;
        String envModel = System.getenv("VOXTUNE_AI_MODEL");
        this.model = (envModel != null && !envModel.isBlank()) ? envModel : DEFAULT_MODEL;
    }

    /**
     * Asynchronously parses user speech or command string.
     * Uses cloud AI if configured, otherwise falls back immediately to local CommandParser.
     */
    public CompletableFuture<MusicQuery> parseCommandAsync(String input) {
        if (input == null || input.isBlank()) {
            return CompletableFuture.completedFuture(new MusicQuery(CommandIntent.UNKNOWN));
        }

        if (apiKey == null || apiKey.isBlank()) {
            // Local offline mode
            return CompletableFuture.completedFuture(localParser.parse(input));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return callAiApi(input);
            } catch (Exception e) {
                System.err.println("AI service call failed (" + e.getMessage() + "). Falling back to local parser.");
                return localParser.parse(input);
            }
        });
    }

    private MusicQuery callAiApi(String prompt) throws Exception {
        String systemInstruction = """
            You are a music command parser for VoxTune. Return a JSON object with:
            {
              "intent": "PLAY"|"PAUSE"|"RESUME"|"STOP"|"NEXT"|"PREVIOUS"|"VOLUME_UP"|"VOLUME_DOWN"|"SEARCH"|"CONVERSATION",
              "artist": string or null,
              "genre": string or null,
              "fromYear": integer or null,
              "toYear": integer or null,
              "mood": string or null,
              "text": string or null,
              "limit": integer or null
            }
            Extract decades like '2000s' as fromYear: 2000, toYear: 2009.
            Return ONLY raw JSON, no markdown backticks.
            """;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.0);
        requestBody.put("messages", List.of(
            Map.of("role", "system", "content", systemInstruction),
            Map.of("role", "user", "content", prompt)
        ));

        String jsonPayload = mapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(6))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = mapper.readTree(response.body());
            String content = root.path("choices").get(0).path("message").path("content").asText().trim();
            // Clean markdown code fence if AI added it
            content = content.replaceAll("^```json\\s*", "").replaceAll("^```\\s*", "").replaceAll("\\s*```$", "").trim();
            return mapper.readValue(content, MusicQuery.class);
        } else {
            throw new RuntimeException("API returned HTTP " + response.statusCode());
        }
    }

    public CommandParser getLocalParser() {
        return localParser;
    }

    /**
     * Loads every artist and title from the local library into the offline parser so commands
     * that name a real track or artist are recognized precisely.
     */
    public void refreshLibraryVocabulary(SongRepository repository) {
        if (repository == null) {
            return;
        }
        try {
            Set<String> artists = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            Set<String> titles = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (Song song : repository.findAll()) {
                if (song.getArtist() != null && !song.getArtist().isBlank()) {
                    artists.add(song.getArtist());
                }
                if (song.getTitle() != null && !song.getTitle().isBlank()) {
                    titles.add(song.getTitle());
                }
            }
            localParser.setLibraryVocabulary(artists, titles);
        } catch (Exception e) {
            System.err.println("Could not refresh command vocabulary: " + e.getMessage());
        }
    }
}
