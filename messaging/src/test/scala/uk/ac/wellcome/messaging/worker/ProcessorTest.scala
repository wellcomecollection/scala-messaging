package uk.ac.wellcome.messaging.worker

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.messaging.fixtures.worker.WorkerFixtures
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture
import org.scalatest.prop.Tables._

import scala.concurrent.ExecutionContext.Implicits.global

class ProcessorTest extends FunSpec
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

  describe("when a process runs") {
    it("results in the correct result type") {
      forAll(processResults) { (testProcess, messageToWorkShouldFail, checkType) =>
        val processor = new MyProcessor(testProcess, messageToWorkShouldFail)
        val futureResult = processor.doProcess("id", message)

        whenReady(futureResult)(checkType)
      }
    }
  }
}
