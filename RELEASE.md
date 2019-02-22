RELEASE_TYPE: minor

This release adds a new class, `NotificationStream[T]`, which does the work of
wrapping an `SQSStream[NotificationMessage]`, reading those messages from the
queue and unpacking them as instances of the case class `T` – hiding the mucky
JSON decoding from the worker services, which just get handed lovely case classes.
