package software.amazon.samples.projections;

import software.amazon.samples.write.simplesource.AccountEvent;
import io.simplesource.kafka.model.ValueWithSequence;

public interface Indexer {
    void index(String key, ValueWithSequence<AccountEvent> value);
}
