package software.amazon.samples.read;

import software.amazon.samples.domain.AccountSummary;
import software.amazon.samples.domain.AccountTransaction;

import java.util.List;
import java.util.Optional;

public interface ReadApi {

    Optional<AccountSummary> accountSummary(String accountName);

    List<AccountSummary> list();

    List<AccountTransaction> getTransactions(String accountName);
}
