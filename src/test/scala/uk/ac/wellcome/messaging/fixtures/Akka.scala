package uk.ac.wellcome.messaging.fixtures

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.concurrent.Eventually

trait Akka extends Eventually {
  private[messaging] def withMessagingActorSystem[R] = fixture[ActorSystem, R](
    create = ActorSystem(),
    destroy = eventually { _.terminate() }
  )

  private[messaging] def withMessagingMaterializer[R](actorSystem: ActorSystem) =
    fixture[ActorMaterializer, R](
      create = ActorMaterializer()(actorSystem),
      destroy = _.shutdown()
    )
}
