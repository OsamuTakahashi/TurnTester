import java.nio.ByteBuffer
import java.util.UUID

object UUIDUtil {

  implicit class UUIDConverter(uuid: UUID) {
    def asByteArray: Array[Byte] = {
      val bb = ByteBuffer.wrap(new Array[Byte](16))
      bb.putLong(uuid.getMostSignificantBits)
      bb.putLong(uuid.getLeastSignificantBits)
      bb.array()
    }
  }

  implicit class ArrayConverter(bytes: Array[Byte]) {
    def asUuid: UUID = {
      val bb = ByteBuffer.wrap(bytes)
      val firstLong = bb.getLong()
      val secondLong = bb.getLong()
      new UUID(firstLong, secondLong)
    }
  }
}