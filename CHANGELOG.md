# CHANGELOG

## v1.5.0 - 2019-02-25

This release removes the need to pass an `s3Client` to `SQS.createHybridRecordWith`
and `SQS.createHybridRecordNotificationWith`.

## v1.4.0 - 2019-02-25

This release adds a `namespace` parameter to `SNSBuilder.buildSNSWriter`.

## v1.3.0 - 2019-02-22

This release adds a new class, `NotificationStream[T]`, which does the work of
wrapping an `SQSStream[NotificationMessage]`, reading those messages from the
queue and unpacking them as instances of the case class `T` – hiding the mucky
JSON decoding from the worker services, which just get handed lovely case classes.

## v1.2.0 - 2019-02-08

This release adds the `messaging_typesafe` library for configuring the `messaging` library using Typesafe.

## v1.1.2 - 2019-02-06

Start using the scala-fixtures lib rather than vendoring fixtures.

## v1.1.1 - 2019-02-06

This fixes a small flakiness in `SNS.notificationMessage[T](topic)`, where it
would occasionally fail an assert if a duplicate message was sent twice.

## v1.1.0 - 2019-01-10

Bump the version of scala-monitoring to 1.2.0.

## v1.0.0 - 2018-12-10

First release of the messaging code in the platform repo.

## v0.2.0 - 2018-12-08

Bump the version of scala-storage to 3.1.0.

## v0.1.0 - 2018-12-07

Bump the version of scala-json to 1.1.1.

## v0.0.3 - 2018-12-07

Internal refactoring to simplify the SQS fixtures.

## v0.0.2 - 2018-12-07

Rename the `withActorSystem` method used internally for scala-messaging tests.

## v0.0.1 - 2018-12-07

Initial release of scala-messaging!
