package io.telemetry.ai.otel.pii;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interface for PII detectors.
 */
public interface PIIDetector {
    /**
     * Anonymizes PII in the input text.
     *
     * @param input The input text
     * @return The anonymized text
     */
    String anonymize(String input);

    /**
     * A regex-based PII detector.
     */
    class RegexPIIDetector implements PIIDetector {
        private final Map<Pattern, String> patterns;

        /**
         * Creates a new RegexPIIDetector with the specified patterns.
         *
         * @param patterns A map of regex patterns to replacement strings
         */
        public RegexPIIDetector(Map<String, String> patterns) {
            this.patterns = new HashMap<>();
            for (Map.Entry<String, String> entry : patterns.entrySet()) {
                this.patterns.put(Pattern.compile(entry.getKey()), entry.getValue());
            }
        }

        @Override
        public String anonymize(String input) {
            if (input == null || input.isEmpty()) {
                return input;
            }

            String result = input;
            for (Map.Entry<Pattern, String> entry : patterns.entrySet()) {
                Matcher matcher = entry.getKey().matcher(result);
                result = matcher.replaceAll(entry.getValue());
            }

            return result;
        }
    }
} 