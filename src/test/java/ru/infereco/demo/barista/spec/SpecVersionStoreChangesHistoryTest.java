package ru.infereco.demo.barista.spec;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpecVersionStoreChangesHistoryTest {

    @Test
    void appendsChangesAcrossVersionPairs(@TempDir Path dir) {
        SpecVersionStore store = new SpecVersionStore(dir, new ObjectMapper());
        SpecChange first = SpecChange.create(
                "c1", "doc", 1, 2, "modified", "high", "3-2", "Отмена",
                "s1", "было", "стало", "смысл", true, "old", "new", null);
        SpecChange second = SpecChange.create(
                "c2", "doc", 2, 3, "modified", "high", "3-2", "Отмена",
                "s2", "было2", "стало2", "смысл2", true, "old2", "new2", null);

        store.saveChanges("doc", List.of(first));
        store.saveChanges("doc", List.of(second));

        List<SpecChange> all = store.changesOf("doc");
        assertThat(all).hasSize(2);
        assertThat(all).extracting(SpecChange::id).containsExactly("c1", "c2");
    }

    @Test
    void reanalyzeSameToVersionReplacesBatch(@TempDir Path dir) {
        SpecVersionStore store = new SpecVersionStore(dir, new ObjectMapper());
        SpecChange first = SpecChange.create(
                "c1", "doc", 1, 2, "modified", "high", "3-2", "Отмена",
                "s1", "было", "стало", "смысл", true, "old", "new", null);
        SpecChange again = SpecChange.create(
                "c1b", "doc", 1, 2, "modified", "medium", "3-2", "Отмена",
                "s1b", "было", "стало иначе", "смысл", true, "old", "new", null);

        store.saveChanges("doc", List.of(first));
        store.saveChanges("doc", List.of(again));

        assertThat(store.changesOf("doc")).hasSize(1);
        assertThat(store.changesOf("doc").getFirst().id()).isEqualTo("c1b");
    }
}
