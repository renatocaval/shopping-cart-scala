package com.example.shoppingcart.impl

trait AkkaPersistentTypedComponents extends AkkaTypedComponents {
  def akkaTypedJsonSerializers: AkkaTypedJsonSerializers
  def jsonSerializerRegistry = akkaTypedJsonSerializers.jsonSerializerRegistry
}
