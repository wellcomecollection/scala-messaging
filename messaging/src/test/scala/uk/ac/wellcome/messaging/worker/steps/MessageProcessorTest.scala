package uk.ac.wellcome.messaging.worker.steps

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.messaging.fixtures.worker.WorkerFixtures
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture

import scala.concurrent.ExecutionContext.Implicits.global
class MessageProcessorTest
    extends FunSpec
    with Matchers
    with Akka
    with ScalaFutures
    with IntegrationPatience
    with WorkerFixtures
    with MetricsSenderFixture {

  it(
    "calls a successful transform and process functions and returns successful result type") {
    val processor =
      new MyMessageProcessor(createResult(successful, new CallCounter))
    val futureResult = processor.process(work)

    whenReady(futureResult)(shouldBeSuccessful)
  }

  it(
    "returns deterministic failure if process function fails with deterministic failure") {
    val processor =
      new MyMessageProcessor(
        createResult(deterministicFailure, new CallCounter))
    val futureResult = processor.process(work)

    whenReady(futureResult)(shouldBeDeterministicFailure)
  }

  it(
    "returns non deterministic failure if process function fails with non deterministic failure") {
    val processor =
      new MyMessageProcessor(
        createResult(nonDeterministicFailure, new CallCounter))
    val futureResult = processor.process(work)

    whenReady(futureResult)(shouldBeNonDeterministicFailure)
  }

  it(
    "returns deterministic failure if process function fails with an unrecognised exception state") {
    val processor =
      new MyMessageProcessor(createResult(exceptionState, new CallCounter))
    val futureResult = processor.process(work)

    whenReady(futureResult)(shouldBeDeterministicFailure)
  }
}
