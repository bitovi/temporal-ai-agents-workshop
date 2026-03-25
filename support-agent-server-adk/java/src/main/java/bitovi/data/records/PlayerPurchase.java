package bitovi.data.records;

public class PlayerPurchase {
    private final String id;
    private final String item;
    private final double amount;
    private final String date;

    public PlayerPurchase(String id, String item, double amount, String date) {
        this.id = id;
        this.item = item;
        this.amount = amount;
        this.date = date;
    }

    public String getId() {
        return id;
    }

    public String getItem() {
        return item;
    }

    public double getAmount() {
        return amount;
    }

    public String getDate() {
        return date;
    }

    public String toString() {
        return "PlayerPurchase{" +
                "id='" + id + '\'' +
                ", item='" + item + '\'' +
                ", amount=" + amount +
                ", date='" + date + '\'' +
                '}';
    }

}