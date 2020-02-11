# scala-messaging

[![Build Status](https://travis-ci.org/wellcomecollection/scala-messaging.svg?branch=master)](https://travis-ci.org/wellcomecollection/scala-messaging)

Messaging libraries in use at Wellcome including:

-   `SQSStream`: An Akka streams-backed class for processing messages from an SQS queue.
-   `SNSWriter`: A strongly typed client that allows sending notifications to SNS.
-   `MessageStream` and associated pieces: a messaging stack that allows publishing via SNS/SQS, or storing pointers to objects in S3 if the messages are too large.

These libraries are used as part of the [Wellcome Digital Platform][platform].

[platform]: https://github.com/wellcomecollection/platform


## Installation

```scala
libraryDependencies ++= Seq(
  "uk.ac.wellcome" %% "messaging" % "<LATEST_VERSION>"
)
```

`messaging` is published for Scala 2.11 and Scala 2.12.

Read [the changelog](CHANGELOG.md) to find the latest version.
