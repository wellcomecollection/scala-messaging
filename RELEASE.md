RELEASE_TYPE: minor

This release changes how some of the SQS helpers work.

*   `withLocalSqsQueueAndDlq` and `withLocalSqsQueueAndDlqAndTimeout` are both renamed to `withLocalSqsQueuePair()`, which takes an optional `visibilityTimeout` argument.

    The DLQ name will also be the name of the main queue suffixed with `-dlq` for easier debugging.

*   `withLocalSqsQueue` now takes an optional `visibilityTimeout` argument.

    If you are using it without arguments, e.g.:

    ```scala
    withLocalSqsQueue { queue =>
      // do stuff with queue
    }
    ```

    you need to add empty parentheses:

    ```scala
    withLocalSqsQueue() { queue =>
      // do stuff with queue
    }
    ```

*   The `SQS.Queue` case class now includes the `visibilityTimeout`.

*   The following helpers have been made private or removed, because they weren't in use in the catalogue or storage-service repos:

    -   `assertQueueNotEmpty()`
    -   `assertQueuePairSizes()`
    -   `waitVisibilityTimeoutExipiry()`
