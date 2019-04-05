package com.example.shoppingcart.impl

import akka.actor.ExtendedActorSystem
import akka.actor.typed.{ActorRef, ActorRefResolver, ActorSystem}
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import play.api.libs.json._

import scala.collection.immutable.Seq

trait AkkaTypedJsonSerializers {

  def actorSystem: ActorSystem[_]

  implicit def actorRefFormat[T:Format]: Format[ActorRef[T]] =
    new Format[ActorRef[T]] {

      private val actorRefResolver = ActorRefResolver(actorSystem)

      override def writes(ref: ActorRef[T]): JsValue = {
        JsString(actorRefResolver.toSerializationFormat(ref))
      }


      override def reads(json: JsValue): JsResult[ActorRef[T]] = {
        json match {
          case serializedActorRef: JsString =>
            val actorRef: ActorRef[T] = actorRefResolver.resolveActorRef(serializedActorRef.value)
            JsSuccess(actorRef)

          case _ =>  JsError(s"Can't deserialize $json to ActorRef")
        }

      }
    }

  def typedSerializers: Seq[JsonSerializer[_]]

  def jsonSerializerRegistry: JsonSerializerRegistry = new JsonSerializerRegistry {
    override def serializers: Seq[JsonSerializer[_]] = typedSerializers
  }
}
