package uk.ac.wellcome.messaging.worker.monitoring.tracing

import io.opentracing.Span
import io.opentracing.mock.{MockSpan, MockTracer}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Assertion, FunSpec}
import uk.ac.wellcome.messaging.fixtures.monitoring.tracing.TracingFixtures
import uk.ac.wellcome.messaging.fixtures.worker.WorkerFixtures
import uk.ac.wellcome.messaging.worker.models.MonitoringProcessorFailure

import scala.collection.JavaConverters._

class OpenTracingMonitoringProcessorTest
    extends FunSpec
    with WorkerFixtures
    with ScalaFutures
    with TracingFixtures {

  it("opens and finishes a span") {
    val mockTracer = new MockTracer()
    withOpenTracingMetricsProcessor[MyPayload, Assertion](mockTracer) {
      processor =>
        whenReady(processor.recordStart(Right((work, Map.empty)))) {
          span: Either[Throwable, (Span, Map[String, String])] =>
            span shouldBe a[Right[_, _]]
            span.right.get shouldNot be(null)
            whenReady(processor.recordEnd(span, successful(work))) { _ =>
              val finishedSpans = mockTracer.finishedSpans().asScala
              finishedSpans should have size (1)
              finishedSpans.head.tags() shouldBe empty
            }
        }
    }
  }

  it("opens and finishes a span and records a deterministic error in process") {
    val mockTracer = new MockTracer()
    withOpenTracingMetricsProcessor[MyPayload, Assertion](mockTracer) {
      processor =>
        whenReady(processor.recordStart(Right((work, Map.empty)))) {
          spanEither =>
            spanEither shouldBe a[Right[_, _]]
            val span = spanEither.right.get._1
            span shouldNot be(null)

            val deterministicErrorResult = deterministicFailure(work)
            whenReady(processor.recordEnd(spanEither, deterministicErrorResult)) {
              _ =>
                mockTracer.finishedSpans().asScala should have size (1)

                spanShouldBeTaggeddWith(
                  span,
                  deterministicErrorResult.failure,
                  "DeterministicFailure")
            }
        }
    }
  }

  it(
    "opens and finishes a span and records a non deterministic error in process") {
    val mockTracer = new MockTracer()
    withOpenTracingMetricsProcessor[MyPayload, Assertion](mockTracer) {
      processor =>
        whenReady(processor.recordStart(Right((work, Map.empty)))) {
          spanEither =>
            spanEither shouldBe a[Right[_, _]]
            val span = spanEither.right.get._1
            span shouldNot be(null)

            val nonDeterministicErrorResult = nonDeterministicFailure(work)
            whenReady(
              processor.recordEnd(spanEither, nonDeterministicErrorResult)) {
              _ =>
                mockTracer.finishedSpans().asScala should have size (1)
                spanShouldBeTaggeddWith(
                  span,
                  nonDeterministicErrorResult.failure,
                  "NonDeterministicFailure")
            }
        }
    }
  }

  describe("recordStart") {
    it("records an error if it receives a left instead of a work") {
      val mockTracer = new MockTracer()
      withOpenTracingMetricsProcessor[MyPayload, Assertion](mockTracer) {
        processor =>
          val exception = new RuntimeException("BOOM!")
          whenReady(processor.recordStart(Left(exception))) { spanEither =>
            spanEither shouldBe a[Right[_, _]]
            val span = spanEither.right.get._1
            span shouldNot be(null)

            spanShouldBeTaggeddWith(span, exception, "DeterministicFailure")
          }

      }
    }

    it("opens a span as child of another passed as context") {
      val mockTracer = new MockTracer()
      withOpenTracingMetricsProcessor[MyPayload, Assertion](mockTracer) {
        processor =>
          val parentSpan = mockTracer.buildSpan("parent").start()
          val parentTraceId = parentSpan.context().toTraceId
          val parentSpanId = parentSpan.context().toSpanId
          parentSpan.finish()

          whenReady(processor.recordStart(Right((work, Map.empty)))) {
            spanEither =>
              spanEither shouldBe a[Right[_, _]]
              val span = spanEither.right.get._1
              span shouldNot be(null)
              val references = span.asInstanceOf[MockSpan].references().asScala
              references should have size (1)
              references.head.getReferenceType shouldBe "child_of"
              references.head.getContext.toTraceId shouldBe parentTraceId
              references.head.getContext.toSpanId shouldBe parentSpanId

              span.context().toTraceId shouldBe parentTraceId

          }
      }
    }

    it("returns a MonitoringProcessorFailure if it fails") {
      val exception = new RuntimeException("TADAAA")
      val failingTracer = new MockTracer {
        override def buildSpan(
          operationName: MySummary): MockTracer#SpanBuilder = throw exception
      }
      withOpenTracingMetricsProcessor[MyPayload, Assertion](failingTracer) {
        processor =>
          whenReady(processor.recordStart(Right((work, Map.empty)))) {
            spanEither =>
              spanEither shouldBe a[Left[_, _]]
              spanEither.left.get shouldBe exception
          }
      }
    }
  }

  describe("recordEnd") {
    it("returns a monitoring failure if it doesn't receive a span") {
      val exception = new RuntimeException("TADAAA")
      val mockTracer = new MockTracer()
      withOpenTracingMetricsProcessor[MyPayload, Assertion](mockTracer) {
        processor =>
          val nonDeterministicErrorResult = nonDeterministicFailure(work)
          whenReady(
            processor.recordEnd(Left(exception), nonDeterministicErrorResult)) {
            result =>
              result shouldBe a[MonitoringProcessorFailure[_]]
              result
                .asInstanceOf[MonitoringProcessorFailure[_]]
                .failure shouldBe exception
              mockTracer.finishedSpans().asScala should have size (0)
          }
      }

    }

    it("returns a monitoring failure if it fails while closing a span") {
      val mockTracer = new MockTracer()

      val span = mockTracer.buildSpan("parent").start()
      span.finish()
      withOpenTracingMetricsProcessor[MyPayload, Assertion](mockTracer) {
        processor =>
          val nonDeterministicErrorResult = nonDeterministicFailure(work)
          whenReady(processor
            .recordEnd(Right((span, Map.empty)), nonDeterministicErrorResult)) {
            result =>
              result shouldBe a[MonitoringProcessorFailure[_]]
          }
      }

    }
  }
}
