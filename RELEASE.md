RELEASE_TYPE: minor

Remove all the hybrid S3/SNS messaging code from this repo, and move it to the catalogue repo.

This breaks the dependency between scala-messaging and scala-storage.