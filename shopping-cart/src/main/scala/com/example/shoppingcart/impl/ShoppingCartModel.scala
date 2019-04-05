package com.example.shoppingcart.impl

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

/**
 * That's just an example. Not sure if we need such a wrapping thing,
 * but there are a few things that need to be done correctly and well aligned before using an entity.
 *
 * Here we inject the cluster sharding, wire the behavior, adding a tagger and making it easier to retrieve an instance
 */
class ShoppingCartModel(clusterSharding: ClusterSharding) {

  type Command = ShoppingCartCommand[ShoppingCartReply]

  val typeKey = EntityTypeKey[Command]("shopping-cart")

  def behavior(entityContext: EntityContext): Behavior[Command] = {

    // we need to isolate the persistenceId because
    // it's required for the sharded tagging in Lagom
    val persistenceId = PersistenceId(s"ShoppingCart|${entityContext.entityId}")

    EventSourcedBehavior.withEnforcedReplies[Command, ShoppingCartEvent, ShoppingCartState](
      persistenceId = persistenceId,
      emptyState = ShoppingCartState.empty,
      commandHandler = (cart, cmd) => cart.applyCommand(cmd),
      eventHandler = (cart, evt) => cart.applyEvent(evt)
    )
    // this is quite different than current Lagom experience
    // we need to have a tagger defined somewhere so we can use it in distributed projections as well
    // Lagom adds it to the Event, for instance.
    .withTagger(Tagger.sharded(persistenceId.id, 10, "ShoppingCartEvent"))

  }

  clusterSharding.init(Entity(typeKey, ctx => behavior(ctx)))

  def entityRefFor(shoppingCartId: String): EntityRef[Command] =
    clusterSharding.entityRefFor(typeKey, shoppingCartId)
}



/**
 * The current state held by the persistent entity.
 */
case class ShoppingCartState(items: Map[String, Int], checkedOut: Boolean) {

  def applyCommand(cmd: ShoppingCartCommand[_]): ReplyEffect[ShoppingCartEvent, ShoppingCartState] =
    cmd match {
      case x: UpdateItem => onUpdateItem(x)
      case x: Checkout => onCheckout(x)
      case x: Get => onReadState(x)
    }


  def onUpdateItem(cmd: UpdateItem): ReplyEffect[ShoppingCartEvent, ShoppingCartState] =
    cmd match {
      case UpdateItem(_, qty, _) if qty < 0 =>
        Effect.reply(cmd)(Rejected("Quantity must be greater than zero"))

      case UpdateItem(productId, 0, _) if !items.contains(productId) =>
        Effect.reply(cmd)(Rejected("Cannot delete item that is not already in cart"))

      case UpdateItem(productId, quantity, _) =>
        Effect
          .persist(ItemUpdated(productId, quantity))
          .thenReply(cmd) { _ => Accepted }
    }

  def onCheckout(cmd: Checkout): ReplyEffect[ShoppingCartEvent, ShoppingCartState] =
    if (items.isEmpty)
      Effect.reply(cmd)(Rejected("Cannot checkout empty cart"))
    else
      Effect
        .persist(CheckedOut)
        .thenReply(cmd) { _ => Accepted }


  def onReadState(cmd: Get): ReplyEffect[ShoppingCartEvent, ShoppingCartState] =
    Effect.reply(cmd)(CurrentState(this))


  def applyEvent(evt: ShoppingCartEvent): ShoppingCartState = {
    evt match {
      case ItemUpdated(productId, quantity) => updateItem(productId, quantity)
      case CheckedOut => checkout
    }
  }

  private def updateItem(productId: String, quantity: Int) = {
    quantity match {
      case 0 => copy(items = items - productId)
      case _ => copy(items = items + (productId -> quantity))
    }
  }

  private def checkout = copy(checkedOut = true)
}

object ShoppingCartState {
  /**
   * Format for the hello state.
   *
   * Persisted entities get snapshotted every configured number of events. This
   * means the state gets stored to the database, so that when the entity gets
   * loaded, you don't need to replay all the events, just the ones since the
   * snapshot. Hence, a JSON format needs to be declared so that it can be
   * serialized and deserialized when storing to and from the database.
   */
  implicit val format: Format[ShoppingCartState] = Json.format

  def empty: ShoppingCartState = ShoppingCartState(Map.empty, checkedOut = false)
}

/**
 * This interface defines all the events that the ShoppingCartEntity supports.
 */
sealed trait ShoppingCartEvent

/**
 * An event that represents a item updated event.
 */
case class ItemUpdated(productId: String, quantity: Int) extends ShoppingCartEvent

object ItemUpdated {

  /**
   * Format for the item updated event.
   *
   * Events get stored and loaded from the database, hence a JSON format
   * needs to be declared so that they can be serialized and deserialized.
   */
  implicit val format: Format[ItemUpdated] = Json.format
}

sealed trait CheckedOut extends ShoppingCartEvent

/**
 * An event that represents a checked out event.
 */
case object CheckedOut extends CheckedOut {

  /**
   * Format for the checked out event.
   *
   * Events get stored and loaded from the database, hence a JSON format
   * needs to be declared so that they can be serialized and deserialized.
   */
  implicit val format: Format[CheckedOut.type] = Format(
    Reads(_ => JsSuccess(CheckedOut)),
    Writes(_ => Json.obj())
  )
}


sealed trait ShoppingCartReply

sealed trait Confirmation extends ShoppingCartReply

case object Confirmation {
  implicit val format: Format[Confirmation] = new Format[Confirmation] {
    override def reads(json: JsValue): JsResult[Confirmation] = {
      if ((json \ "reason").isDefined)
        Json.fromJson[Rejected](json)
      else
        Json.fromJson[Accepted](json)
    }

    override def writes(o: Confirmation): JsValue = {
      o match {
        case Accepted => Json.toJson(Accepted)
        case rej: Rejected => Json.toJson(rej)
      }
    }
  }
}

sealed trait Accepted extends Confirmation

case object Accepted extends Accepted {
  implicit val format: Format[Accepted] = Format(
    Reads(_ => JsSuccess(Accepted)),
    Writes(_ => Json.obj())
  )
}

case class Rejected(reason: String) extends Confirmation

object Rejected {
  implicit val format: Format[Rejected] = Json.format
}

final case class CurrentState(state: ShoppingCartState) extends ShoppingCartReply

object CurrentState {
  implicit val format: Format[CurrentState] = Json.format
}


/**
 * This interface defines all the commands that the ShoppingCartEntity supports.
 */
sealed trait ShoppingCartCommand[R] extends ExpectingReply[R]

/**
 * A command to update an item.
 *
 * It has a reply type of [[Done]], which is sent back to the caller
 * when all the events emitted by this command are successfully persisted.
 */
case class UpdateItem(productId: String, quantity: Int, replyTo: ActorRef[Confirmation]) extends ShoppingCartCommand[Confirmation]

object UpdateItem extends ReplyToFormat[UpdateItem, Confirmation] {
  override implicit def format(implicit actorRefFormat: Format[ActorRef[Confirmation]]): Format[UpdateItem] = Json.format
}

/**
 * A command to get the current state of the shopping cart.
 *
 * The reply type is the ShoppingCart, and will contain the message to say to that
 * person.
 */
case class Get(replyTo: ActorRef[CurrentState]) extends ShoppingCartCommand[CurrentState]

object Get extends ReplyToFormat[Get, CurrentState] {
  override implicit def format(implicit actorRefFormat: Format[ActorRef[CurrentState]]): Format[Get] = Json.format
}

/**
 * A command to checkout the shopping cart.
 *
 * The reply type is the Done, which will be returned when the events have been
 * emitted.
 */
case class Checkout(replyTo: ActorRef[Confirmation]) extends ShoppingCartCommand[Confirmation]

object Checkout extends ReplyToFormat [Checkout, Confirmation] {
  override implicit def format(implicit actorRefFormat: Format[ActorRef[Confirmation]]): Format[Checkout] = Json.format
}

/**
 * An exception thrown by the shopping cart validation
 *
 * @param message The message
 */
case class ShoppingCartException(message: String) extends RuntimeException(message)

object ShoppingCartException {

  /**
   * Format for the ShoppingCartException.
   *
   * When a command fails, the error needs to be serialized and sent back to
   * the node that requested it, this is used to do that.
   */
  implicit val format: Format[ShoppingCartException] = Json.format[ShoppingCartException]
}

/**
 * Akka serialization, used by both persistence and remoting, needs to have
 * serializers registered for every type serialized or deserialized. While it's
 * possible to use any serializer you want for Akka messages, out of the box
 * Lagom provides support for JSON, via this registry abstraction.
 *
 * The serializers are registered here, and then provided to Lagom in the
 * application loader.
 */
class ShoppingCartSerializers(val actorSystem: ActorSystem[_]) extends AkkaTypedJsonSerializers {

  def typedSerializers: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer[ItemUpdated],
    JsonSerializer[CheckedOut.type],
    JsonSerializer[UpdateItem],
    JsonSerializer[Checkout],
    JsonSerializer[Get],
    JsonSerializer[ShoppingCartState],
    JsonSerializer[ShoppingCartException]
  )

}