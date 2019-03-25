package uk.ac.wellcome.messaging.worker

import org.scalatest.{FunSpec, Matchers}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.messaging.fixtures.worker.{MetricsFixtures, WorkerFixtures}
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture
import org.scalatest.prop.Tables._

import scala.concurrent.ExecutionContext.Implicits.global

class WorkerTest extends FunSpec
  with Matchers
  with Akka
  with ScalaFutures
  with IntegrationPatience
  with WorkerFixtures
  with MetricsFixtures
  with MetricsSenderFixture {


  describe("when a message is processed") {
    it("increments metrics, processes work and returns the correct action") {

      val processResults = Table(
        ("result", "calledCount", "messageFail", "resultFail", "monitorFail", "metricName", "count", "empty"),
        (successful, 1, false, false, false, "namespace/Successful", 1, false),
        (successful, 0, true, false, false, "namespace/DeterministicFailure", 1, false),
        (successful, 1, false, true, false, "namespace/Successful", 1, false),
        (successful, 1, false, false, true, "noMetric", 0, true),

        (deterministicFailure, 1, false, false,false, "namespace/DeterministicFailure", 1, false),
        (nonDeterministicFailure, 1, false, false, false, "namespace/NonDeterministicFailure", 1, false)
      )

      forAll(processResults) {
        (testProcess, calledCount, toWorkFail,
         toActionFail, monClientFail, metricName,
         metricCount, empty) => {

          val worker = new MyWorker(
            testProcess,
            toWorkFail,
            monClientFail
          )

          val process = worker.work("id", message)

          whenReady(process) { _ =>
            worker.calledCount shouldBe calledCount

            assertMetricCount(worker.metrics,
              metricName, metricCount, empty)

            assertMetricDurations(worker.metrics,
              "namespace/Duration", metricCount, empty)
          }
        }
      }
    }
  }
}
