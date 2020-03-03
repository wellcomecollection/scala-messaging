package uk.ac.wellcome.messaging.sns

import io.circe.generic.extras.JsonKey

case class NotificationMessage(
  @JsonKey("Message") body: String,
  MessageAttributes: Map[String, AttributeValue] = Map.empty
)
case class AttributeValue(
  Type: String,
  Value: String
)
