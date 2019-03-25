package uk.ac.wellcome.messaging.worker.steps

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables._
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.messaging.fixtures.worker.WorkerFixtures
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture

import scala.concurrent.ExecutionContext.Implicits.global

class ResultProcessorTest extends FunSpec
  with Matchers
  with Akka
  with ScalaFutures
  with IntegrationPatience
  with WorkerFixtures
  with MetricsSenderFixture {

  val processResults = Table(
    ("result", "resultProcessorShouldFail", "isA"),
    (successful(work), false, shouldBeSuccessful),
    (successful(work), true, shouldBeResultProcessorFailure),
    (deterministicFailure(work), false, shouldBeSuccessful),
    (nonDeterministicFailure(work), false, shouldBeSuccessful)
  )

  describe("when a result process runs") {
    it("returns the correct result type") {
      forAll(processResults) { (result, resultProcessorShouldFail, checkType) =>
        val resultProcessor = new MyResultProcessor(resultProcessorShouldFail)

        val processedResult = resultProcessor.result("id")(result)

        whenReady(processedResult) { actualResult =>
          checkType(actualResult)

          // If the result processor fails
          // it can't pass through result
          if (resultProcessorShouldFail) {
            actualResult.summary shouldEqual None
          } else {
            actualResult.summary shouldEqual Some(result)
          }
        }
      }
    }
  }
}
