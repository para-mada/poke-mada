package net.paramada.pokemada.server;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class JsonTest {
    @Test
    void readsNestedServerResponse() {
        Object parsed = Json.parse("""
                [{"id":"abc","name":"Paquete \\u00e9pico","rewards":[{"reward_type":2,"quantity":5}],"active":true}]
                """);

        Map<?, ?> bundle = (Map<?, ?>) ((List<?>) parsed).getFirst();
        assertEquals("Paquete épico", bundle.get("name"));
        assertEquals(2L, ((Map<?, ?>) ((List<?>) bundle.get("rewards")).getFirst()).get("reward_type"));
        assertEquals(Boolean.TRUE, bundle.get("active"));
    }

    @Test
    void rejectsTrailingContent() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{} garbage"));
    }
}
