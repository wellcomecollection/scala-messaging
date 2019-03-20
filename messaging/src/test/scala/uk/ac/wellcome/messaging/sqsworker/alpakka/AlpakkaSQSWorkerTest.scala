package uk.ac.wellcome.messaging.sqsworker.alpakka

import grizzled.slf4j.Logging
import org.scalatest.{FunSpec, Matchers}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.messaging.fixtures.Messaging
import uk.ac.wellcome.messaging.worker._
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture
import uk.ac.wellcome.json.JsonUtil._

import scala.concurrent.{ExecutionContext, Future}

class AlpakkaSQSWorkerTest   extends FunSpec
  with Matchers
  with Messaging
  with Akka
  with ScalaFutures
  with IntegrationPatience
  with MetricsSenderFixture {
  case class MyWork(s: String)

  class MyProcess()
    extends WorkerProcess[MyWork, Option[String]] {
    def run(in: MyWork)(implicit ec: ExecutionContext) = {
      info(s"MyProcess run $in")

      val waitTimeMillis = (math.random * 100).toInt
      //math.random
      0.6 match {
        case n if n < 0.25 => Future {
          Thread.sleep(waitTimeMillis)
          Successful(
            in.toString,
            Some("Summary Successful")
          )
        }
        case n if n >= 0.25 && n < 0.5 => Future {
          Thread.sleep(waitTimeMillis)

          NonDeterministicFailure(
            in.toString,
            new RuntimeException("NonDeterministicFailure"),
            Some("Summary NonDeterministicFailure")
          )
        }
        case n if n >= 0.5 && n <=0.75 => Future {
          Thread.sleep(waitTimeMillis)

          DeterministicFailure(
            in.toString,
            new RuntimeException("DeterministicFailure"),
            Some("Summary DeterministicFailure")
          )
        }
        case n if n > 0.75 => {
          Thread.sleep(waitTimeMillis)

          Future.failed(new RuntimeException("BOOM"))
        }
      }
    }
  }

  class MyMonitoringClient extends MonitoringClient with Logging{
    override def incrementCount(metricName: String)(implicit ec: ExecutionContext): Future[Unit] = Future {
      info(s"MyMonitoringClient incrementing $metricName")

      //      if(math.random > 0.9) {
      //        info(s"Throwing error doing incrementCount")
      //        throw new RuntimeException("MyMessageClient Error!")
      //      }
    }

    override def recordValue(metricName: String, value: Double)(implicit ec: ExecutionContext): Future[Unit] = Future {
      info(s"MyMonitoringClient recordValue $metricName: $value")

      //      if(math.random > 0.9) {
      //        info(s"Throwing error doing recordValue")
      //        throw new RuntimeException("MyMessageClient Error!")
      //      }
    }
  }

  it("fails") {
    withLocalSqsQueue { queue =>
      withActorSystem { actorSytem =>
        implicit val _m = new MyMonitoringClient()
        implicit val _s = asyncSqsClient
        implicit val _a = actorSytem

        val config = AlpakkaSQSWorkerConfig("namespace", queue.url)
        val process = new MyProcess()

        val worker =
          new AlpakkaSQSWorker[MyMonitoringClient, MyWork, Option[String], MyProcess](
            config
          )(process)

        worker.start

        val work = MyWork("whoop")

        //(1 to 100).foreach(_=>
        sendNotificationToSQS[MyWork](queue, work)
        //)

        eventually {

          true shouldBe false
        }
      }
    }
  }
}
