package uk.ac.wellcome.messaging.worker.monitoring.tracing

import java.util

import io.opentracing.contrib.concurrent.TracedExecutionContext
import io.opentracing.propagation.{Format, TextMapAdapter}
import io.opentracing.{Span, SpanContext, Tracer}
import uk.ac.wellcome.messaging.worker.models._
import uk.ac.wellcome.messaging.worker.steps.MonitoringProcessor

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try


class OpenTracingMonitoringProcessor[Payload](namespace: String)(
  val tracer: Tracer,
  wrappedEc: ExecutionContext)
    extends MonitoringProcessor[Payload,  Map[String, String], Span] {

  override implicit val ec: ExecutionContext =
    new TracedExecutionContext(wrappedEc, tracer)

  /**
    * Starts a [[Span]] and returns it.
    * If an optional [[context]] is passed, it uses a [[MonitoringContextSerializerDeserialiser]]
    * to deserialise it into a [[io.opentracing.SpanContext]] and link it to the new [[Span]]
    */
  override def recordStart(work: Either[Throwable, Payload],
                           context: Either[Throwable, Map[String, String]])
    : Future[Either[Throwable, (Span, Map[String, String])]] = {
    val f = Future {
      val spanBuilder = tracer.buildSpan(namespace)
      val span = context match {
        case Right(map) if map.isEmpty =>
          spanBuilder.start()
        case Right(map) =>
          val rootSpanContext = deserialise(map)
          spanBuilder.asChildOf(rootSpanContext.get).start()
        case Left(ex) =>
          val span = spanBuilder.start()
          tagError(span, ex, classOf[DeterministicFailure[_]].getSimpleName)
      }
      work match {
        case Right(_) =>
        case Left(ex) =>
          tagError(span, ex, classOf[DeterministicFailure[_]].getSimpleName)
      }
      Right((span, serialise(span).get))
    }
    f recover {
      case e =>
        Left(e)
    }
  }

  /**
    * Receives a [[Span]] and calls [[Span.finish]]. It tags the [[Span]] as
    * errored based on the type of [[result]]
    */
  override def recordEnd[Recorded](
    span: Either[Throwable, (Span, Map[String, String])],
    result: Result[Recorded]): Future[Result[Unit]] = {
    val f: Future[Result[Unit]] = Future {
      span.fold(
        throwable => MonitoringProcessorFailure(throwable),
        { case (span, _) =>
          result match {
            case Successful(_) =>
            case f @ DeterministicFailure(failure) =>
              tagError(span, failure, f.getClass.getSimpleName)
            case f @ NonDeterministicFailure(failure) =>
              tagError(span, failure, f.getClass.getSimpleName)
            case _ =>
          }
          span.finish()
          Successful[Unit](None)
        }
      )
    }
    f recover {
      case e =>
        MonitoringProcessorFailure[Unit](e)
    }
  }

  private def tagError[Recorded](span: Span,
                                 failure: Throwable,
                                 errorType: String) = {
    span.setTag("error", true)
    span.setTag("error.type", errorType)
    span.log(Map("event" -> "error", "error.object" -> failure).asJava)
  }

  private def serialise(
                 span: Span): Try[Map[String, String]] = {
    Try {
      val map = new util.HashMap[String, String]()

      tracer.inject(
        span.context(),
        Format.Builtin.TEXT_MAP,
        new TextMapAdapter(map))
      map.asScala.toMap

    }
  }

  private def deserialise(t: Map[String, String]): Try[SpanContext] =
    Try{

      val spanContext = tracer.extract(Format.Builtin.TEXT_MAP, new TextMapAdapter(t.asJava))
      if(spanContext == null) {
        throw new RuntimeException("Unable to parse monitoring context")
      }
      spanContext

    }
}
