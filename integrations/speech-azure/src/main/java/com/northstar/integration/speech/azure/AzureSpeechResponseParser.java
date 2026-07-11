package com.northstar.integration.speech.azure;

import com.northstar.core.study.PhonemeScore;
import com.northstar.core.study.PronunciationResult;
import com.northstar.core.study.SpeechAssessmentException;
import com.northstar.core.study.SpokenAnswerResult;
import com.northstar.core.study.WordScore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

final class AzureSpeechResponseParser {

    private final ObjectMapper json;

    AzureSpeechResponseParser(ObjectMapper json) {
        this.json = json;
    }

    PronunciationResult reading(List<String> responses) {
        List<Segment> segments = segments(responses);
        Segment segment = segments.getFirst();
        return new PronunciationResult(
                required(segment.accuracy(), "accuracy"),
                required(segment.fluency(), "fluency"),
                segment.prosody(),
                segment.transcript(),
                segment.words().stream().map(Word::score).toList());
    }

    SpokenAnswerResult spoken(List<String> responses) {
        List<Segment> segments = segments(responses);
        List<Word> words = segments.stream().flatMap(segment -> segment.words().stream()).toList();
        List<Double> accuracy = words.stream()
                .filter(word -> !"Insertion".equalsIgnoreCase(word.errorType()))
                .map(Word::accuracy).toList();
        double paragraphAccuracy = average(accuracy, "accuracy");
        List<Double> prosodyValues = segments.stream().map(Segment::prosody).filter(value -> value != null).toList();
        Double paragraphProsody = prosodyValues.isEmpty() ? null : average(prosodyValues, "prosody");
        double paragraphFluency = fluency(words, segments);
        String transcript = segments.stream().map(Segment::transcript)
                .filter(value -> !value.isBlank()).reduce((left, right) -> left + " " + right).orElse("");
        return new SpokenAnswerResult(transcript, paragraphAccuracy, paragraphFluency,
                paragraphProsody, words.stream().map(Word::score).toList());
    }

    private List<Segment> segments(List<String> responses) {
        if (responses == null || responses.isEmpty()) throw invalid("Azure Speech returned no result");
        List<Segment> parsed = new ArrayList<>();
        for (String response : responses) {
            try {
                Map<?, ?> root = json.readValue(response, Map.class);
                String status = string(root.get("RecognitionStatus"));
                if (!status.isBlank() && !"Success".equals(status)) continue;
                Map<?, ?> nbest = firstMap(root.get("NBest"));
                if (nbest.isEmpty()) continue;
                Map<?, ?> assessment = map(nbest.get("PronunciationAssessment"));
                List<Word> words = words(nbest.get("Words"));
                parsed.add(new Segment(
                        firstString(nbest.get("Display"), nbest.get("Lexical"), root.get("DisplayText")),
                        number(assessment.get("AccuracyScore"), nbest.get("AccuracyScore")),
                        number(assessment.get("FluencyScore"), nbest.get("FluencyScore")),
                        number(assessment.get("ProsodyScore"), nbest.get("ProsodyScore")),
                        words));
            } catch (SpeechAssessmentException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new SpeechAssessmentException(SpeechAssessmentException.Failure.PROVIDER,
                        "Azure Speech returned invalid JSON", exception);
            }
        }
        if (parsed.isEmpty()) throw invalid("Azure Speech did not recognize speech");
        return List.copyOf(parsed);
    }

    private static List<Word> words(Object raw) {
        if (!(raw instanceof List<?> values)) return List.of();
        List<Word> result = new ArrayList<>();
        for (Object value : values) {
            Map<?, ?> word = map(value);
            Map<?, ?> assessment = map(word.get("PronunciationAssessment"));
            Double accuracy = number(assessment.get("AccuracyScore"), word.get("AccuracyScore"));
            String error = firstString(assessment.get("ErrorType"), word.get("ErrorType"));
            result.add(new Word(
                    string(word.get("Word")),
                    accuracy == null ? 0 : accuracy,
                    error.isBlank() ? "None" : error,
                    longNumber(word.get("Offset")),
                    longNumber(word.get("Duration")),
                    phonemes(word.get("Phonemes"))));
        }
        return List.copyOf(result);
    }

    private static List<PhonemeScore> phonemes(Object raw) {
        if (!(raw instanceof List<?> values)) return List.of();
        List<PhonemeScore> result = new ArrayList<>();
        for (Object value : values) {
            Map<?, ?> phoneme = map(value);
            Map<?, ?> assessment = map(phoneme.get("PronunciationAssessment"));
            Double accuracy = number(assessment.get("AccuracyScore"), phoneme.get("AccuracyScore"));
            result.add(new PhonemeScore(string(phoneme.get("Phoneme")), accuracy == null ? 0 : accuracy));
        }
        return List.copyOf(result);
    }

    private static double fluency(List<Word> words, List<Segment> segments) {
        List<Word> timed = words.stream().filter(word -> word.offset() != null && word.duration() != null).toList();
        if (!timed.isEmpty()) {
            long start = timed.stream().mapToLong(Word::offset).min().orElse(0);
            long end = timed.stream().mapToLong(word -> word.offset() + word.duration()).max().orElse(start);
            long spoken = timed.stream().filter(word -> "None".equalsIgnoreCase(word.errorType()))
                    .mapToLong(word -> word.duration() + 100_000).sum();
            if (end > start) return rounded(Math.min(100, spoken * 100.0 / (end - start)));
        }
        return average(segments.stream().map(Segment::fluency).filter(value -> value != null).toList(), "fluency");
    }

    private static double average(List<Double> values, String name) {
        if (values.isEmpty()) throw invalid("Azure Speech omitted " + name + " scores");
        return rounded(values.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
    }

    private static double rounded(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double required(Double value, String name) {
        if (value == null) throw invalid("Azure Speech omitted the " + name + " score");
        return value;
    }

    private static Map<?, ?> firstMap(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) return map(list.getFirst());
        return map(value);
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> values ? values : Map.of();
    }

    private static String string(Object value) {
        return value instanceof String text ? text : "";
    }

    private static String firstString(Object... values) {
        for (Object value : values) {
            String text = string(value);
            if (!text.isBlank()) return text;
        }
        return "";
    }

    private static Double number(Object... values) {
        for (Object value : values) if (value instanceof Number number) return number.doubleValue();
        return null;
    }

    private static Long longNumber(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static SpeechAssessmentException invalid(String message) {
        return new SpeechAssessmentException(SpeechAssessmentException.Failure.PROVIDER, message);
    }

    private record Segment(String transcript, Double accuracy, Double fluency, Double prosody, List<Word> words) {
    }

    private record Word(String text, double accuracy, String errorType, Long offset, Long duration,
                        List<PhonemeScore> phonemes) {
        WordScore score() {
            return new WordScore(text, accuracy, errorType, phonemes);
        }
    }
}
