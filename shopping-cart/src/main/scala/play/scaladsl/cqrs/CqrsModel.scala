
package play.scaladsl.cqrs

import akka.Done
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.journal.Tagged
import akka.persistence.typed.ExpectingReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventShards, AggregateEventTag}
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import play.api.libs.json.{Format, _}

import scala.collection.immutable.Seq
import scala.reflect.ClassTag

abstract class CqrsModel[Command:ClassTag, Event, State](clusterSharding: ClusterSharding) {

  val name: String

  val typeKey = EntityTypeKey[Command](name)
  
  def tagger(entityId: String): Event => Set[String]

  def entityRefFor(entityId: String): EntityRef[Command] =
    clusterSharding.entityRefFor(typeKey, entityId)
  
  def behavior(entityContext: EntityContext): EventSourcedBehavior[Command, Event, State]

  // at this point we should be able to pass ShardingSettings
  clusterSharding.init(Entity(typeKey, ctx => behavior(ctx).withTagger(tagger(ctx.entityId))))


}