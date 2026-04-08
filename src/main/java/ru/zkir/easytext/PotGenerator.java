package ru.zkir.easytext;

import ru.zkir.easytext.io.MsgId;
import ru.zkir.easytext.io.MsgStr;
import ru.zkir.easytext.io.PoParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Scans Java source files to extract translatable strings and generates a .pot template file.
 * This is a Java-based replacement for xgettext.
 */
public final class PotGenerator {

    // Regex patterns to find gettext function calls.
    // They are designed to handle escaped quotes (\") inside the strings.
    private static final Pattern REGEX_TR = Pattern.compile( "(?<![A-Za-z])(?:tr|marktr)\\s*\\(\\s*\\\"((?:\\\\\\\"|[^\\\"])*)\\\"\\s*[,)]");
    private static final Pattern REGEX_TRC = Pattern.compile("(?<![A-Za-z])(?:trc|marktrc)\\s*\\(\\s*\\\"((?:\\\\\\\"|[^\\\"])*)\\\"\\s*,\\s*\\\"((?:\\\\\\\"|[^\\\"])*)\\\"\\s*\\)");
    private static final Pattern REGEX_TRN = Pattern.compile("(?<![A-Za-z])trn\\s*\\(\\s*\\\"((?:\\\\\\\"|[^\\\"])*)\\\"\\s*,\\s*\\\"((?:\\\\\\\"|[^\\\"])*)\\\"");
    private static final Pattern REGEX_TRNC = Pattern.compile("(?<![A-Za-z])trnc\\s*\\(\\s*\\\"((?:\\\\\\\"|[^\\\"])*)\\\"\\s*,\\s*\\\"((?:\\\\\\\"|[^\\\"])*)\\\"\\s*,\\s*\\\"((?:\\\\\\\"|[^\\\"])*)\\\"");

    private PotGenerator() {
        // Utility class
    }

    public static void main(String[] args) {
        Path sourceDir = Paths.get("src/main/java");
        Path outputFile = Paths.get("po/urbaneye3d.pot");

        System.out.println("Starting .pot file generation...");
        System.out.println("Scanning for .java files in: " + sourceDir.toAbsolutePath());

        try {
            List<Path> javaFiles;
            try (Stream<Path> stream = Files.walk(sourceDir)) {
                javaFiles = stream
                        .filter(p -> p.toString().endsWith(".java"))
                        .collect(Collectors.toList());
            }

            System.out.println("Found " + javaFiles.size() + " java files to scan.");

            Map<MsgId, List<String>> extractedStrings = new TreeMap<>();

            for (Path javaFile : javaFiles) {
                extractStringsFromFile(javaFile, extractedStrings);
            }

            String potContent = formatPotFile(extractedStrings);
            Files.write(outputFile, potContent.getBytes(StandardCharsets.UTF_8));

            System.out.println("Successfully generated .pot file: " + outputFile.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("Error during .pot file generation:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void extractStringsFromFile(Path javaFile, Map<MsgId, List<String>> extractedStrings) throws IOException {
        String content = Files.readString(javaFile);
        String relativePath = javaFile.toString();
        String contentWithoutComments = content.replaceAll("//.*", "").replaceAll("/\\*[\\s\\S]*?\\*/", "");

        Matcher trMatcher = REGEX_TR.matcher(contentWithoutComments);
        while (trMatcher.find()) {
            addEntry(new MsgId(new MsgStr(PoParser.unescapeCharacters(trMatcher.group(1))), null), relativePath, getLineNumber(content, trMatcher.start()), extractedStrings);
        }

        Matcher trcMatcher = REGEX_TRC.matcher(contentWithoutComments);
        while (trcMatcher.find()) {
            addEntry(new MsgId(new MsgStr(PoParser.unescapeCharacters(trcMatcher.group(2))), PoParser.unescapeCharacters(trcMatcher.group(1))), relativePath, getLineNumber(content, trcMatcher.start()),
                    extractedStrings);
        }

        Matcher trnMatcher = REGEX_TRN.matcher(contentWithoutComments);
        while (trnMatcher.find()) {
            addEntry(new MsgId(new MsgStr(List.of(PoParser.unescapeCharacters(trnMatcher.group(1)), PoParser.unescapeCharacters(trnMatcher.group(2)))), null), relativePath, getLineNumber(content,
                    trnMatcher.start()), extractedStrings);
        }

        Matcher trncMatcher = REGEX_TRNC.matcher(contentWithoutComments);
        while (trncMatcher.find()) {
            addEntry(new MsgId(new MsgStr(List.of(PoParser.unescapeCharacters(trncMatcher.group(2)), PoParser.unescapeCharacters(trncMatcher.group(3)))),
                    PoParser.unescapeCharacters(trncMatcher.group(1))), relativePath, getLineNumber(content, trncMatcher.start()), extractedStrings);
        }
    }

    private static void addEntry(MsgId msgId, String filePath, int lineNumber, Map<MsgId, List<String>> extractedStrings) {
        String location = filePath.replace('\\', '/') + ":" + lineNumber;
        extractedStrings.computeIfAbsent(msgId, k -> new ArrayList<>()).add(location);
    }

    private static int getLineNumber(String content, int position) {
        return content.substring(0, position).split("\\r\\n|\\r|\\n").length;
    }

    private static String formatPotFile(Map<MsgId, List<String>> extractedStrings) {
        StringBuilder sb = new StringBuilder();
        String creationDate = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssZ"));

        sb.append("# SOME DESCRIPTIVE TITLE.\n");
        sb.append("# Copyright (C) YEAR THE PACKAGE'S COPYRIGHT HOLDER\n");
        sb.append("# This file is distributed under the same license as the PACKAGE package.\n");
        sb.append("# FIRST AUTHOR <EMAIL@ADDRESS>, YEAR.\n");
        sb.append("#\n");
        sb.append("#, fuzzy\n");
        sb.append("msgid \"\"\n");
        sb.append("msgstr \"\"\n");
        sb.append("\"Project-Id-Version: PACKAGE VERSION\\n\"\n");
        sb.append("\"Report-Msgid-Bugs-To: \\n\"\n");
        sb.append("\"POT-Creation-Date: ").append(creationDate).append("\\n\"\n");
        sb.append("\"PO-Revision-Date: YEAR-MO-DA HO:MI+ZONE\\n\"\n");
        sb.append("\"Last-Translator: FULL NAME <EMAIL@ADDRESS>\\n\"\n");
        sb.append("\"Language-Team: LANGUAGE <LL@li.org>\\n\"\n");
        sb.append("\"Language: \\n\"\n");
        sb.append("\"MIME-Version: 1.0\\n\"\n");
        sb.append("\"Content-Type: text/plain; charset=UTF-8\\n\"\n");
        sb.append("\"Content-Transfer-Encoding: 8bit\\n\"\n");
        sb.append("\"Plural-Forms: nplurals=2; plural=(n != 1);\\n\"\n");

        for (Map.Entry<MsgId, List<String>> entry : extractedStrings.entrySet()) {
            MsgId msgId = entry.getKey();
            List<String> locations = entry.getValue();

            sb.append("\n");
            for (String location : locations) {
                sb.append("#: ").append(location).append("\n");
            }

            if (msgId.context() != null) {
                sb.append("msgctxt \"").append(escapeString(msgId.context())).append("\"\n");
            }

            List<String> idStrings = msgId.id().strings();
            sb.append("msgid \"").append(escapeString(idStrings.get(0))).append("\"\n");
            if (idStrings.size() > 1) {
                sb.append("msgid_plural \"").append(escapeString(idStrings.get(1))).append("\"\n");
                sb.append("msgstr[0] \"\"\n");
                sb.append("msgstr[1] \"\"\n");
            } else {
                sb.append("msgstr \"\"\n");
            }
        }
        return sb.toString();
    }

    private static String escapeString(String s) {
        return s.replace("\"", "\\\"").replace("\n", "\\n");
    }
}