package uk.ac.wellcome.messaging

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.messaging.memory.{
  MemoryIndividualMessageSender,
  MemoryMessageSender
}

import scala.util.Success

class MessageSenderTest extends FunSpec with Matchers with JsonAssertions {
  it("sends individual messages") {
    val sender = new MemoryIndividualMessageSender()

    sender.send("hello world", None)(
      subject = "my first message",
      destination = "greetings") shouldBe Success(())
    sender.send("guten tag", None)(
      subject = "auf deutsch",
      destination = "greetings") shouldBe Success(())
    sender.send("你好", None)(subject = "中文", destination = "greetings") shouldBe Success(
      ())
    sender.send("chinese", None)(
      subject = "a non-alphabet language",
      destination = "languages") shouldBe Success(())

    sender.messages shouldBe List(
      sender.MemoryMessage("hello world", "my first message", "greetings"),
      sender.MemoryMessage("guten tag", "auf deutsch", "greetings"),
      sender.MemoryMessage("你好", "中文", "greetings"),
      sender.MemoryMessage("chinese", "a non-alphabet language", "languages")
    )
  }

  it("encodes case classes as JSON") {
    case class Animal(name: String, legs: Int)

    val sender = new MemoryIndividualMessageSender()

    val dog = Animal(name = "dog", legs = 4)
    val octopus = Animal(name = "octopus", legs = 8)
    val snake = Animal(name = "snake", legs = 0)

    Seq(dog, octopus, snake).map { animal =>
      sender.sendT(animal, None)(
        subject = "animals",
        destination = "all creatures") shouldBe Success(())
    }

    Seq(dog, octopus, snake).zip(sender.messages).map {
      case (animal, message) =>
        assertJsonStringsAreEqual(toJson(animal).get, message.body)
    }
  }

  sealed trait Container {}

  case class Box(sides: Int) extends Container
  case class Bottle(height: Int) extends Container

  val containers = Seq(Box(sides = 3), Box(sides = 4), Bottle(height = 5))

  it("encodes case classes using the type parameter") {
    val sender = new MemoryIndividualMessageSender()

    containers.map { c =>
      sender.sendT[Container](c, None)(
        destination = "containers",
        subject = "stuff to store things in") shouldBe Success(())
    }

    containers.zip(sender.messages).map {
      case (container, message) =>
        fromJson[Container](message.body).get shouldBe container
    }
  }

  it("sends messages to a default destination/subject") {
    val sender = new MemoryMessageSender() {
      override val destination = "colours"
      override val subject = "ideas for my design"
    }

    sender.send("red", None) shouldBe Success(())
    sender.send("yellow", None) shouldBe Success(())
    sender.send("green", None) shouldBe Success(())
    sender.send("blue", None) shouldBe Success(())

    sender.messages.map { _.destination } shouldBe Seq(
      "colours",
      "colours",
      "colours",
      "colours")
    sender.messages.map { _.subject } shouldBe Seq(
      "ideas for my design",
      "ideas for my design",
      "ideas for my design",
      "ideas for my design")
  }

  it("sends case classes to a default destination/subject") {
    val sender = new MemoryMessageSender() {
      override val destination = "trees"
      override val subject = "ideas for my garden"
    }

    case class Tree(name: String)

    sender.sendT(Tree("oak"), None) shouldBe Success(())
    sender.sendT(Tree("ash"), None) shouldBe Success(())
    sender.sendT(Tree("yew"), None) shouldBe Success(())

    sender.messages.map { _.destination } shouldBe Seq(
      "trees",
      "trees",
      "trees")
    sender.messages.map { _.subject } shouldBe Seq(
      "ideas for my garden",
      "ideas for my garden",
      "ideas for my garden")
  }

  it(
    "sends type-parameter encoded case classes to a default destination/subject") {
    val sender = new MemoryMessageSender()

    containers.map { c =>
      sender.sendT[Container](c, None) shouldBe Success(())
    }

    containers.zip(sender.messages).map {
      case (container, message) =>
        fromJson[Container](message.body).get shouldBe container
    }
  }

}
