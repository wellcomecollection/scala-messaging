package uk.ac.wellcome.messaging.worker.steps

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.messaging.fixtures.monitoring.metrics.MetricsFixtures
import uk.ac.wellcome.messaging.fixtures.worker.WorkerFixtures

import scala.concurrent.ExecutionContext.Implicits.global
class MessageProcessorTest
    extends AnyFunSpec
    with Matchers
    with Akka
    with ScalaFutures
    with IntegrationPatience
    with WorkerFixtures
    with MetricsFixtures {

  it(
    "calls a successful transform and process functions and returns successful result type") {
    val processor =
      new MyMessageProcessor(createResult(successful, new CallCounter))
    val futureResult = processor.process(Right(work))

    whenReady(futureResult)(shouldBeSuccessful)
  }

  it("returns deterministic failure if transformation fails") {
    val processor =
      new MyMessageProcessor(createResult(successful, new CallCounter))
    val futureResult = processor.process(Left(new RuntimeException()))

    whenReady(futureResult)(shouldBeDeterministicFailure)
  }

  it(
    "returns deterministic failure if process function fails with deterministic failure") {
    val processor =
      new MyMessageProcessor(
        createResult(deterministicFailure, new CallCounter))
    val futureResult = processor.process(Right(work))

    whenReady(futureResult)(shouldBeDeterministicFailure)
  }

  it(
    "returns non deterministic failure if process function fails with non deterministic failure") {
    val processor =
      new MyMessageProcessor(
        createResult(nonDeterministicFailure, new CallCounter))
    val futureResult = processor.process(Right(work))

    whenReady(futureResult)(shouldBeNonDeterministicFailure)
  }

  it(
    "returns deterministic failure if process function fails with an unrecognised exception state") {
    val processor =
      new MyMessageProcessor(createResult(exceptionState, new CallCounter))
    val futureResult = processor.process(Right(work))

    whenReady(futureResult)(shouldBeDeterministicFailure)
  }
}
