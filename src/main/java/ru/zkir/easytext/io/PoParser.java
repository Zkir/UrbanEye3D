package ru.zkir.easytext.io;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A utility class to decode Gettext .po file content into a map of MsgId to MsgStr translations.
 * This is a Java port of the logic from the gradle-josm-plugin's PoFileDecoder.kt.
 */
public final class PoParser {

    private static final Pattern REGEX_MSGCTXT = Pattern.compile("^msgctxt \\\"(.*)\\\"$");
    private static final Pattern REGEX_MSGID = Pattern.compile("^msgid \\\"(.*)\\\"$");
    private static final Pattern REGEX_MSGID_PLURAL = Pattern.compile("^msgid_plural \\\"(.*)\\\"$");
    private static final Pattern REGEX_MSGSTR = Pattern.compile("^msgstr \\\"(.*)\\\"$");
    // The original Kotlin code used `Regex("^msgstr\\[([0-9]+)\\] \"(.*)\"$")` where `[` and `]` are literal.
    // In Java regex, `[` and `]` are special characters, so they need to be escaped as `\\[` and `\\]`.
    // For a Java string literal, each backslash needs to be doubled.
    private static final Pattern REGEX_MSGSTR_INDEXED = Pattern.compile("^msgstr\\[([0-9]+)\\] \\\"(.*)\\\"$");

    /**
     * Matches every time a line ends with a double quote and the next line starts with a double quote.
     * Every such match will be removed in the process of decoding, so multiline strings appear as oneline strings.
     * In Java string literals, `\` must be `\\`, `"` must be `\"`.
     */
    private static final Pattern REGEX_MULTILINE_STRING_SEPARATOR = Pattern.compile("\\\"[ \\t\\r]*\\n[ \\t\\r]*\\\"");
    private static final Pattern REGEX_COMMENT_LINE = Pattern.compile("(\\n#[^\\n]*)+");

    // Regex for octal escapes: `\`, followed by 1 to 3 octal digits. `\` needs to be `\\` in regex, `\\\\` in Java string.
    private static final Pattern REGEX_OCTAL_ESCAPE = Pattern.compile("\\\\([0-7]{1,3})");
    // Regex for hexadecimal escapes: `\x`, followed by 1 to 4 hex digits. `\x` needs to be `\\x` in regex, `\\\\x` in Java string.
    private static final Pattern REGEX_HEX_ESCAPE = Pattern.compile("\\\\x([0-9a-fA-F]{1,4})");

    private PoParser() {
        // Utility class
    }

    /**
     * Decodes the content of a .po file into a map of MsgId to MsgStr translations.
     *
     * @param poFileContent The full content of the .po file as a String.
     * @return A Map where keys are MsgId objects (representing message IDs and context)
     * and values are MsgStr objects (representing the translated strings, possibly pluralized).
     * @throws IllegalArgumentException if a syntax error is found in the .po file content.
     */
    public static Map<MsgId, MsgStr> decodeToTranslations(String poFileContent) {
        String cleanedContent = ("\n" + poFileContent)
                .replaceAll(REGEX_COMMENT_LINE.pattern(), "") // Remove comment lines
                .replaceAll(REGEX_MULTILINE_STRING_SEPARATOR.pattern(), ""); // Handle multiline strings

        List<String> lines = Arrays.stream(cleanedContent.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        Map<MsgId, MsgStr> result = new java.util.HashMap<>();

        int currentLineIndex = 0;
        while (currentLineIndex < lines.size()) {
            String currentLine = lines.get(currentLineIndex);

            String msgctxt = null;
            Matcher msgctxtMatcher = REGEX_MSGCTXT.matcher(currentLine);
            if (msgctxtMatcher.matches()) {
                msgctxt = unescapeCharacters(msgctxtMatcher.group(1));
                currentLineIndex++;
                if (currentLineIndex < lines.size()) {
                    currentLine = lines.get(currentLineIndex);
                } else {
                    // Reached end of file after msgctxt, implies syntax error or incomplete entry
                    throw new IllegalArgumentException("Syntax error: Incomplete entry after msgctxt at end of file.");
                }
            }

            // msgid is mandatory
            Matcher msgidMatcher = REGEX_MSGID.matcher(currentLine);
            String msgidSingular;
            if (msgidMatcher.matches()) {
                msgidSingular = unescapeCharacters(msgidMatcher.group(1));
                currentLineIndex++;
            } else {
                throw new IllegalArgumentException("Syntax error on line `" + currentLine + "`: This line was expected to be a `msgid` line");
            }


            String msgidPlural = null;
            if (currentLineIndex < lines.size()) {
                currentLine = lines.get(currentLineIndex);
                Matcher msgidPluralMatcher = REGEX_MSGID_PLURAL.matcher(currentLine);
                if (msgidPluralMatcher.matches()) {
                    msgidPlural = unescapeCharacters(msgidPluralMatcher.group(1));
                    currentLineIndex++;
                }
            }


            MsgStr msgstr;
            if (msgidPlural != null) {
                // Plural forms
                List<Map.Entry<Integer, String>> pluralForms = new ArrayList<>();
                boolean foundAnotherPluralForm;
                do {
                    if (currentLineIndex >= lines.size()) {
                        // If a translation with plurals is the last one in a file
                        foundAnotherPluralForm = false;
                    } else {
                        currentLine = lines.get(currentLineIndex);
                        Matcher msgstrIndexedMatcher = REGEX_MSGSTR_INDEXED.matcher(currentLine);
                        if (msgstrIndexedMatcher.matches()) {
                            pluralForms.add(new AbstractMap.SimpleEntry<>(
                                    Integer.parseInt(msgstrIndexedMatcher.group(1)),
                                    unescapeCharacters(msgstrIndexedMatcher.group(2))
                            ));
                            foundAnotherPluralForm = true;
                            currentLineIndex++;
                        } else {
                            foundAnotherPluralForm = false;
                        }
                    }
                } while (foundAnotherPluralForm);

                if (pluralForms.isEmpty()) {
                    throw new IllegalArgumentException("The plural forms for '" + msgidSingular + "' must not be empty!");
                }

                List<String> sortedPluralStrings = pluralForms.stream()
                        .sorted(Comparator.comparingInt(Map.Entry::getKey))
                        .map(Map.Entry::getValue)
                        .collect(Collectors.toList());

                // Validate indices are consecutive and start from 0
                for (int i = 0; i < sortedPluralStrings.size(); i++) {
                    if (!pluralForms.get(i).getKey().equals(i)) {
                        throw new IllegalArgumentException(
                                "Syntax error: The translations for '" + msgidSingular +
                                        "' are missing msgstr[" + i + "] (only found indices " +
                                        pluralForms.stream().map(Map.Entry::getKey).sorted().collect(Collectors.toList()) + ")"
                        );
                    }
                }
                msgstr = new MsgStr(sortedPluralStrings);

            } else {
                // Singular form
                if (currentLineIndex >= lines.size()) {
                    throw new IllegalArgumentException("Syntax error: Incomplete entry, expected msgstr line at end of file.");
                }
                currentLine = lines.get(currentLineIndex);
                Matcher msgstrMatcher = REGEX_MSGSTR.matcher(currentLine);
                if (msgstrMatcher.matches()) {
                    msgstr = new MsgStr(unescapeCharacters(msgstrMatcher.group(1)));
                    currentLineIndex++;
                } else {
                    throw new IllegalArgumentException("Syntax error on line `" + currentLine + "`: This line was expected to be a `msgstr` line");
                }
            }

            if (msgidSingular.isEmpty()) {
                continue; // Skip the header entry
            }

            result.put(new MsgId(new MsgStr(Arrays.asList(msgidSingular, msgidPlural).stream().filter(Objects::nonNull).collect(Collectors.toList())), msgctxt), msgstr);
        }
        return result;
    }

    /**
     * Unescapes characters in a string, converting Gettext-style escapes to actual characters.
     */
    public static String unescapeCharacters(String s) {
        String unescaped = s
                .replace("\\\\", "\\") // Backslash first!
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\n", "\n")
                .replace("\\a", "\u0007")
                .replace("\\b", "\u0008")
                .replace("\\f", "\u000C")
                .replace("\\v", "\u000B")
                .replace("\\\"", "\"");

        // Handle octal escapes
        unescaped = REGEX_OCTAL_ESCAPE.matcher(unescaped).replaceAll(matchResult ->
                Character.toString((char) Integer.parseInt(matchResult.group(1), 8))
        );

        // Handle hexadecimal escapes
        unescaped = REGEX_HEX_ESCAPE.matcher(unescaped).replaceAll(matchResult ->
                Character.toString((char) Integer.parseInt(matchResult.group(1), 16))
        );

        return unescaped;
    }
}