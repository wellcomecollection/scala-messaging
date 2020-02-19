package uk.ac.wellcome.messaging.worker.monitoring.tracing

import io.opentracing.mock.MockSpan
import io.opentracing.{Span, SpanContext}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Assertion, FunSpec}
import uk.ac.wellcome.messaging.fixtures.monitoring.tracing.TracingFixtures
import uk.ac.wellcome.messaging.fixtures.worker.WorkerFixtures

import scala.collection.JavaConverters._

case class Bu(span: Span, rootSpanContext: Option[SpanContext])


class OpenTracingMonitoringProcessorTest extends FunSpec with WorkerFixtures with ScalaFutures with TracingFixtures{

  it("opens and finishes a span") {
    withOpenTracingMetricsProcessor[MyWork, Assertion](textMapCarrier) { case (mockTracer, processor) =>

      whenReady(processor.recordStart(Right(work), Right(None))) { c: Span =>
        c shouldNot be(null)

        whenReady(processor.recordEnd(c, successful(work))) { _ =>
          val f = mockTracer.finishedSpans().asScala
          f should have size (1)
          f.head.tags() shouldBe empty
        }
      }
    }
  }

  it("opens and finishes a span and records a deterministic error in process") {
    withOpenTracingMetricsProcessor[MyWork, Assertion](textMapCarrier) { case (mockTracer, processor) =>

      whenReady(processor.recordStart(Right(work), Right(None))) { c: Span =>
        c shouldNot be(null)

        val deterministicErrorResult = deterministicFailure(work)
        whenReady(processor.recordEnd(c, deterministicErrorResult)) { _ =>
          val f = mockTracer.finishedSpans().asScala
          f should have size (1)
          f.head.tags().asScala.toMap should contain only (("error" -> true),"error.type" -> "DeterministicFailure")
          val logEntries = c.asInstanceOf[MockSpan].logEntries().asScala.map(_.fields().asScala.toMap)
          logEntries should contain only (Map(("event" -> "error"), ("error.object" -> deterministicErrorResult.failure)))
        }
      }
    }
  }

  it("opens and finishes a span and records a non deterministic error in process") {
    withOpenTracingMetricsProcessor[MyWork, Assertion](textMapCarrier) { case (mockTracer, processor) =>

      whenReady(processor.recordStart(Right(work), Right(None))) { c: Span =>
        c shouldNot be(null)

        val nonDeterministicErrorResult = nonDeterministicFailure(work)
        whenReady(processor.recordEnd(c, nonDeterministicErrorResult)) { _ =>
          val f = mockTracer.finishedSpans().asScala
          f should have size (1)
          f.head.tags().asScala.toMap should contain only (("error" -> true),"error.type" -> "NonDeterministicFailure")
          val logEntries = c.asInstanceOf[MockSpan].logEntries().asScala.map(_.fields().asScala.toMap)
          logEntries should contain only (Map(("event" -> "error"), ("error.object" -> nonDeterministicErrorResult.failure)))
        }
      }
    }
  }

  describe("recordStart") {
    it("records an error if it receives a left instead of a work") {
      withOpenTracingMetricsProcessor[MyWork, Assertion](textMapCarrier) { case (_, processor) =>

        val exception = new RuntimeException("BOOM!")
        whenReady(processor.recordStart(Left(exception), Right(None))) { c: Span =>
          c shouldNot be(null)
          val tags = c.asInstanceOf[MockSpan].tags().asScala.toMap
          tags should contain only ("error" -> true)
          val logEntries = c.asInstanceOf[MockSpan].logEntries().asScala.map(_.fields().asScala.toMap)
          logEntries should contain only (Map(("event" -> "error"), ("error.object" -> exception)))
        }

      }
    }

    it("opens a span as child of another passed as context") {
      withOpenTracingMetricsProcessor[MyWork, Assertion](textMapCarrier) { case (mockTracer, processor) =>

        val parentSpan = mockTracer.buildSpan("parent").start()
        val parentTraceId = parentSpan.context().toTraceId
        val parentSpanId = parentSpan.context().toSpanId
        val context = textMapCarrier.inject(mockTracer, parentSpan.context())
        parentSpan.finish()

        whenReady(processor.recordStart(Right(work), Right(Some(context)))) { s: Span =>

          s shouldNot be(null)
          val references = s.asInstanceOf[MockSpan].references().asScala
          references should have size (1)
          references.head.getReferenceType shouldBe "child_of"
          references.head.getContext.toTraceId shouldBe parentTraceId
          references.head.getContext.toSpanId shouldBe parentSpanId

          s.context().toTraceId shouldBe parentTraceId

        }
      }
    }

    it("records an error if it receives a left instead of a parent span") {
      withOpenTracingMetricsProcessor[MyWork, Assertion](textMapCarrier) { case (mockTracer, processor) =>


        val exception = new RuntimeException("AAAARGH!")
        whenReady(processor.recordStart(Right(work), Left(exception))) { s: Span =>

          s shouldNot be(null)
          val references = s.asInstanceOf[MockSpan].references().asScala
          references shouldBe empty
          val tags = s.asInstanceOf[MockSpan].tags().asScala.toMap
          tags should contain only ("error" -> true)
          val logEntries = s.asInstanceOf[MockSpan].logEntries().asScala.map(_.fields().asScala.toMap)
          logEntries should contain only (Map(("event" -> "error"), ("error.object" -> exception)))


        }
      }
    }
  }
}
