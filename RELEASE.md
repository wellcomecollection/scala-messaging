RELEASE_TYPE: minor

This release adds a new trait `BetterMessageSender`, with implementations `BetterMemoryMessageSender` and `BetterSNSMessageSender`.

You should move to these traits/implementations -- they're easier to test and work with, and the old ones will eventually go away!