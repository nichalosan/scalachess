package benchmarks

import org.openjdk.jmh.annotations._

import chess.PerftTestCase

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Threads(value = 1)
class PerftBench {

  @Benchmark
  def chess960(): Int =
    PerftTestCase.chess960.flatMap(_.calculate()).map(_.result).sum

  @Benchmark
  def tricky(): Int =
    PerftTestCase.tricky.flatMap(_.calculate()).map(_.result).sum

}