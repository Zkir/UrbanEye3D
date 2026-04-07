package ru.zkir.easytext.io;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A utility class to encode translation data into the JOSM .lang binary format.
 * This is a Java port of the logic from the gradle-josm-plugin's LangFileEncoder.kt.
 */
public final class LangWriter {

    private static final String BASE_LANGUAGE = "en";
    private static final int SEPARATOR = 0xFFFF;
    private static final int SAME_AS_MSGID_SINGULAR = 0xFFFE;
    private static final byte SAME_AS_MSGID_PLURAL = (byte) 0xFE;

    private LangWriter() {
        // Utility class
    }

    /**
     * Encodes a map of translations for a specific language into the .lang file byte format.
     *
     * @param translations The map of translations for the target language.
     * @param allMsgIds    A complete, sorted list of all MsgIds in the project.
     * @param language     The language code (e.g., "ru") being written.
     * @return A byte array representing the .lang file.
     * @throws IOException If an I/O error occurs during writing to the byte stream.
     */
    public static byte[] encodeToLang(Map<MsgId, MsgStr> translations, List<MsgId> allMsgIds, String language) throws IOException {
        // Partition the master list of all known MsgIds into singular and plural lists.
        List<MsgId> singularMsgIds = allMsgIds.stream().filter(m -> m.id().strings().size() <= 1).collect(Collectors.toList());
        List<MsgId> pluralMsgIds = allMsgIds.stream().filter(m -> m.id().strings().size() > 1).collect(Collectors.toList());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // --- 1. Write Singular Strings ---
        for (MsgId msgId : singularMsgIds) {
            if (BASE_LANGUAGE.equals(language)) {
                writeBaseLanguageEntry(dos, msgId);
            } else {
                writeTranslatedEntry(dos, translations.get(msgId), msgId);
            }
        }

        // --- 2. Write Separator ---
        dos.writeShort(SEPARATOR);

        // --- 3. Write Plural Strings ---
        for (MsgId msgId : pluralMsgIds) {
            if (BASE_LANGUAGE.equals(language)) {
                writeBaseLanguagePluralEntry(dos, msgId);
            } else {
                writeTranslatedPluralEntry(dos, translations.get(msgId), msgId);
            }
        }

        dos.flush();
        return baos.toByteArray();
    }

    private static void writeBaseLanguageEntry(DataOutputStream dos, MsgId msgId) throws IOException {
        String text = msgId.id().strings().get(0);
        if (msgId.context() != null) {
            text = "_:" + msgId.context() + System.lineSeparator() + text;
        }
        writeString(dos, text);
    }

    private static void writeTranslatedEntry(DataOutputStream dos, MsgStr translation, MsgId originalMsgId) throws IOException {
        if (translation == null) {
            dos.writeShort(0); // Missing translation
        } else if (translation.equals(originalMsgId.id())) {
            dos.writeShort(SAME_AS_MSGID_SINGULAR); // Same as original
        } else {
            writeString(dos, translation.strings().get(0));
        }
    }

    private static void writeBaseLanguagePluralEntry(DataOutputStream dos, MsgId msgId) throws IOException {
        List<String> forms = msgId.id().strings();
        dos.writeByte(forms.size());

        String firstForm = forms.get(0);
        if (msgId.context() != null) {
            firstForm = "_:" + msgId.context() + System.lineSeparator() + firstForm;
        }
        writeString(dos, firstForm);

        for (int i = 1; i < forms.size(); i++) {
            writeString(dos, forms.get(i));
        }
    }

    private static void writeTranslatedPluralEntry(DataOutputStream dos, MsgStr translation, MsgId originalMsgId) throws IOException {
        if (translation == null) {
            dos.writeByte(0); // Missing translation
        } else if (translation.equals(originalMsgId.id())) {
            dos.writeByte(SAME_AS_MSGID_PLURAL); // Same as original
        } else {
            List<String> forms = translation.strings();
            dos.writeByte(forms.size());
            for (String form : forms) {
                writeString(dos, form);
            }
        }
    }

    /**
     * Writes a string to the DataOutputStream in the required JOSM .lang format
     * (2-byte big-endian length prefix, followed by UTF-8 bytes).
     */
    private static void writeString(DataOutputStream dos, String s) throws IOException {
        byte[] stringBytes = s.getBytes(StandardCharsets.UTF_8);
        if (stringBytes.length >= 65534) { // 0xFFFE and 0xFFFF are reserved
            throw new IllegalArgumentException("String is too long for the .lang format (>= 65534 bytes): " + s.substring(0, 100));
        }
        dos.writeShort(stringBytes.length);
        dos.write(stringBytes);
    }
}
