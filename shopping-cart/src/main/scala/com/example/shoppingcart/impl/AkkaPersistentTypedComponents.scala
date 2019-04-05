package com.example.shoppingcart.impl

trait AkkaPersistentTypedComponents extends AkkaTypedComponents with AkkaTypedClusterComponents {
  def akkaTypedJsonSerializers: AkkaTypedJsonSerializers
  def jsonSerializerRegistry = akkaTypedJsonSerializers.jsonSerializerRegistry
}
