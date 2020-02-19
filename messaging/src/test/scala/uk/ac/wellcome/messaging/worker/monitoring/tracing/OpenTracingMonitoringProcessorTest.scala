package uk.ac.wellcome.messaging.worker.monitoring.tracing

import io.opentracing.mock.{MockSpan, MockTracer}
import io.opentracing.{Span, SpanContext}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Assertion, FunSpec}
import uk.ac.wellcome.messaging.fixtures.monitoring.tracing.TracingFixtures
import uk.ac.wellcome.messaging.fixtures.worker.WorkerFixtures
import uk.ac.wellcome.messaging.worker.models.MonitoringProcessorFailure

import scala.collection.JavaConverters._

case class Bu(span: Span, rootSpanContext: Option[SpanContext])


class OpenTracingMonitoringProcessorTest extends FunSpec with WorkerFixtures with ScalaFutures with TracingFixtures{

  it("opens and finishes a span") {
    val mockTracer = new MockTracer()
    withOpenTracingMetricsProcessor[MyWork, Assertion](textMapCarrier, mockTracer) { processor =>

      whenReady(processor.recordStart(Right(work), Right(None))) { span: Either[Throwable, Span] =>
        span shouldBe a[Right[_,_]]
        span.right.get shouldNot be(null)
        whenReady(processor.recordEnd(span, successful(work))) { _ =>
          val f = mockTracer.finishedSpans().asScala
          f should have size (1)
          f.head.tags() shouldBe empty
        }
      }
    }
  }

  it("opens and finishes a span and records a deterministic error in process") {
    val mockTracer = new MockTracer()
    withOpenTracingMetricsProcessor[MyWork, Assertion](textMapCarrier, mockTracer) { processor =>

      whenReady(processor.recordStart(Right(work), Right(None))) { spanEither =>
        spanEither shouldBe a[Right[_,_]]
        val span = spanEither.right.get
        span shouldNot be(null)

        val deterministicErrorResult = deterministicFailure(work)
        whenReady(processor.recordEnd(spanEither, deterministicErrorResult)) { _ =>
          val f = mockTracer.finishedSpans().asScala
          f should have size (1)
          f.head.tags().asScala.toMap should contain only (("error" -> true),"error.type" -> "DeterministicFailure")
          val logEntries = span.asInstanceOf[MockSpan].logEntries().asScala.map(_.fields().asScala.toMap)
          logEntries should contain only (Map(("event" -> "error"), ("error.object" -> deterministicErrorResult.failure)))
        }
      }
    }
  }

  it("opens and finishes a span and records a non deterministic error in process") {
    val mockTracer = new MockTracer()
    withOpenTracingMetricsProcessor[MyWork, Assertion](textMapCarrier, mockTracer) { processor =>

      whenReady(processor.recordStart(Right(work), Right(None))) { spanEither =>
        spanEither shouldBe a[Right[_,_]]
        val span = spanEither.right.get
        span shouldNot be (null)

        val nonDeterministicErrorResult = nonDeterministicFailure(work)
        whenReady(processor.recordEnd(spanEither, nonDeterministicErrorResult)) { _ =>
          val f = mockTracer.finishedSpans().asScala
          f should have size (1)
          f.head.tags().asScala.toMap should contain only (("error" -> true),"error.type" -> "NonDeterministicFailure")
          val logEntries = span.asInstanceOf[MockSpan].logEntries().asScala.map(_.fields().asScala.toMap)
          logEntries should contain only (Map(("event" -> "error"), ("error.object" -> nonDeterministicErrorResult.failure)))
        }
      }
    }
  }

  describe("recordStart") {
    it("records an error if it receives a left instead of a work") {
      val mockTracer = new MockTracer()
      withOpenTracingMetricsProcessor[MyWork, Assertion](textMapCarrier, mockTracer) { processor =>
        val exception = new RuntimeException("BOOM!")
        whenReady(processor.recordStart(Left(exception), Right(None))) { spanEither =>
          spanEither shouldBe a[Right[_,_]]
          val span = spanEither.right.get
          span shouldNot be (null)
          val tags = span.asInstanceOf[MockSpan].tags().asScala.toMap
          tags should contain only (("error" -> true),"error.type" -> "DeterministicFailure")
          val logEntries = span.asInstanceOf[MockSpan].logEntries().asScala.map(_.fields().asScala.toMap)
          logEntries should contain only (Map(("event" -> "error"), ("error.object" -> exception)))
        }

      }
    }

    it("opens a span as child of another passed as context") {
      val mockTracer = new MockTracer()
      withOpenTracingMetricsProcessor[MyWork, Assertion](textMapCarrier, mockTracer) { processor =>

        val parentSpan = mockTracer.buildSpan("parent").start()
        val parentTraceId = parentSpan.context().toTraceId
        val parentSpanId = parentSpan.context().toSpanId
        val context = textMapCarrier.inject(mockTracer, parentSpan.context())
        parentSpan.finish()

        whenReady(processor.recordStart(Right(work), Right(Some(context)))) { spanEither =>

          spanEither shouldBe a[Right[_,_]]
          val span = spanEither.right.get
          span shouldNot be (null)
          val references = span.asInstanceOf[MockSpan].references().asScala
          references should have size (1)
          references.head.getReferenceType shouldBe "child_of"
          references.head.getContext.toTraceId shouldBe parentTraceId
          references.head.getContext.toSpanId shouldBe parentSpanId

          span.context().toTraceId shouldBe parentTraceId

        }
      }
    }

    it("records an error if it receives a left instead of a parent span") {
      val mockTracer = new MockTracer()
      withOpenTracingMetricsProcessor[MyWork, Assertion](textMapCarrier, mockTracer) { processor =>


        val exception = new RuntimeException("AAAARGH!")
        whenReady(processor.recordStart(Right(work), Left(exception))) { spanEither =>

          spanEither shouldBe a[Right[_,_]]
          val span = spanEither.right.get
          span shouldNot be (null)
          val references = span.asInstanceOf[MockSpan].references().asScala
          references shouldBe empty
          val tags = span.asInstanceOf[MockSpan].tags().asScala.toMap
          tags should contain only (("error" -> true),"error.type" -> "DeterministicFailure")
          val logEntries = span.asInstanceOf[MockSpan].logEntries().asScala.map(_.fields().asScala.toMap)
          logEntries should contain only (Map(("event" -> "error"), ("error.object" -> exception)))


        }
      }
    }
    it("returns a MonitoringProcessorFailure if it fails"){
      val exception = new RuntimeException("TADAAA")
      val failingTracer = new MockTracer{
        override def buildSpan(operationName: MySummary): MockTracer#SpanBuilder = throw exception
      }
      withOpenTracingMetricsProcessor[MyWork, Assertion](textMapCarrier, failingTracer) { processor =>
        whenReady(processor.recordStart(Right(work), Right(None))) { spanEither =>
          spanEither shouldBe a[Left[_,_]]
          spanEither.left.get shouldBe exception
        }
      }
    }
  }

  describe("recordEnd"){
    it("returns a monitoring failure if it doesn't receive a span") {
      val exception = new RuntimeException("TADAAA")
      val mockTracer = new MockTracer()
      withOpenTracingMetricsProcessor[MyWork, Assertion](textMapCarrier, mockTracer) { processor =>


          val nonDeterministicErrorResult = nonDeterministicFailure(work)
          whenReady(processor.recordEnd(Left(exception), nonDeterministicErrorResult)) { result =>
            result shouldBe a[MonitoringProcessorFailure[_]]
            result.asInstanceOf[MonitoringProcessorFailure[_]].failure shouldBe exception
            mockTracer.finishedSpans().asScala should have size (0)
          }
        }

    }

    it("returns a monitoring failure if it fails while closing a span") {
      val mockTracer = new MockTracer()

      val span = mockTracer.buildSpan("parent").start()
      span.finish()
      withOpenTracingMetricsProcessor[MyWork, Assertion](textMapCarrier, mockTracer) { processor =>


          val nonDeterministicErrorResult = nonDeterministicFailure(work)
          whenReady(processor.recordEnd(Right(span), nonDeterministicErrorResult)) { result =>
            result shouldBe a[MonitoringProcessorFailure[_]]
          }
        }

    }
  }
}
