package com.example.shoppingcart.impl

import akka.actor.typed.{ActorSystem => TypedActorSystem}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding

trait AkkaTypedClusterComponents {
  def typedActorSystem: TypedActorSystem[Nothing]

  lazy val clusterSharding: ClusterSharding = ClusterSharding(typedActorSystem)
}
