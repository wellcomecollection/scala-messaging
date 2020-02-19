package uk.ac.wellcome.messaging.fixtures.monitoring.tracing

import java.util

import io.opentracing.{SpanContext, Tracer}
import io.opentracing.mock.MockTracer
import io.opentracing.propagation.{Format, TextMapAdapter}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.worker.monitoring.tracing.{ContextCarrier, OpenTracingMonitoringProcessor}

import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._

trait TracingFixtures {

  val textMapCarrier = new ContextCarrier[Map[String,String]] {
    override def inject(tracer: Tracer, span: SpanContext): Map[String, String] = {
      val map = new util.HashMap[String, String]()
      tracer.inject(span, Format.Builtin.TEXT_MAP, new TextMapAdapter(map))
      map.asScala.toMap
    }

    override def extract(tracer: Tracer, t: Map[String, String]): SpanContext = tracer.extract(Format.Builtin.TEXT_MAP, new TextMapAdapter(t.asJava))
  }

  def withOpenTracingMetricsProcessor[MyWork,R](textMapCarrier: ContextCarrier[Map[String, String]])(testWith:TestWith[(MockTracer,OpenTracingMonitoringProcessor[MyWork]),R]): R ={
    val mockTracer = new MockTracer()

    val processor = new OpenTracingMonitoringProcessor[MyWork] {
      override lazy val tracer: Tracer = mockTracer
      override lazy val wrappedEc: ExecutionContext = ExecutionContext.Implicits.global
      override lazy val namespace: String = "namespace"
      override lazy val carrier: ContextCarrier[Map[String, String]] = textMapCarrier
    }
    testWith((mockTracer, processor))
  }
}
