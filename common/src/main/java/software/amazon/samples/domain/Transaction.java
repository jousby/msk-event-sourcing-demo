package software.amazon.samples.domain;

public class Transaction {
    public final String id;
    public final Double amount;

    public Transaction(String id, Double amount) {
        this.id = id;
        this.amount = amount;
    }
}
