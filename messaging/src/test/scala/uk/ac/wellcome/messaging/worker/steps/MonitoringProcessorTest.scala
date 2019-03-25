package uk.ac.wellcome.messaging.worker.steps

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables._
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.messaging.fixtures.worker.{MetricsFixtures, WorkerFixtures}
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture

import scala.concurrent.ExecutionContext.Implicits._

class MonitoringProcessorTest extends FunSpec
  with Matchers
  with Akka
  with ScalaFutures
  with IntegrationPatience
  with WorkerFixtures
  with MetricsFixtures
  with MetricsSenderFixture {

  val successMetric = "namespace/Successful"
  val dFailMetric = "namespace/DeterministicFailure"
  val nonDFailMetric = "namespace/NonDeterministicFailure"

  val postProcessActions = Table(
    ("result", "metricName", "count", "noMetric", "monClientFail", "isA"),
    (successful(work), successMetric, 1, false, false, shouldBeSuccessful),
    (successful(work), successMetric, 1, false, false, shouldBeSuccessful),
    (successful(work), "noMetric", -1, true, true, shouldBeMonitoringProcessorFailure),

    (deterministicFailure(work), dFailMetric, 1, false, false, shouldBeSuccessful),
    (nonDeterministicFailure(work), nonDFailMetric, 1, false, false, shouldBeSuccessful)
  )

  describe("when a monitoring process runs") {
    it("performs the correct monitoring functions") {
      forAll(postProcessActions) {
        (result, metricName, count, noMetric, monClientFail, checkType) =>

        val processor =
          new MyMonitoringProcessor(result, false, monClientFail)

          val metrics = processor.monitoringClient
        val futureResult = processor.record(result)

        whenReady(futureResult) { action =>
          checkType(action)

          assertMetricCount(metrics, metricName, count, noMetric)
          assertMetricDurations(metrics, "namespace/Duration", count, noMetric)
        }
      }
    }
  }
}

