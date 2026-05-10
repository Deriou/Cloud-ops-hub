package dev.deriou.gateway.ops;

import java.time.Instant;
import java.util.List;

public interface PrometheusClient {

    double queryInstant(String promQl);

    List<Double> queryRange(String promQl, Instant start, Instant end, String step);
}
