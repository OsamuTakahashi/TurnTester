package com.sopranoworks.truntester

import java.security.MessageDigest

import org.ice4j.attribute.{Attribute, MessageIntegrityAttribute}
import org.ice4j.stack.StunStack

/**
  * Created by takahashi on 2017/05/26.
  */
class TrueMessageIntegrityAttribute extends MessageIntegrityAttribute {
  private var _username:String = _
  private var _password:String = _
  private var _realm:String = _

  override def setUsername(username: String): Unit =
    _username = username

  def setPassword(password:String):Unit =
    _password = password

  def setRealm(realm:String):Unit =
    _realm = realm

  override def encode(stunStack: StunStack, content: Array[Byte], offset: Int, length: Int): Array[Byte] = {
    val key = MessageDigest.getInstance("MD5").digest(s"${_username}:${_realm}:${_password}".getBytes)

    val t = getAttributeType
    val binValue = new Array[Byte](Attribute.HEADER_LENGTH + getDataLength)

    //Type
    binValue(0) = (t >> 8).toByte
    binValue(1) = (t & 0x00FF).toByte

    //Length
    binValue(2) = (getDataLength >> 8).toByte
    binValue(3) = (getDataLength & 0x00FF).toByte

    val hash = MessageIntegrityAttribute.calculateHmacSha1(content, offset, length, key)
    System.arraycopy(hash, 0, binValue, 4, getDataLength)

    binValue
  }
}
