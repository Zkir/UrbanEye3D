package ru.zkir.easytext;

import ru.zkir.easytext.io.LangWriter;
import ru.zkir.easytext.io.MsgId;
import ru.zkir.easytext.io.MsgStr;
import ru.zkir.easytext.io.PoParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The main orchestrator class for the i18n process.
 * This class replaces the external toolchain (xgettext, i18n.pl) with a pure Java solution.
 */
public final class EasyText {

    private EasyText() {
        // Utility class
    }

    public static void main(String[] args) {
        // For now, we hardcode the paths according to our project structure.
        // In the future, these could be read from args.
        Path potFilePath = Paths.get("po/urbaneye3d.pot");
        Path poDir = Paths.get("po");
        Path outputDir = Paths.get("src/main/resources/data");

        System.out.println("Starting pure Java i18n process...");

        try {
            // 1. Read the .pot file to get a master list of all message IDs.
            System.out.println("Reading master list from: " + potFilePath);
            String potContent = Files.readString(potFilePath);
            Map<MsgId, MsgStr> potEntries = PoParser.decodeToTranslations(potContent);
            List<MsgId> allMsgIds = new ArrayList<>(potEntries.keySet());
            Collections.sort(allMsgIds); // Sort alphabetically as the original script did.

            // 2. Find all .po files
            List<Path> poFiles;
            try (Stream<Path> stream = Files.list(poDir)) {
                poFiles = stream
                        .filter(p -> p.getFileName().toString().endsWith(".po"))
                        .collect(Collectors.toList());
            }

            for (Path poFilePath : poFiles) {
                String langCode=poFilePath.getFileName().toString().substring(0, poFilePath.getFileName().toString().length() - 3);

                // 3. Read the specific language .po file to get translations.
                System.out.println("Reading translations from: " + poFilePath);
                String poContent = Files.readString(poFilePath);
                Map<MsgId, MsgStr> translations = PoParser.decodeToTranslations(poContent);

                // 4. Encode and write the .lang file for the specific language.
                System.out.println("Encoding '"+ langCode +"' language...");
                byte[] langBytes = LangWriter.encodeToLang(translations, allMsgIds, langCode);
                Path langFilePath = outputDir.resolve(langCode+".lang");
                Files.write(langFilePath, langBytes);
                System.out.println("Successfully wrote " + langBytes.length + " bytes to " + langFilePath);
            }



            // 5. Encode and write the .lang file for the base language ("en").
            // For English, the "translations" are the message IDs themselves.
            System.out.println("Encoding 'en' language (base)...");
            byte[] enLangBytes = LangWriter.encodeToLang(potEntries, allMsgIds, "en");
            Path enLangFilePath = outputDir.resolve("en.lang");
            Files.write(enLangFilePath, enLangBytes);
            System.out.println("Successfully wrote " + enLangBytes.length + " bytes to " + enLangFilePath);

            System.out.println(); // Add a newline
            System.out.println("I18n process completed successfully!");

        } catch (IOException e) {
            System.err.println("An error occurred during file processing:");
            e.printStackTrace();
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("An error occurred during parsing:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
