package com.voxtune.ai;

import com.voxtune.model.CommandIntent;
import com.voxtune.model.MusicQuery;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline natural language command parser using regular expressions and heuristics.
 * Understands both spoken speech artifacts and typed natural commands.
 *
 * <p>The parser can be taught the actual content of the user's library via
 * {@link #setLibraryVocabulary(Collection, Collection)} so that any artist or
 * song title present on disk is recognized precisely, not just a hardcoded set.</p>
 */
public class CommandParser {

    private static final List<String> DEFAULT_ARTISTS = List.of(
        "Taylor Swift", "Imagine Dragons", "Adele", "The Weeknd", "Ed Sheeran",
        "Coldplay", "Billie Eilish", "Bruno Mars", "Dua Lipa", "Queen",
        "Nirvana", "Radiohead", "Oasis", "Linkin Park", "Green Day"
    );

    private static final List<String> DEFAULT_TITLES = List.of(
        "Love Story", "You Belong With Me", "Fifteen", "Blank Space", "Shake It Off",
        "Style", "Cruel Summer", "Cardigan", "Willow", "Anti Hero",
        "Radioactive", "Demons", "Believer", "Thunder", "Enemy", "Bones",
        "Rolling In The Deep", "Someone Like You", "Set Fire To The Rain", "Hello", "Easy On Me",
        "Can't Feel My Face", "Starboy", "Blinding Lights", "Save Your Tears",
        "Thinking Out Loud", "Shape Of You", "Perfect", "Bad Habits", "Shivers",
        "Yellow", "The Scientist", "Fix You", "Viva La Vida", "Higher Power",
        "Ocean Eyes", "Bad Guy", "Happier Than Ever", "Birds Of A Feather",
        "Just The Way You Are", "Grenade", "Locked Out Of Heaven", "Uptown Funk", "24K Magic",
        "New Rules", "Don't Start Now", "Levitating", "Dance The Night",
        "Bohemian Rhapsody", "Don't Stop Me Now", "Smells Like Teen Spirit", "Come As You Are",
        "Creep", "Wonderwall", "In The End", "Boulevard Of Broken Dreams"
    );

    private static final Set<String> KNOWN_GENRES = Set.of(
        "pop", "rock", "country", "indie", "folk", "soul", "rnb", "disco",
        "synthpop", "alternative", "grunge", "acoustic", "electro", "dance"
    );

    private static final Set<String> KNOWN_MOODS = Set.of(
        "relaxing", "chill", "calm", "energetic", "party", "happy", "sad", "peaceful"
    );

    /** Words that carry no search value and are stripped from free-text queries. */
    private static final Set<String> STOPWORDS = Set.of(
        "play", "plays", "playing", "song", "songs", "track", "tracks", "music",
        "some", "something", "anything", "by", "from", "the", "a", "an", "of", "in", "on",
        "at", "please", "me", "my", "for", "with", "to", "and", "all", "out", "that",
        "this", "year", "years", "album", "albums", "record", "records", "called", "named",
        "want", "hear", "listen", "put", "start", "give", "just", "now",
        "add", "mark", "remove", "delete", "unfavorite", "unfavourite",
        "favorite", "favorites", "favourite", "favourites", "current", "love", "it"
    );

    // Decade & year regex patterns
    private static final Pattern DECADE_PATTERN = Pattern.compile("\\b(19)?(80|90|00|10|20)s\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_RANGE_PATTERN = Pattern.compile("\\bfrom\\s+(\\d{4})\\s+to\\s+(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BETWEEN_YEARS_PATTERN = Pattern.compile("\\bbetween\\s+(\\d{4})\\s+and\\s+(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SINGLE_YEAR_PATTERN = Pattern.compile("\\b(?:from|in|of|year)\\s+(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BY_ARTIST_PATTERN = Pattern.compile("\\bby\\s+([a-z0-9\\s]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CALLED_PATTERN = Pattern.compile("\\b(?:called|named)\\s+([a-z0-9\\s]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FAVORITE_PATTERN = Pattern.compile("\\b(?:add|put|mark)\\b.*\\bfavou?rites?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FAVORITE_VERB_PATTERN = Pattern.compile("\\bfavou?rite\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNFAVORITE_PATTERN = Pattern.compile("\\bunfavou?rite\\b|\\b(?:remove|delete)\\b.*\\bfavou?rites?\\b", Pattern.CASE_INSENSITIVE);

    // Learned vocabulary (defaults plus everything discovered in the library)
    private final List<String> knownArtists = new ArrayList<>(DEFAULT_ARTISTS);
    private final List<String> knownTitles = new ArrayList<>(DEFAULT_TITLES);

    /**
     * Teaches the parser the artists and titles actually present in the user's library so that
     * commands naming any real track or artist are recognized exactly.
     */
    public void setLibraryVocabulary(Collection<String> artists, Collection<String> titles) {
        knownArtists.clear();
        knownArtists.addAll(DEFAULT_ARTISTS);
        if (artists != null) {
            knownArtists.addAll(artists);
        }

        knownTitles.clear();
        knownTitles.addAll(DEFAULT_TITLES);
        if (titles != null) {
            knownTitles.addAll(titles);
        }

        normalizeVocabulary(knownArtists);
        normalizeVocabulary(knownTitles);
    }

    private void normalizeVocabulary(List<String> phrases) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String p : phrases) {
            if (p != null && !p.isBlank()) {
                seen.add(p.trim());
            }
        }
        phrases.clear();
        phrases.addAll(seen);
        // Longest phrases first so "Billie Eilish" wins over a shorter partial match.
        phrases.sort((a, b) -> Integer.compare(b.length(), a.length()));
    }

    /**
     * Parses free-form user speech or text into a structured MusicQuery.
     */
    public MusicQuery parse(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return new MusicQuery(CommandIntent.UNKNOWN);
        }

        String input = rawInput.trim();
        // Normalize speech numbers and contractions
        String normalized = normalizeSpeech(input);

        // 1. Conversational greetings and queries
        if (isConversational(normalized)) {
            return new MusicQuery(CommandIntent.CONVERSATION, input);
        }

        // Favorite commands are handled with the same extraction pipeline so "add Hello to favorites"
        // can target a specific track, while "favorite this" targets whatever is playing.

        // 2. Direct playback control intents
        if (matchesAny(normalized, "pause", "pause the music", "pause music", "stop music", "hold on", "wait")) {
            return new MusicQuery(CommandIntent.PAUSE);
        }
        if (matchesAny(normalized, "resume", "continue", "continue playing", "unpause", "play again", "keep playing")) {
            return new MusicQuery(CommandIntent.RESUME);
        }
        if (matchesAny(normalized, "stop", "stop playing", "turn off", "end music")) {
            return new MusicQuery(CommandIntent.STOP);
        }
        if (matchesAny(normalized, "next", "next song", "next track", "skip", "skip song", "play next")) {
            return new MusicQuery(CommandIntent.NEXT);
        }
        if (matchesAny(normalized, "previous", "previous song", "previous track", "back", "go back", "last song")) {
            return new MusicQuery(CommandIntent.PREVIOUS);
        }
        if (matchesAny(normalized, "volume up", "louder", "turn it up", "increase volume", "higher volume")) {
            return new MusicQuery(CommandIntent.VOLUME_UP);
        }
        if (matchesAny(normalized, "volume down", "softer", "quieter", "turn it down", "decrease volume", "lower volume")) {
            return new MusicQuery(CommandIntent.VOLUME_DOWN);
        }

        // 3. Base intent: FAVORITE / UNFAVORITE / PLAY / SEARCH
        boolean verbFirst = normalized.startsWith("play ") || normalized.startsWith("search ")
                || normalized.startsWith("show ") || normalized.startsWith("find ")
                || normalized.startsWith("look for ");

        CommandIntent intent;
        if (!verbFirst && UNFAVORITE_PATTERN.matcher(normalized).find()) {
            intent = CommandIntent.UNFAVORITE;
        } else if (!verbFirst && (FAVORITE_PATTERN.matcher(normalized).find()
                || FAVORITE_VERB_PATTERN.matcher(normalized).find())) {
            intent = CommandIntent.FAVORITE;
        } else {
            intent = CommandIntent.PLAY;
            if (normalized.startsWith("search ") || normalized.startsWith("show ") ||
                normalized.startsWith("find ") || normalized.startsWith("look for ")) {
                intent = CommandIntent.SEARCH;
            }
        }

        // 4. Extract Year Range or Decade
        Integer[] years = extractYears(normalized);
        Integer fromYear = years[0];
        Integer toYear = years[1];

        // 5. Extract Artist (library vocabulary first, then a "by <artist>" phrase)
        String remainder = normalized;
        String detectedArtist = matchVocabulary(remainder, knownArtists);
        if (detectedArtist != null) {
            remainder = removePhrase(remainder, detectedArtist);
        } else {
            Matcher byMatcher = BY_ARTIST_PATTERN.matcher(remainder);
            if (byMatcher.find()) {
                String raw = stripTrailingTypeWords(byMatcher.group(1));
                if (!raw.isBlank()) {
                    detectedArtist = titleCase(raw);
                    remainder = removePhrase(remainder, raw);
                }
            }
        }

        // 6. Extract Song Title (from vocabulary, then an explicit "called/named" phrase)
        String detectedTitle = matchVocabulary(remainder, knownTitles);
        if (detectedTitle != null) {
            remainder = removePhrase(remainder, detectedTitle);
        } else {
            Matcher calledMatcher = CALLED_PATTERN.matcher(remainder);
            if (calledMatcher.find()) {
                String raw = stripTrailingTypeWords(calledMatcher.group(1));
                if (!raw.isBlank()) {
                    detectedTitle = titleCase(raw);
                    remainder = removePhrase(remainder, raw);
                }
            }
        }

        // 7. Extract Genre (from the remainder so title words like "Dance The Night" don't misfire)
        String detectedGenre = null;
        for (String g : KNOWN_GENRES) {
            if (matchesGenre(remainder, g)) {
                detectedGenre = genreLabel(g);
                remainder = removePhrase(remainder, g);
                break;
            }
        }

        // 8. Extract Mood
        String detectedMood = null;
        for (String m : KNOWN_MOODS) {
            if (containsWord(remainder, m)) {
                detectedMood = capitalize(m);
                remainder = removePhrase(remainder, m);
                break;
            }
        }

        // 9. Free-text fallback: prefer the exact title, otherwise meaningful leftover words
        String text = detectedTitle;
        if (text == null) {
            text = residualText(remainder);
        }

        return new MusicQuery(intent, detectedArtist, detectedGenre, fromYear, toYear, detectedMood, text, null);
    }

    private Integer[] extractYears(String normalized) {
        Matcher rangeMatcher = YEAR_RANGE_PATTERN.matcher(normalized);
        if (rangeMatcher.find()) {
            return new Integer[]{ Integer.parseInt(rangeMatcher.group(1)), Integer.parseInt(rangeMatcher.group(2)) };
        }

        Matcher betweenMatcher = BETWEEN_YEARS_PATTERN.matcher(normalized);
        if (betweenMatcher.find()) {
            return new Integer[]{ Integer.parseInt(betweenMatcher.group(1)), Integer.parseInt(betweenMatcher.group(2)) };
        }

        Matcher decadeMatcher = DECADE_PATTERN.matcher(normalized);
        if (decadeMatcher.find()) {
            return decadeRange(decadeMatcher.group(2));
        }

        for (String[] pair : new String[][]{
                {"2000s", "2000", "2009"}, {"two thousands", "2000", "2009"},
                {"90s", "1990", "1999"}, {"nineties", "1990", "1999"},
                {"2010s", "2010", "2019"}, {"twenty tens", "2010", "2019"},
                {"2020s", "2020", "2029"}, {"twenty twenties", "2020", "2029"},
                {"80s", "1980", "1989"}, {"eighties", "1980", "1989"}}) {
            if (normalized.contains(pair[0])) {
                return new Integer[]{ Integer.parseInt(pair[1]), Integer.parseInt(pair[2]) };
            }
        }

        Matcher singleYearMatcher = SINGLE_YEAR_PATTERN.matcher(normalized);
        if (singleYearMatcher.find()) {
            int y = Integer.parseInt(singleYearMatcher.group(1));
            return new Integer[]{ y, y };
        }

        return new Integer[]{ null, null };
    }

    private Integer[] decadeRange(String decade) {
        return switch (decade) {
            case "80" -> new Integer[]{ 1980, 1989 };
            case "90" -> new Integer[]{ 1990, 1999 };
            case "00" -> new Integer[]{ 2000, 2009 };
            case "10" -> new Integer[]{ 2010, 2019 };
            case "20" -> new Integer[]{ 2020, 2029 };
            default -> new Integer[]{ null, null };
        };
    }

    /** Matches a genre word only when it appears as a standalone word or in a "<genre> music/songs" phrase. */
    private boolean matchesGenre(String text, String genre) {
        return containsWord(text, genre) || text.contains(genre + " music") || text.contains(genre + " songs");
    }

    private boolean containsWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }

    private String matchVocabulary(String text, List<String> phrases) {
        for (String phrase : phrases) {
            String needle = normalizedForm(phrase);
            if (!needle.isEmpty() && containsWord(text, needle)) {
                return phrase;
            }
        }
        return null;
    }

    private String removePhrase(String text, String phrase) {
        String needle = normalizedForm(phrase);
        if (needle.isEmpty()) {
            return text;
        }
        return text.replaceAll("\\b" + Pattern.quote(needle) + "\\b", " ")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    private String stripTrailingTypeWords(String raw) {
        return raw.replaceAll("\\b(songs?|music|tracks?|albums?)\\b", " ")
                  .replaceAll("\\s+", " ")
                  .trim();
    }

    /**
     * Reduces leftover words to a meaningful search phrase, dropping fillers and numbers.
     */
    private String residualText(String remainder) {
        if (remainder == null || remainder.isBlank()) {
            return null;
        }
        String cleaned = remainder
                .replaceAll("\\b\\d{4}\\b", " ")
                .replaceAll("\\b(19|20)?\\d{2}s\\b", " ");

        StringBuilder sb = new StringBuilder();
        for (String token : cleaned.split("\\s+")) {
            if (!token.isBlank() && !STOPWORDS.contains(token)) {
                sb.append(token).append(' ');
            }
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private String genreLabel(String genre) {
        if (genre.equals("rnb")) {
            return "R&B";
        }
        return capitalize(genre);
    }

    private String normalizedForm(String phrase) {
        if (phrase == null) {
            return "";
        }
        return phrase.toLowerCase()
                .replaceAll("['’]", "")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String titleCase(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String word : raw.trim().split("\\s+")) {
            if (word.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private String normalizeSpeech(String input) {
        String lower = input.toLowerCase()
            .replaceAll("['’]", "")
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();

        // Convert spoken numbers / decades from speech recognition
        lower = lower.replaceAll("\\btwo thousands\\b", "2000s")
                     .replaceAll("\\btwo thousand\\b", "2000")
                     .replaceAll("\\bnineties\\b", "90s")
                     .replaceAll("\\beighties\\b", "80s")
                     .replaceAll("\\btwenty tens\\b", "2010s")
                     .replaceAll("\\btwenty twenties\\b", "2020s")
                     .replaceAll("\\btwenty ten\\b", "2010")
                     .replaceAll("\\btwenty fifteen\\b", "2015")
                     .replaceAll("\\btwenty twenty\\b", "2020");

        return lower;
    }

    private boolean isConversational(String text) {
        return text.equals("hi") || text.equals("hello") || text.equals("hey") ||
               text.contains("who are you") || text.contains("what can you do") ||
               text.contains("help") || text.contains("how are you");
    }

    private boolean matchesAny(String input, String... options) {
        for (String opt : options) {
            if (input.equals(opt) || input.startsWith(opt + " ")) {
                return true;
            }
        }
        return false;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
