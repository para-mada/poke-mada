package net.paramada.pokemada.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ServerClientTest {
    @Test
    void acceptsEmptyTrainerForAuthenticatedAccountWithoutTrainer() {
        ServerClient.Trainer trainer = ServerClient.trainerFromBody("");

        assertEquals(0, trainer.id());
        assertEquals("Entrenador", trainer.name());
    }

    @Test
    void readsTrainerWhenProfileExists() {
        ServerClient.Trainer trainer = ServerClient.trainerFromBody("{\"id\":12,\"name\":\"Mada\"}");

        assertEquals(12, trainer.id());
        assertEquals("Mada", trainer.name());
    }

    @Test
    void readsNotificationMetadata() {
        ServerClient.Notification notification = ServerClient.notificationsFromBody(
                "[{\"id\":7,\"message\":\"Llegó un comodín\",\"created_at\":\"2026-08-24T20:15:30Z\"}]").getFirst();

        assertEquals(7, notification.id());
        assertEquals("Llegó un comodín", notification.message());
        assertEquals("2026-08-24T20:15:30Z", notification.createdAt().toString());
    }

    @Test
    void readsVirtualInventoryContract() {
        ServerClient.VirtualItemStack item = ServerClient.virtualInventoryFromBody("""
                [{"code":"SUPER_PROTEIN","name":"Super Proteína","description":"Ataque +1",
                "sprite":"https://example.test/media/virtual_items/super_protein.png","pocket":"supplements",
                "quantity":3,"reserved_quantity":1,"available_quantity":2,
                "required_inputs":{"target_profile":false,"target_pokemon":true},
                "client_capabilities":["modify_pokemon_stat.v1"]}]
                """).getFirst();

        assertEquals("SUPER_PROTEIN", item.code());
        assertEquals(2, item.availableQuantity());
        assertEquals("supplements", item.pocket());
        assertEquals("https://example.test/media/virtual_items/super_protein.png", item.spriteUrl());
        assertTrue(item.requiresTargetPokemon());
        assertEquals("modify_pokemon_stat.v1", item.clientCapabilities().getFirst());
    }

    @Test
    void readsAuthorizedStatCommand() {
        ServerClient.ActionOperation operation = ServerClient.operationFromBody("""
                {"id":"9a33b593-3baa-4ea3-8eba-fb5938663406","state":"awaiting_client","commands":[{
                "id":"927b7ca6-c608-4506-ada9-85f137c60eb1",
                "operation_id":"9a33b593-3baa-4ea3-8eba-fb5938663406","position":0,
                "capability":"modify_pokemon_stat.v1","state":"pending",
                "payload":{"target_pokemon_id":123,"stat":"attack","amount":1,"quantity":1}}]}
                """);

        assertEquals("awaiting_client", operation.state());
        assertEquals(123L, operation.commands().getFirst().payload().get("target_pokemon_id"));
        assertEquals("attack", operation.commands().getFirst().payload().get("stat"));
    }

    @Test
    void readsBoosterPackSummaryAndOdds() {
        ServerClient.BoosterPackSummary summary = ServerClient.boosterPacksFromBody("""
                [{"code":"MASTER_V_ALOLA","name":"Alola Master Pack","description":"Cinco premios",
                "art_url":"https://example.test/pack.png","quantity":2,"cards_per_pack":5,
                "guarantee_label":"Rara garantizada","configuration_version":3}]
                """).getFirst();
        ServerClient.BoosterPackDetail detail = ServerClient.boosterPackDetailFromBody("""
                {"code":"MASTER_V_ALOLA","name":"Alola Master Pack","description":"Cinco premios",
                "art_url":null,"quantity":2,"cards_per_pack":1,"guarantee_label":"Rara garantizada",
                "configuration_version":3,"slots":[{"position":1,"label":"Garantía","pool":"RARE",
                "entries":[{"name":"Revive","rarity":"RARE","image_url":null,"probability":25.5}]}]}
                """);

        assertEquals(2, summary.quantity());
        assertEquals(5, summary.cardsPerPack());
        assertEquals("RARE", detail.slots().getFirst().pool());
        assertEquals(25.5, detail.slots().getFirst().entries().getFirst().probability());
    }

    @Test
    void readsDurablePackOpening() {
        ServerClient.PackOpening opening = ServerClient.packOpeningFromBody("""
                {"id":"9a33b593-3baa-4ea3-8eba-fb5938663406","pack_code":"MASTER_V_ALOLA",
                "pack_name":"Alola Master Pack","state":"COMPLETED","remaining_quantity":1,
                "reward_bundle_id":"927b7ca6-c608-4506-ada9-85f137c60eb1",
                "created_at":"2026-08-26T06:00:00Z","replayed":false,
                "results":[{"position":1,"name":"Revive","rarity":"RARE","image_url":null}]}
                """);

        assertEquals("MASTER_V_ALOLA", opening.packCode());
        assertEquals(1, opening.remainingQuantity());
        assertEquals("Revive", opening.results().getFirst().name());
    }
}
