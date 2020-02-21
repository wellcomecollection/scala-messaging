package uk.ac.wellcome.messaging.worker.monitoring.tracing

import io.opentracing.contrib.concurrent.TracedExecutionContext
import io.opentracing.{Span, Tracer}
import uk.ac.wellcome.messaging.worker.models._
import uk.ac.wellcome.messaging.worker.steps.MonitoringProcessor

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Implements the [[MonitoringProcessor]] interface with Opentracing (https://opentracing.io/).
  */
class OpenTracingMonitoringProcessor[Work, InfraServiceMonitoringContext](
  namespace: String)(
  tracer: Tracer,
  wrappedEc: ExecutionContext,
  carrier: OpenTracingSpanSerializer[InfraServiceMonitoringContext])
    extends MonitoringProcessor[Work, InfraServiceMonitoringContext, Span] {

  override implicit val ec: ExecutionContext =
    new TracedExecutionContext(wrappedEc, tracer)

  /**
    * Starts a [[Span]] and returns it.
    * If an optional [[context]] is passed, it uses a [[OpenTracingSpanSerializer]]
    * to deserialise it into a [[io.opentracing.SpanContext]] and link it to the new [[Span]]
    */
  override def recordStart(
    work: Either[Throwable, Work],
    context: Either[Throwable, Option[InfraServiceMonitoringContext]])
    : Future[Either[Throwable, Span]] = {
    val f = Future {
      val spanBuilder = tracer.buildSpan(namespace)
      val span = context match {
        case Right(None) =>
          spanBuilder.start()
        case Right(Some(c)) =>
          val rootContext = carrier.deserialise(tracer, c)
          spanBuilder.asChildOf(rootContext).start()
        case Left(ex) =>
          val span = spanBuilder.start()
          tagError(span, ex, classOf[DeterministicFailure[_]].getSimpleName)
      }
      work match {
        case Right(_) =>
        case Left(ex) =>
          tagError(span, ex, classOf[DeterministicFailure[_]].getSimpleName)
      }
      Right(span)
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
    span: Either[Throwable, Span],
    result: Result[Recorded]): Future[Result[Unit]] = {
    val f: Future[Result[Unit]] = Future {
      span.fold(throwable => MonitoringProcessorFailure(throwable), span => {
        result match {
          case Successful(_) =>
          case f@DeterministicFailure(failure) =>
            tagError(span, failure, f.getClass.getSimpleName)
          case f@NonDeterministicFailure(failure) =>
            tagError(span, failure, f.getClass.getSimpleName)
          case _ =>
        }
        span.finish()
        Successful[Unit](None)
      }
      )
    }
    f recover { case e =>
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
}
