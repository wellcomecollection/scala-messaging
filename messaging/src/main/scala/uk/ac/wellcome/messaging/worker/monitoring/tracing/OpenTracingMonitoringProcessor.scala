package uk.ac.wellcome.messaging.worker.monitoring.tracing

import io.opentracing.contrib.concurrent.TracedExecutionContext
import io.opentracing.{Span, SpanContext, Tracer}
import uk.ac.wellcome.messaging.worker.models.{Result, Successful}
import uk.ac.wellcome.messaging.worker.steps.MonitoringProcessor
import scala.collection.JavaConverters._

import scala.concurrent.{ExecutionContext, Future}

trait ContextCarrier[T]{
  def inject(tracer: Tracer, span: SpanContext): T

  def extract(tracer: Tracer, t:T): SpanContext
}

trait OpenTracingMonitoringProcessor[Work]
    extends MonitoringProcessor[Work, Map[String,String],Span] {
  val namespace: String
  val tracer: Tracer
  val wrappedEc:ExecutionContext
  val carrier: ContextCarrier[Map[String,String]]


  override implicit val ec: ExecutionContext = new TracedExecutionContext(wrappedEc, tracer)

  override def recordStart(work: Either[Throwable, Work],
                           context: Either[Throwable, Option[Map[String,String]]]): Future[Span] = {
    Future {
      val span = context match {
        case Right(None) =>
          val span = tracer.buildSpan(namespace).start()
          span
        case Right(Some(c)) =>
          val rootContext = carrier.extract(tracer, c)
          val span = tracer.buildSpan(namespace).asChildOf(rootContext).start()
          span
        case Left(ex) => val span = tracer.buildSpan(namespace).start()
          span.setTag("error", true)
          span.log(Map("event" -> "error", "error.object" -> ex).asJava)
          span
      }
      work match {
        case Right(_) =>
        case Left(ex) =>
          span.setTag("error", true)
          span.log(Map("event" -> "error", "error.object" -> ex).asJava)
      }
      span

    }
  }

  override def recordEnd[Recorded](context: Span,
                                   result: Result[Recorded]): Future[Result[Unit]] = Future{
    context.finish()
  }.map{_=> Successful(Some(()))}
}