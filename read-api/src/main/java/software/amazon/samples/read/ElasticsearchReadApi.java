package software.amazon.samples.read;

import software.amazon.samples.read.domain.Account;
import software.amazon.samples.read.domain.Transaction;
import spark.Request;
import spark.Response;

import java.util.List;
import java.util.Optional;

public class ElasticsearchReadApi implements ReadApi{

    public Optional<List<Account>> listAccounts() {
        return Optional.of(List.of(new Account("1", "Savings")));
    }

    public Optional<Account> getAccount(String id) {
        return Optional.of(new Account("1", "Savings"));
    }

    public Optional<List<Transaction>> getAccountTransactions(String id) {
        return Optional.of(List.of(new Transaction("1", 100.00)));
    }
}
