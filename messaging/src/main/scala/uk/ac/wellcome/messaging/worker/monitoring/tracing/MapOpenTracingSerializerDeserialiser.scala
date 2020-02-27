package uk.ac.wellcome.messaging.worker.monitoring.tracing

import java.util

import io.opentracing.propagation.{Format, TextMapAdapter}
import io.opentracing.{SpanContext, Tracer}
import uk.ac.wellcome.messaging.worker.monitoring.serialize.{MonitoringContextDeserialiser, MonitoringContextSerialiser}

import scala.collection.JavaConverters._
import scala.util.Try

/**
  * This trait allows to send and receive information
  * about a Span in messages so that we can chain spans
  * together in the same trace
  */
trait MapOpenTracingSerializerDeserialiser
    extends MonitoringContextDeserialiser[
      SpanContext,
      Map[String, String]] with MonitoringContextSerialiser[
      SpanContext,
      Map[String, String]]{
  val tracer: Tracer

  override def serialise(
    monitoringContext: SpanContext): Try[Map[String, String]] = {
    Try {
      val map = new util.HashMap[String, String]()

      tracer.inject(
        monitoringContext,
        Format.Builtin.TEXT_MAP,
        new TextMapAdapter(map))
      map.asScala.toMap
    }
  }

  override def deserialise(t: Option[Map[String, String]]): Try[Option[SpanContext]] =
    Try{t.map{ tt =>
      val spanContext = tracer.extract(Format.Builtin.TEXT_MAP, new TextMapAdapter(tt.asJava))
      if(spanContext == null) {
        throw new RuntimeException("Unable to parse monitoring context")
      }
      spanContext
    }
    }
}
