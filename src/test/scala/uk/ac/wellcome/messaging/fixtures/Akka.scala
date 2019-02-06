package uk.ac.wellcome.messaging.fixtures

import akka.actor.ActorSystem
import org.scalatest.concurrent.Eventually
import uk.ac.wellcome.fixtures.fixture

private[messaging] trait Akka extends Eventually {
  private[messaging] def withMessagingActorSystem[R] = fixture[ActorSystem, R](
    create = ActorSystem(),
    destroy = eventually { _.terminate() }
  )
}
