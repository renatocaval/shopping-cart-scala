package com.example.shoppingcart.impl

import akka.actor.typed.ActorRef
import play.api.libs.json._

trait ReplyToFormat[T, R] {
  implicit def format(implicit actorRefFormat: Format[ActorRef[R]]): Format[T]
}
