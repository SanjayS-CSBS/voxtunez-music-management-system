package com.voxtune.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.CompletableFuture;

/**
 * Service generating conversational and situational responses for the user and SpeechService.
 * Demonstrates:
 * - Conversational dialogue management
 * - Contextual response templating
 * - Cloud AI conversational endpoint with offline rule-based fallback
 * - StringBuilder for efficient string composition
 */
public class AiResponseService {
    private static final String DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    private final String apiKey;
    private final String apiUrl;
    private final String model;

    public AiResponseService() {
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
     * Generates a conversational response to a user prompt asynchronously.
     */
    public CompletableFuture<String> getConversationalResponseAsync(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return CompletableFuture.completedFuture("I'm here! What would you like to listen to?");
        }

        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.completedFuture(getLocalConversationalResponse(userPrompt));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return callAiConversationalApi(userPrompt);
            } catch (Exception e) {
                return getLocalConversationalResponse(userPrompt);
            }
        });
    }

    /**
     * Generates an instant response for music playback actions.
     */
    public String getPlaybackResponse(MusicQuery query, List<Song> results) {
        if (results == null || results.isEmpty()) {
            if (query.hasArtist()) {
                return "No songs by " + query.getArtist() + " found in your local library.";
            }
            return "No matching songs found in your library.";
        }

        Song first = results.get(0);
        StringBuilder sb = new StringBuilder("Playing ");

        if (query.hasArtist() && query.hasYearFilter()) {
            sb.append(query.getArtist()).append(" songs from ");
            if (query.getFromYear() != null && query.getToYear() != null && !query.getFromYear().equals(query.getToYear())) {
                if (query.getFromYear() == 2000 && query.getToYear() == 2009) {
                    sb.append("the 2000s.");
                } else if (query.getFromYear() == 1990 && query.getToYear() == 1999) {
                    sb.append("the 90s.");
                } else {
                    sb.append(query.getFromYear()).append(" to ").append(query.getToYear()).append(".");
                }
            } else {
                sb.append(query.getFromYear()).append(".");
            }
        } else if (query.hasArtist()) {
            sb.append(query.getArtist()).append(" songs.");
        } else if (query.hasGenre()) {
            sb.append(query.getGenre()).append(" music.");
        } else if (query.hasYearFilter()) {
            sb.append("songs from ").append(query.getFromYear()).append(".");
        } else if (results.size() == 1) {
            sb.append(first.getTitle()).append(" by ").append(first.getArtist()).append(".");
        } else {
            sb.append(first.getTitle()).append(" and ").append(results.size() - 1).append(" more tracks.");
        }

        return sb.toString();
    }

    /**
     * Local offline rule-based conversational replies.
     */
    public String getLocalConversationalResponse(String input) {
        String lower = input.toLowerCase().trim();

        if (lower.equals("hi") || lower.equals("hello") || lower.equals("hey")) {
            return "Hi! How can I help you today?";
        }
        if (lower.contains("who are you")) {
            return "I'm VoxTune, your AI-powered music assistant.";
        }
        if (lower.contains("what can you do") || lower.contains("help")) {
            return "I can search your music library, play songs, control playback, and understand voice commands.";
        }
        if (lower.contains("how are you")) {
            return "I'm doing great and ready to play some great tunes for you!";
        }
        if (lower.contains("thank")) {
            return "You're welcome! Enjoy the music.";
        }

        return "I'm ready to play your music. Try saying: Play Taylor Swift songs from the 2000s.";
    }

    private String callAiConversationalApi(String prompt) throws Exception {
        String systemInstruction = "You are VoxTune, a friendly and concise music voice assistant. Give brief, natural answers (1-2 sentences max).";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.7);
        requestBody.put("messages", List.of(
            Map.of("role", "system", "content", systemInstruction),
            Map.of("role", "user", "content", prompt)
        ));

        String jsonPayload = mapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = mapper.readTree(response.body());
            return root.path("choices").get(0).path("message").path("content").asText().trim();
        } else {
            throw new RuntimeException("Conversational API returned HTTP " + response.statusCode());
        }
    }
}
