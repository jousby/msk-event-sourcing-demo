package software.amazon.samples.write;

import java.util.List;
import java.util.Optional;

public interface WriteApi {
    void createAccount(String accountName, double openingBalance);

    void deposit(String account, double amount, long sequence);

    void withdraw(String account, double amount, long sequence);
}
