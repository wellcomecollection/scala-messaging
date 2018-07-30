# scala-messaging

[![Build Status](https://travis-ci.org/wellcometrust/scala-messaging.svg?branch=master)](https://travis-ci.org/wellcometrust/scala-messaging)

Messaging libraries in use at Wellcome:

-   `SQSStream`: An Akka stream for processing messages from SQS.
-   `SNSWriter`: A class for publishing notifications to SNS.
-   `MessageStream` and `MessageWriter`: classes for sending large messages
    via SNS/SQS by saving the body to S3, and passing around the pointer.

This library is used as part of the [Wellcome Digital Platform][platform].

[platform]: https://github.com/wellcometrust/platform

## Installation

This library is only published to a private S3 bucket.

Wellcome projects have access to this S3 bucket -- you can use our build
scripts to publish a copy to your own private package repository, or vendor
the library by copying the code into your own repository.

Read [the changelog](CHANGELOG.md) to find the latest version.
