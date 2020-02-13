package software.amazon.samples.read;

import software.amazon.samples.domain.Account;
import software.amazon.samples.domain.Transaction;

import java.util.List;
import java.util.Optional;

public interface ReadApi {

    Optional<List<Account>> listAccounts();

    Optional<Account> getAccount(String id);

    Optional<List<Transaction>> getAccountTransactions(String id);
}
