package software.amazon.samples.write.simplesource;

import io.simplesource.api.CommandAPI;
import io.simplesource.api.CommandError;
import io.simplesource.api.CommandId;
import io.simplesource.data.FutureResult;
import io.simplesource.data.Result;
import io.simplesource.data.Sequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.samples.write.CreateAccountError;
import software.amazon.samples.write.WriteApi;

import java.time.Duration;
import java.util.Optional;

public class SimplesourceWriteApi implements WriteApi {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(SimplesourceWriteApi.class);

    private CommandAPI<String, AccountCommand> commandApi;

    public SimplesourceWriteApi(CommandAPI<String, AccountCommand> commandApi) {
        this.commandApi = commandApi;
    }

    @Override
    public Optional<CreateAccountError> createAccount(String accountName, double openingBalance) {
        log.debug("in create account function: " + accountName + ", " + openingBalance);
        FutureResult<CommandError, Sequence> result = commandApi.publishAndQueryCommand(
            new CommandAPI.Request<>(
                CommandId.random(),
                accountName,
                Sequence.first(),
                new AccountCommand.CreateAccount(accountName, openingBalance)
            ),
            DEFAULT_TIMEOUT
        );

        //TODO handle future resolution and error handling properly, below is a quick hacky just do it implementation
        final Result<CommandError, Sequence> resolved = result.unsafePerform(
            e -> CommandError.of(CommandError.Reason.CommandHandlerFailed, e.getMessage())
        );

        if(resolved.failureReasons().isPresent()){
            if(resolved.failureReasons().get().head().getReason() == CommandError.Reason.InvalidReadSequence) {
                return Optional.of(CreateAccountError.ACCOUNT_ALREADY_EXISTS);
            }

            Optional<CreateAccountError> error = CreateAccountError.fromString(resolved.failureReasons().get().head().getMessage());

            if(error.isPresent()) {
                return error;
            } else {
                throw new RuntimeException(resolved.failureReasons().get().head().getMessage());
            }
        }

        return Optional.empty();
    }

    @Override
    public void deposit(String account, double amount, Sequence version) {
        FutureResult<CommandError, Sequence> result = commandApi.publishAndQueryCommand(
            new CommandAPI.Request<>(
                CommandId.random(),
                account,
                version,
                new AccountCommand.Deposit(amount)
            ),
            DEFAULT_TIMEOUT
        );

        Result<CommandError, Sequence> commandErrorSequenceResult = result.unsafePerform(
            e -> CommandError.of(CommandError.Reason.InternalError, e.getMessage())
        );

        commandErrorSequenceResult.failureReasons()
            .map( errors -> (Runnable) () -> {
                log.info("Failed depositing {} in account {} with seq {}", amount, account, version);
                errors.forEach(error -> {
                    log.error("  - {}", error.getMessage());
                });
                throw new RuntimeException("Deposit failed"); // TODO should return a value
            })
            .orElse(() -> {})
            .run();
    }

    @Override
    public void withdraw(String account, double amount, Sequence version) {
        FutureResult<CommandError, Sequence> result = commandApi.publishAndQueryCommand(
            new CommandAPI.Request<>(
                CommandId.random(),
                account,
                version,
                new AccountCommand.Withdraw(amount)
            ),
            DEFAULT_TIMEOUT
        );

        Result<CommandError, Sequence> commandErrorSequenceResult = result.unsafePerform(
            e -> CommandError.of(CommandError.Reason.InternalError, e.getMessage())
        );

        commandErrorSequenceResult.failureReasons()
            .map( errors -> (Runnable) () -> {
                log.info("Failed depositing {} in account {} with seq {}", amount, account, version.getSeq());
                errors.forEach(error -> {
                    log.error("  - {}", error.getMessage());
                });
                throw new RuntimeException("Withdraw failed"); // TODO should return a value
            })
            .orElse(() -> {})
            .run();
    }
}
