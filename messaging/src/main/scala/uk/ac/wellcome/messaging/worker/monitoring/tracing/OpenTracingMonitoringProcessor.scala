package uk.ac.wellcome.messaging.worker.monitoring.tracing

import io.opentracing.contrib.concurrent.TracedExecutionContext
import io.opentracing.{Span, Tracer}
import uk.ac.wellcome.messaging.worker.models._
import uk.ac.wellcome.messaging.worker.steps.MonitoringProcessor

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class OpenTracingMonitoringProcessor[Work, S](namespace: String)(
  tracer: Tracer,
  wrappedEc: ExecutionContext,
  carrier: ContextCarrier[S])
    extends MonitoringProcessor[Work, S, Span] {

  override implicit val ec: ExecutionContext =
    new TracedExecutionContext(wrappedEc, tracer)

  override def recordStart(
    work: Either[Throwable, Work],
    context: Either[Throwable, Option[S]]): Future[Either[Throwable, Span]] = {
    val f = Future {
      val spanBuilder = tracer.buildSpan(namespace)
      val span = context match {
        case Right(None) =>
          spanBuilder.start()
        case Right(Some(c)) =>
          val rootContext = carrier.extract(tracer, c)
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

  override def recordEnd[Recorded](
    span: Either[Throwable, Span],
    result: Result[Recorded]): Future[Result[Unit]] = {
    val f: Future[Result[Unit]] = Future {
      span.fold(
        throwable => MonitoringProcessorFailure(throwable),
        span => {
          result match {
            case Successful(_) =>
            case f @ DeterministicFailure(failure, _) =>
              tagError(span, failure, f.getClass.getSimpleName)
            case f @ NonDeterministicFailure(failure, _) =>
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
        MonitoringProcessorFailure[Unit](e, None)
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
