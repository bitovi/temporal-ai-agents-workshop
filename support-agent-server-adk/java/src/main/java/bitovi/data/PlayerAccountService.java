package bitovi.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bitovi.data.records.PlayerAccount;
import bitovi.data.records.PlayerPurchase;

public class PlayerAccountService {
        private static final Map<String, PlayerAccount> PLAYER_ACCOUNTS = new HashMap<>();

        public static void init() {
                PlayerAccount user_8821 = new PlayerAccount("mark@example.com", "ValorantAce", "4242", List.of(
                                new PlayerPurchase("CHG-1001", "Episode 9 Battle Pass", 9.99, "2026-03-01"),
                                new PlayerPurchase("CHG-1002", "Episode 9 Battle Pass", 9.99, "2026-03-01")));
                PLAYER_ACCOUNTS.put("#8821", user_8821);

                PlayerAccount user_9932 = new PlayerAccount("alex@example.com", "NovaShard", "1111", List.of(
                                new PlayerPurchase("CHG-2001", "Legendary Skin Bundle", 24.99, "2026-02-15")));
                PLAYER_ACCOUNTS.put("#9932", user_9932);
        }

        public static PlayerAccount getPlayerAccount(String playerId) {
                System.out.println("Fetching player account for ID: " + playerId);
                if (!PLAYER_ACCOUNTS.containsKey(playerId)) {
                        System.out.println("Player account not found for ID: " + playerId);
                        return null;
                }
                System.out.println("Player account found for ID: " + playerId);
                return PLAYER_ACCOUNTS.get(playerId);
        }

        public static List<PlayerPurchase> getPlayerPurchases(String playerId) {
                System.out.println("Fetching player purchases for ID: " + playerId);
                if (!PLAYER_ACCOUNTS.containsKey(playerId)) {
                        System.out.println("Player purchases not found for ID: " + playerId);
                        return List.of();
                }
                System.out.println("Player purchases found for ID: " + playerId);
                return PLAYER_ACCOUNTS.get(playerId).getPurchases();
        }

}