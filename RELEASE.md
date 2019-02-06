RELEASE_TYPE: patch

This fixes a small flakiness in `SNS.notificationMessage[T](topic)`, where it
would occasionally fail an assert if a duplicate message was sent twice.