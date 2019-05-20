package uk.ac.wellcome.messaging

import org.scalatest.{FunSpec, Matchers}

class MessageSenderTest extends FunSpec with Matchers {
  describe("individual messages") {
    it("sends messages to the destination") {
      val sender = new MemoryIn
    }
  }
}
