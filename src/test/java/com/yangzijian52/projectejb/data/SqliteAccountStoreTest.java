package com.yangzijian52.projectejb.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteAccountStoreTest {
    @TempDir
    Path tempDirectory;

    private SqliteAccountStore store;

    @BeforeEach
    void setUp() {
        store = new SqliteAccountStore(
                new File(tempDirectory.toFile(), "accounts.db"),
                100L,
                1_000_000L,
                2000,
                true);
        store.initialize();
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void createsCreditsAndDebitsAccountAtomically() {
        UUID player = UUID.randomUUID();
        assertEquals(100L, store.getBalance(player, "Alice"));
        assertEquals(600L, store.credit(player, "Alice", 500L, "TEST", Material.DIAMOND, 1));
        assertTrue(store.tryDebit(player, "Alice", 250L, "TEST_BUY", Material.IRON_INGOT, 1));
        assertEquals(350L, store.getBalance(player, "Alice"));

        assertFalse(store.tryDebit(player, "Alice", 351L, "TEST_BUY", Material.IRON_INGOT, 1));
        assertEquals(350L, store.getBalance(player, "Alice"));
    }

    @Test
    void learningIsIdempotentAndCreditsOnlyOnce() {
        UUID player = UUID.randomUUID();
        assertTrue(store.learnAndCredit(player, "Bob", Material.DIAMOND, 8192L));
        assertFalse(store.learnAndCredit(player, "Bob", Material.DIAMOND, 8192L));
        assertTrue(store.hasLearned(player, "Bob", Material.DIAMOND));
        assertEquals(8292L, store.getBalance(player, "Bob"));
        assertEquals(Set.of(Material.DIAMOND), store.getLearned(player, "Bob"));
    }

    @Test
    void bulkLearningCommitsAllItemsAndOneBalanceChange() {
        UUID player = UUID.randomUUID();
        Map<Material, Long> entries = new LinkedHashMap<>();
        entries.put(Material.IRON_INGOT, 256L);
        entries.put(Material.GOLD_INGOT, 2048L);

        assertEquals(2, store.learnManyAndCredit(player, "Carol", entries, 2304L));
        assertEquals(2404L, store.getBalance(player, "Carol"));
        assertEquals(Set.of(Material.IRON_INGOT, Material.GOLD_INGOT), store.getLearned(player, "Carol"));
    }

    @Test
    void transferDoesNotPartiallyApplyWhenFundsAreInsufficient() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        store.credit(sender, "Sender", 900L, "TEST", null, 0);

        AccountStore.TransferResult failed = store.transfer(sender, "Sender", receiver, "Receiver", 1000L, 1L);
        assertFalse(failed.success());
        assertEquals(1000L, store.getBalance(sender, "Sender"));
        assertEquals(100L, store.getBalance(receiver, "Receiver"));

        AccountStore.TransferResult success = store.transfer(sender, "Sender", receiver, "Receiver", 400L, 20L);
        assertTrue(success.success());
        assertEquals(580L, success.senderBalance());
        assertEquals(500L, success.receiverBalance());
    }

    @Test
    void rejectsMaximumBalanceOverflowAndRollsBack() {
        UUID player = UUID.randomUUID();
        assertThrows(DataAccessException.class,
                () -> store.credit(player, "Overflow", 1_000_000L, "TEST", null, 0));
        assertEquals(100L, store.getBalance(player, "Overflow"));
    }

    @Test
    void languageAndBalancePersistAcrossReopen() {
        UUID player = UUID.randomUUID();
        store.setLanguage(player, "Dana", "en_us");
        store.credit(player, "Dana", 42L, "TEST", null, 0);
        store.close();

        store.initialize();
        assertEquals("en_us", store.getLanguage(player, "Dana"));
        assertEquals(142L, store.getBalance(player, "Dana"));
    }
}
