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
        ("result", "called", "toWork", "toAction", "metrics", "metricName", "count", "empty"),
        (successful,
          true, false, false, false, "namespace/Successful", 1, false),

        (successful,
          false, true, false, false, "namespace/DeterministicFailure", 1, false),
//        (successful,
//          true, false, true, false, "namespace/Successful", 1, false),
        (successful,
          true, false, false, true, "noMetric", 0, true),

        (deterministicFailure,
          true, false, false,false, "namespace/DeterministicFailure", 1, false),
        (postProcessFailure,
          true, false, false, false, "namespace/PostProcessFailure", 1, false),
        (nonDeterministicFailure,
          true, false, false, false, "namespace/NonDeterministicFailure", 1, false)
      )

      forAll(processResults) {
        (testProcess, called, toWorkFail,
         toActionFail, monClientFail, metricName,
         metricCount, empty) => {

          val worker = new MyWorker(testProcess, toWorkFail, toActionFail, monClientFail)
          val process = worker.processMessage("id", message)

          whenReady(process) { _ =>
            worker.process.called shouldBe called

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
