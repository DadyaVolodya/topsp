package ru.infereco.demo.barista.spec;

import java.util.ArrayList;
import java.util.List;

public record SpecChange(
        String id,
        String documentId,
        int fromVersion,
        int toVersion,
        String type,
        String significance,
        String sectionPath,
        String heading,
        String summary,
        String oldBehavior,
        String newBehavior,
        String businessImpact,
        boolean requiresCodeChange,
        String oldText,
        String newText,
        boolean excluded,
        List<AffectedCode> affected,
        NavigationHint navigation
) {
    public SpecChange {
        affected = affected == null ? List.of() : List.copyOf(affected);
    }

    public SpecChange withAffected(List<AffectedCode> next) {
        return new SpecChange(
                id, documentId, fromVersion, toVersion, type, significance, sectionPath, heading,
                summary, oldBehavior, newBehavior, businessImpact, requiresCodeChange, oldText, newText,
                excluded, next == null ? List.of() : List.copyOf(next), navigation);
    }

    public SpecChange withExcluded(boolean value) {
        return new SpecChange(
                id, documentId, fromVersion, toVersion, type, significance, sectionPath, heading,
                summary, oldBehavior, newBehavior, businessImpact, requiresCodeChange, oldText, newText,
                value, affected, navigation);
    }

    public record AffectedCode(
            String repo,
            String path,
            String symbol,
            String symbolType,
            String reason,
            double confidence,
            int startLine,
            int endLine
    ) {
    }

    public record NavigationHint(String route, String target, String action, String openUrl) {
    }

    public static SpecChange create(
            String id,
            String documentId,
            int fromVersion,
            int toVersion,
            String type,
            String significance,
            String sectionPath,
            String heading,
            String summary,
            String oldBehavior,
            String newBehavior,
            String businessImpact,
            boolean requiresCodeChange,
            String oldText,
            String newText,
            NavigationHint navigation
    ) {
        return new SpecChange(
                id, documentId, fromVersion, toVersion, type, significance, sectionPath, heading,
                summary, oldBehavior, newBehavior, businessImpact, requiresCodeChange, oldText, newText,
                false, new ArrayList<>(), navigation);
    }
}
