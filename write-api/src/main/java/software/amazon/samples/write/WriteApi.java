package software.amazon.samples.write;

import io.simplesource.data.Sequence;

import java.util.Optional;

public interface WriteApi {
    Optional<CreateAccountError> createAccount(String accountName, double openingBalance);

    void deposit(String account, double amount, Sequence version);

    void withdraw(String account, double amount, Sequence version);
}
