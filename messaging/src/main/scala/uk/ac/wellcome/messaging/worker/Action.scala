package uk.ac.wellcome.messaging.worker

sealed trait Action

trait Completed extends Action
trait Retry extends Action
