package ru.zkir.easytext.io;

import java.util.Comparator;
import java.util.Objects;

/**
 * Represents a unique message identifier, consisting of the message itself (id) and an optional context.
 * This is a Java equivalent of the Kotlin data class and implements Comparable for sorting.
 *
 * @param id The message string(s).
 * @param context An optional context to disambiguate identical message ids. Can be null.
 */
public final class MsgId implements Comparable<MsgId> {

    private final MsgStr id;
    private final String context;

    public MsgId(MsgStr id, String context) {
        this.id = Objects.requireNonNull(id, "MsgStr cannot be null");
        this.context = context;
    }

    public MsgStr id() {
        return id;
    }

    public String context() {
        return context;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MsgId msgId = (MsgId) o;
        return id.equals(msgId.id) && Objects.equals(context, msgId.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, context);
    }

    @Override
    public String toString() {
        return "MsgId{" +
                "id=" + id +
                ", context='" + context + '\'' +
                '}';
    }

    @Override
    public int compareTo(MsgId other) {
        // Compare by context first. We want null contexts to come before non-null contexts.
        int contextCompare = Comparator.nullsFirst(String::compareTo).compare(this.context, other.context);

        if (contextCompare != 0) {
            return contextCompare;
        }

        // If contexts are the same (or both null), compare by the primary message id string.
        // We assume the list of strings in MsgStr is never empty.
        return this.id.strings().get(0).compareTo(other.id.strings().get(0));
    }
}
