package play.scaladsl.cqrs

object Tagger {

  def sharded[Event](uniqueId: String, numOfShards: Int, tag: String): Event => Set[String] = {
    val shardId = uniqueId.hashCode % numOfShards
    _ => Set(tag + shardId)
  }

  def sharded[Event](uniqueId: String, numOfShards: Int,
                     tag1: String, tag2: String): Event => Set[String] = {
    val shardId = uniqueId.hashCode % numOfShards
    _ => Set(tag1 + shardId, tag2 + shardId)
  }

  def sharded[Event](uniqueId: String, numOfShards: Int,
                     tag1: String, tag2: String, otherTags: String*): Event => Set[String] = {
    val shardId = uniqueId.hashCode % numOfShards
    _ => Set(tag1 + shardId, tag2 + shardId) ++ otherTags
  }

  def tag[Event](tag: String): Event => Set[String] =
    _ => Set(tag)

  def tag[Event](tag1: String, tag2: String): Event => Set[String] =
    _ => Set(tag1, tag2)

  def tag[Event](tag1: String, tag2: String, otherTags: String*): Event => Set[String] =
    _ => Set(tag1, tag2) ++ otherTags
}
