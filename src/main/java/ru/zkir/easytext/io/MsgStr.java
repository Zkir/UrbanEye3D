package ru.zkir.easytext.io;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a translated string, which may have multiple forms for pluralization.
 * This is a Java equivalent of the Kotlin data class.
 *
 * @param strings A list of string forms. For singular strings, this list will have one element.
 */
public final class MsgStr {

    private final List<String> strings;

    public MsgStr(List<String> strings) {
        this.strings = Objects.requireNonNull(strings, "Strings list cannot be null");
    }

    /**
     * Convenience constructor for creating a MsgStr with a single string form.
     * @param singleString The single string.
     */
    public MsgStr(String singleString) {
        this(Collections.singletonList(Objects.requireNonNull(singleString, "Single string cannot be null")));
    }

    public List<String> strings() {
        return strings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MsgStr msgStr = (MsgStr) o;
        return strings.equals(msgStr.strings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(strings);
    }

    @Override
    public String toString() {
        return "MsgStr{" +
                "strings=" + strings +
                '}';
    }
}
