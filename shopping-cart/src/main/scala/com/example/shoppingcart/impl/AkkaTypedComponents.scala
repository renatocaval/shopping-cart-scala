package com.example.shoppingcart.impl

import akka.actor.ActorSystem
import akka.actor.typed.{ActorSystem => TypedActorSystem}
import akka.actor.typed.scaladsl.adapter._

trait AkkaTypedComponents  {
  def actorSystem: ActorSystem
  def typedActorSystem: TypedActorSystem[_] = actorSystem.toTyped
}
