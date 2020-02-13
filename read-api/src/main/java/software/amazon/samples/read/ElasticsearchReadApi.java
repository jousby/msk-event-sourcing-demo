package software.amazon.samples.read;

import software.amazon.samples.domain.Account;
import software.amazon.samples.domain.Transaction;

import java.util.List;
import java.util.Optional;

public class ElasticsearchReadApi implements ReadApi{
    @Override
    public Optional<List<Account>> listAccounts() {
        return Optional.of(List.of(new Account("1", "Savings")));
    }

    @Override
    public Optional<Account> getAccount(String id) {
        return Optional.of(new Account("1", "Savings"));
    }

    @Override
    public Optional<List<Transaction>> getAccountTransactions(String id) {
        return Optional.of(List.of(new Transaction("1", 100.00)));
    }
}
