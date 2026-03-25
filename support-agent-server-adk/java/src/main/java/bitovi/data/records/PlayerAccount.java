package bitovi.data.records;

import java.util.List;

public class PlayerAccount {
    private final String email;
    private final String username;
    private final String paymentLast4;
    private final List<PlayerPurchase> purchases;

    public PlayerAccount(String email, String username, String paymentLast4, List<PlayerPurchase> purchases) {
        this.email = email;
        this.username = username;
        this.paymentLast4 = paymentLast4;
        this.purchases = purchases;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getPaymentLast4() {
        return paymentLast4;
    }

    public List<PlayerPurchase> getPurchases() {
        return purchases;
    }

    public String toString() {
        return "PlayerAccount{" +
                "email='" + email + '\'' +
                ", username='" + username + '\'' +
                ", paymentLast4='" + paymentLast4 + '\'' +
                ", purchases=" + purchases +
                '}';

    }

}