package com.sopranoworks.truntester

import java.net.InetSocketAddress
import java.util.UUID

import akka.actor.{ActorRef, FSM, Props}
import akka.util.ByteString
import org.ice4j.attribute._
import org.ice4j.message.{ChannelData, Message, MessageFactory, Request}
import org.ice4j.security.{LongTermCredential, LongTermCredentialSession}
import org.ice4j.stack.StunStack
import org.ice4j.{Transport, TransportAddress}

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration

/**
  * Created by takahashi on 2017/05/25.
  */

object Phase extends Enumeration {
  val WAIT = Value
  val BIND = Value
  val ALLOCATE = Value
  val CREATE_PERMISSION = Value
  val CHANNEL_BIND = Value
  val WAITING_HOST = Value
  val RUNNING = Value
  val STOP = Value
}



class ClientActor(username:String, realm:String, password:String, turnServer:InetSocketAddress, messageInterval:Int, messageSize:Int, host:Option[ActorRef] = None, observer:Option[ActorRef] = None) extends FSM[Phase.Value,Int] {
  import Phase._
  import UUIDUtil._
  private val _stack = new StunStack()
  private val _credential = new LongTermCredential(username,password)
  private val _session = new LongTermCredentialSession(_credential,realm.getBytes())
  private var _peerAddr:Option[InetSocketAddress] = None
  private var _turnMappedEndpoint:Option[InetSocketAddress] = None
  private var _relayedEndpoint:Option[InetSocketAddress] = None
  private var _guest:Option[ActorRef] = None

  _session.setNonce(UUID.randomUUID().asByteArray)

  private val _udpPort = context.system.actorOf(Props(new UdpPort(turnServer,self)),s"UdpPort${self.path.name}")

//  private var _nonce:Option[Array[Byte]] = None
  private var _nonce:Option[NonceAttribute] = None
  private var _realm:Option[RealmAttribute] = None
  private var _lastTransactionId:Array[Byte] = _

  private val _message = ByteString(new Array[Byte](messageSize))

//  private val _sentCounter = Kamon.metrics.counter("num_sent")
//  private val _receivedCounter = Kamon.metrics.counter("num_received")

  private object Retry
  private object Refresh
  private object Send

  def sendRequest(req:Request):Unit = {
    val trId = UUID.randomUUID().asByteArray.take(12)
    req.setTransactionID(trId)
    _lastTransactionId = trId
    _nonce.foreach {
      n =>
        req.putAttribute(n)
        _realm.foreach(req.putAttribute(_))
        req.putAttribute(AttributeFactory.createUsernameAttribute(username))
        val a = new TrueMessageIntegrityAttribute()
        a.setUsername(username)
        a.setRealm(realm)
        a.setPassword(password)
        req.putAttribute(a)
    }
    _udpPort ! ByteString(req.encode(_stack))
  }

  def sendBindMessage():Unit = {
    val req = MessageFactory.createBindingRequest()
    sendRequest(req)
  }

  // Allocate
  def sendAllocateMessage():Unit = {
    val req = MessageFactory.createAllocateRequest()
    req.putAttribute(AttributeFactory.createLifetimeAttribute(180))
    req.putAttribute(AttributeFactory.createRequestedTransportAttribute(17))
    sendRequest(req)
  }

  // CreatePermission
  def sendCreatePermission():Unit = {
    _turnMappedEndpoint.foreach {
      addr =>
        val req = MessageFactory.createCreatePermissionRequest(new TransportAddress(addr,Transport.UDP),UUID.randomUUID().asByteArray)
        sendRequest(req)
    }
  }

  // ChannelBind
  def sendChannelBind():Unit = {
    _turnMappedEndpoint.foreach {
      addr =>
        val req = MessageFactory.createChannelBindRequest(0x4000, new TransportAddress(addr, Transport.UDP), UUID.randomUUID().asByteArray)
        sendRequest(req)
    }
  }

  def sendRefresh():Unit = {
    val req = MessageFactory.createRefreshRequest(180)
    sendRequest(req)
  }

//  override def preStart(): Unit = {
//    setTimer("ping",Retry,Duration(10,"s"))
//  }

  def setRetry():Unit = {
    setTimer("ping",Retry,Duration(10,"s"))
  }


  startWith(WAIT,0)

  when(WAIT) {
    case Event("READY",_) =>
      sendBindMessage()
      goto(BIND)
  }

  when(BIND) {
    case Event(res:Message,_) if (res.getMessageType & 0x000f.toChar) == Message.BINDING_REQUEST =>
      val tp = res.getMessageType
      if (!Message.isErrorResponseType(tp)) {
        log.info("STUN bind request success")
        res.getAttributes.asScala.foreach(a=>log.info(a.getName))

        if (res.containsAttribute(Attribute.MAPPED_ADDRESS)) {
          val addr = res.getAttribute(Attribute.MAPPED_ADDRESS).asInstanceOf[MappedAddressAttribute].getAddress
          _peerAddr = Some(new InetSocketAddress(addr.getAddress,addr.getPort))
//          log.info(s"Peer:${_peerAddr.get}")
        } else
        if (res.containsAttribute(Attribute.XOR_MAPPED_ADDRESS)) {
          val addr:TransportAddress = res.getAttribute(Attribute.XOR_MAPPED_ADDRESS).asInstanceOf[XorMappedAddressAttribute].getAddress
          _peerAddr = Some(new InetSocketAddress(addr.getAddress,addr.getPort))
//          log.info(s"Peer Xor:${_peerAddr.get}")
        } else {
          log.error("Invalid bind request response")
          stop()
        }
        host match {
          case None =>
            _peerAddr.foreach(_ => sendAllocateMessage())
            goto(ALLOCATE)
          case Some(host) =>
            _turnMappedEndpoint match {
              case None =>
                _peerAddr.foreach(host ! _)
                cancelTimer("ping")
                goto(WAITING_HOST)
              case Some(_) =>
                goto(RUNNING)
            }
        }
      } else {
        log.error("STUN bind request failure")
        log.error(ErrorCodeAttribute.getDefaultReasonPhrase(res.getAttribute(Attribute.ERROR_CODE).asInstanceOf[ErrorCodeAttribute].getErrorCode))
        setRetry()
        stay()
      }

    case Event(Retry,_) =>
      sendBindMessage()
      stay()
  }

  when(ALLOCATE) {
    case Event(res:Message,_) if (res.getMessageType & 0x000f.toChar) == Message.ALLOCATE_REQUEST =>
      val tp = res.getMessageType
      if (!Message.isErrorResponseType(tp)) {
        log.info("TURN Allocate request success")

        if (res.containsAttribute(Attribute.XOR_RELAYED_ADDRESS)) {
          val addr:TransportAddress = res.getAttribute(Attribute.XOR_RELAYED_ADDRESS).asInstanceOf[XorRelayedAddressAttribute].getAddress(_lastTransactionId)
          _relayedEndpoint = Some(new InetSocketAddress(addr.getAddress,addr.getPort))
          log.info(s"Relay:${_relayedEndpoint.get}")
          setTimer("refresh",Refresh,Duration(60,"s"),true)
//          res.getAttributes.asScala.foreach(a=>log.info(a.getName))
          _turnMappedEndpoint.foreach(_ => sendCreatePermission())
          goto(CREATE_PERMISSION)
        } else {
          log.error("Invalid Allocate request response")
          stay()
        }
      } else {
        log.error("STUN allocate request error")
        log.error(ErrorCodeAttribute.getDefaultReasonPhrase(res.getAttribute(Attribute.ERROR_CODE).asInstanceOf[ErrorCodeAttribute].getErrorCode))
        log.info(s"response type:${res.getMessageType.toInt}")
        if (res.containsAttribute(Attribute.NONCE)) {                                                  
          _nonce = Some(res.getAttribute(Attribute.NONCE).asInstanceOf[NonceAttribute])
        }
        if (res.containsAttribute(Attribute.REALM)) {
          _realm = Some(res.getAttribute(Attribute.REALM).asInstanceOf[RealmAttribute])
        }
//        if (res.containsAttribute(Attribute.SOFTWARE)) {
//          val soft = new String(res.getAttribute(Attribute.SOFTWARE).asInstanceOf[SoftwareAttribute].getSoftware)
//          log.info(s"Software:$soft")
//        }
        res.getAttributes.asScala.foreach(a=>log.info(a.getName))
        setRetry()
        stay()
      }
    case Event(Retry,_) =>
      sendAllocateMessage()
      stay()
  }

  when(CREATE_PERMISSION) {
    case Event(addr:InetSocketAddress,_) =>
      _turnMappedEndpoint = Some(addr)
      _guest = Some(sender())
      sendCreatePermission()
      stay()
    case Event(res:Message,_) if (res.getMessageType & 0x000f.toChar) == Message.CREATEPERMISSION_REQUEST =>
      val tp = res.getMessageType
      if (!Message.isErrorResponseType(tp)) {
        log.info("TURN Create Permission request success")
        res.getAttributes.asScala.foreach(a=>log.info(a.getName))
        sendChannelBind()
        goto(CHANNEL_BIND)
      } else {
        log.error("TURN Create Permission request error")
        log.error(ErrorCodeAttribute.getDefaultReasonPhrase(res.getAttribute(Attribute.ERROR_CODE).asInstanceOf[ErrorCodeAttribute].getErrorCode))
        setRetry()
        stay()
      }
    case Event(Retry,_) =>
      sendCreatePermission()
      stay()
  }

  when(CHANNEL_BIND) {
    case Event(res:Message,_) if (res.getMessageType & 0x000f.toChar) == Message.CHANNELBIND_REQUEST =>
      val tp = res.getMessageType
      if (!Message.isErrorResponseType(tp)) {
        log.info("TURN Channel Bind request success")
        res.getAttributes.asScala.foreach(a=>log.info(a.getName))
        setTimer("send",Send,Duration(messageInterval,"ms"),true)
        cancelTimer("ping")
        _guest.foreach(_ ! _relayedEndpoint.get)
//        log.info("Running")
        observer.foreach(_ ! RUNNING)
        goto(RUNNING)
      } else {
        log.error("TURN Channel Bind request error")
        log.error(ErrorCodeAttribute.getDefaultReasonPhrase(res.getAttribute(Attribute.ERROR_CODE).asInstanceOf[ErrorCodeAttribute].getErrorCode))
        setRetry()
        stay()
      }
  }

  when(WAITING_HOST) {
    case Event(bytes:ByteString,_) =>
      stay()
    case Event(addr:InetSocketAddress,_) =>
      _turnMappedEndpoint = Some(addr)
      setTimer("send",Send,Duration(messageInterval,"ms"),true)
//      log.info("Running")
      observer.foreach(_ ! RUNNING)
      goto(RUNNING)
  }

  when(RUNNING) {
    case Event(bytes:ByteString,_) =>
//      _receivedCounter.increment()
      log.info(s"${bytes.length} bytes received")
      stay()
    case Event(Send,_) =>
      host match {
        case Some(_) =>
          _udpPort ! (_turnMappedEndpoint.get,_message)
        case None =>
          val data = new ChannelData
          data.setChannelNumber(0x4000)
          data.setData(_message.toArray)
          _udpPort ! ByteString(data.encode())
      }
//      _sentCounter.increment()
      stay()
    case Event(Refresh,_) =>
      if (host.isEmpty) {
        sendRefresh()
      }
      stay()

    case Event(STOP,_) =>
      log.info("Stopping ")
      stop()
      stay()

  }

  whenUnhandled {
    case Event(bytes:ByteString,_) =>
      val res = Message.decode(bytes.toArray,0,bytes.length.toChar)
      if (res != null) {
        self ! res
      } else {
        log.warning("Unknown Message received")
      }
      stay()
    case Event(addr:InetSocketAddress,_) =>
      _turnMappedEndpoint = Some(addr)
      _guest = Some(sender())
      stay()
    case Event(res:Message,_) =>
      stay()
  }
}
