package uk.ac.wellcome.messaging.worker.steps

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables._
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

  val processResults = Table(
    ("testProcess", "messageToWorkShouldFail", "isA"),
    (successful, false, shouldBeSuccessful),
    (successful, true, shouldBeDeterministicFailure),
    (deterministicFailure, false, shouldBeDeterministicFailure),
    (nonDeterministicFailure, false, shouldBeNonDeterministicFailure),
    (exceptionState, false, shouldBeDeterministicFailure)
  )

  describe("when a message process runs") {
    it("returns the correct result type") {
      forAll(processResults) {
        (testProcess, messageToWorkShouldFail, checkType) =>
          val processor =
            new MyMessageProcessor(testProcess, messageToWorkShouldFail)
          val futureResult = processor.process(message)

          whenReady(futureResult)(checkType)
      }
    }
  }
}
