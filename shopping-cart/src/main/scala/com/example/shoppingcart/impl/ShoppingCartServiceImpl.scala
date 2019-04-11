package com.example.shoppingcart.impl

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.util.Timeout
import akka.{Done, NotUsed}
import com.example.shoppingcart.api.{ShoppingCart, ShoppingCartItem, ShoppingCartService}
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.transport.BadRequest
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.{EventStreamElement, PersistentEntityRegistry}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
/**
  * Implementation of the [[ShoppingCartService]].
  */
class ShoppingCartServiceImpl(shoppingCartModel: ShoppingCartModel)(implicit ec: ExecutionContext) extends ShoppingCartService {

  implicit val timeout = Timeout(3.seconds)
  /**
    * Looks up the shopping cart entity for the given ID.
    */
  private def entityRef(id: String) = shoppingCartModel.entityRefFor(id)

//    persistentEntityRegistry.refFor[ShoppingCartEntity](id)

  override def get(id: String): ServiceCall[NotUsed, ShoppingCart] = ServiceCall { _ =>
    entityRef(id).ask(Get(_))
      .mapTo[CurrentState]
      .map(cart => convertShoppingCart(id, cart.state))
  }

  override def updateItem(id: String): ServiceCall[ShoppingCartItem, Done] = ServiceCall { update =>
    entityRef(id)
      .ask(UpdateItem(update.productId, update.quantity, _))
      .mapTo[Confirmation]
      .map {
        case Accepted => Done
        case Rejected(msg) => throw BadRequest(msg)
      }

  }

  override def checkout(id: String): ServiceCall[NotUsed, Done] = ServiceCall { _ =>
    entityRef(id)
      .ask(Checkout(_))
      .mapTo[Confirmation]
      .map {
        case Accepted => Done
        case Rejected(msg) => throw BadRequest(msg)
      }
  }

  override def shoppingCartTopic: Topic[ShoppingCart] = TopicProducer.singleStreamWithOffset { fromOffset =>
//    persistentEntityRegistry.eventStream(ShoppingCartEvent.Tag, fromOffset)
//      .filter(_.event.isInstanceOf[CheckedOut.type])
//      .mapAsync(4) {
//        case EventStreamElement(id, _, offset) =>
//          entityRef(id)
//            .ask(Get)
//            .map(cart => convertShoppingCart(id, cart) -> offset)
//      }
    ???
    }

  private def convertShoppingCart(id: String, cart: ShoppingCartState) = {
    ShoppingCart(id, cart.items.map((ShoppingCartItem.apply _).tupled).toSeq, cart.checkedOut)
  }
}
