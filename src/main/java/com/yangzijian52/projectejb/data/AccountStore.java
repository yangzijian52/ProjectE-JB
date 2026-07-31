package com.yangzijian52.projectejb.data;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;

public interface AccountStore extends AutoCloseable {
    void initialize();

    long getBalance(UUID playerId, String playerName);

    String getLanguage(UUID playerId, String playerName);

    void setLanguage(UUID playerId, String playerName, String language);

    boolean hasLearned(UUID playerId, String playerName, Material material);

    Set<Material> getLearned(UUID playerId, String playerName);

    boolean learnAndCredit(UUID playerId, String playerName, Material material, long emc);

    int learnManyAndCredit(UUID playerId, String playerName, Map<Material, Long> entries, long totalEmc);

    long credit(UUID playerId, String playerName, long amount, String type, Material material, int itemCount);

    boolean tryDebit(UUID playerId, String playerName, long amount, String type, Material material, int itemCount);

    TransferResult transfer(
            UUID senderId,
            String senderName,
            UUID receiverId,
            String receiverName,
            long amount,
            long fee);

    long setBalance(UUID playerId, String playerName, long balance, String type);

    long takeUpTo(UUID playerId, String playerName, long amount, String type);

    boolean isOpen();

    String databasePath();

    @Override
    void close();

    record TransferResult(boolean success, long senderBalance, long receiverBalance) {}
}
