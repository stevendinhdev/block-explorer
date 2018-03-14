package com.xsn.explorer.services

import com.xsn.explorer.config.RPCConfig
import com.xsn.explorer.errors.{TransactionNotFoundError, XSNMessageError, XSNUnexpectedResponseError}
import com.xsn.explorer.helpers.Executors
import com.xsn.explorer.models.TransactionId
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalactic.Bad
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{MustMatchers, OptionValues, WordSpec}
import play.api.libs.json.{JsNull, Json}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}

import scala.concurrent.Future

class XSNServiceRPCImplSpec extends WordSpec with MustMatchers with ScalaFutures with MockitoSugar with OptionValues {

  val ws = mock[WSClient]
  val ec = Executors.externalServiceEC
  val config = new RPCConfig {
    override def password: RPCConfig.Password = RPCConfig.Password("pass")
    override def host: RPCConfig.Host = RPCConfig.Host("localhost")
    override def username: RPCConfig.Username = RPCConfig.Username("user")
  }

  val request = mock[WSRequest]
  val response = mock[WSResponse]
  when(ws.url(anyString)).thenReturn(request)
  when(request.withAuth(anyString(), anyString(), any())).thenReturn(request)
  when(request.withHttpHeaders(any())).thenReturn(request)

  val service = new XSNServiceRPCImpl(ws, config)(ec)
  val txid = TransactionId.from("024aba1d535cfe5dd3ea465d46a828a57b00e1df012d7a2d158e0f7484173f7c").get

  "getTransaction" should {
    "handle coinbase" in {
      val responseBody =
        """
          |{
          |    "result": {
          |        "blockhash": "000003fb382f6892ae96594b81aa916a8923c70701de4e7054aac556c7271ef7",
          |        "blocktime": 1520276270,
          |        "confirmations": 5347,
          |        "height": 1,
          |        "hex": "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff03510101ffffffff010000000000000000232103e8c52f2c5155771492907095753a43ce776e1fa7c5e769a67a9f3db4467ec029ac00000000",
          |        "locktime": 0,
          |        "size": 98,
          |        "time": 1520276270,
          |        "txid": "024aba1d535cfe5dd3ea465d46a828a57b00e1df012d7a2d158e0f7484173f7c",
          |        "version": 1,
          |        "vin": [
          |            {
          |                "coinbase": "510101",
          |                "sequence": 4294967295
          |            }
          |        ],
          |        "vout": [
          |            {
          |                "n": 0,
          |                "scriptPubKey": {
          |                    "addresses": [
          |                        "XdJnCKYNwzCz8ATv8Eu75gonaHyfr9qXg9"
          |                    ],
          |                    "asm": "03e8c52f2c5155771492907095753a43ce776e1fa7c5e769a67a9f3db4467ec029 OP_CHECKSIG",
          |                    "hex": "2103e8c52f2c5155771492907095753a43ce776e1fa7c5e769a67a9f3db4467ec029ac",
          |                    "reqSigs": 1,
          |                    "type": "pubkey"
          |                },
          |                "value": 0,
          |                "valueSat": 0
          |            }
          |        ]
          |    },
          |    "error": null,
          |    "id": null
          |}
        """.stripMargin.trim

      val json = Json.parse(responseBody)

      when(response.status).thenReturn(200)
      when(response.json).thenReturn(json)
      when(request.post[String](anyString())(any())).thenReturn(Future.successful(response))

      whenReady(service.getTransaction(txid)) { result =>
        result.isGood mustEqual true

        val tx = result.get
        tx.id.string mustEqual "024aba1d535cfe5dd3ea465d46a828a57b00e1df012d7a2d158e0f7484173f7c"
        tx.vin.isEmpty mustEqual true
        tx.vout.size mustEqual 1
      }
    }
    "handle non-coinbase result" in {
      val responseBody =
        """
          |{
          |    "result": {
          |      "hex": "0100000001d967897603771672654db507a02ceb65dea8a682d2333ee819cac80950ec5c58020000006a473044022059a0cc21ad24ae18726d128c85328a0b54dab62aeb41ffbcad368ece6fdf9d2602200e477332401ce1296d379dc5f797720e854e40fc5af0a268f585e7dae64d38e5012103624fbfb0079e85bbc9aaeba6f48581326ad01194b3c54ce22852a27b1d2892d1ffffffff03000000000000000000220935d7946a00001976a9143cc9ede1da2d7351aaebaf6a25d2657e0b05a71688ac220935d7946a00001976a9143cc9ede1da2d7351aaebaf6a25d2657e0b05a71688ac00000000",
          |      "txid": "0834641a7d30d8a2d2b451617599670445ee94ed7736e146c13be260c576c641",
          |      "size": 234,
          |      "version": 1,
          |      "locktime": 0,
          |      "vin": [
          |        {
          |          "txid": "585cec5009c8ca19e83e33d282a6a8de65eb2ca007b54d6572167703768967d9",
          |          "vout": 2,
          |          "scriptSig": {
          |            "asm": "3044022059a0cc21ad24ae18726d128c85328a0b54dab62aeb41ffbcad368ece6fdf9d2602200e477332401ce1296d379dc5f797720e854e40fc5af0a268f585e7dae64d38e5[ALL] 03624fbfb0079e85bbc9aaeba6f48581326ad01194b3c54ce22852a27b1d2892d1",
          |            "hex": "473044022059a0cc21ad24ae18726d128c85328a0b54dab62aeb41ffbcad368ece6fdf9d2602200e477332401ce1296d379dc5f797720e854e40fc5af0a268f585e7dae64d38e5012103624fbfb0079e85bbc9aaeba6f48581326ad01194b3c54ce22852a27b1d2892d1"
          |          },
          |          "sequence": 4294967295
          |        }
          |      ],
          |      "vout": [
          |        {
          |          "value": 0.00000000,
          |          "valueSat": 0,
          |          "n": 0,
          |          "scriptPubKey": {
          |            "asm": "",
          |            "hex": "",
          |            "type": "nonstandard"
          |          }
          |        },
          |        {
          |          "value": 1171874.98281250,
          |          "valueSat": 117187498281250,
          |          "n": 1,
          |          "scriptPubKey": {
          |            "asm": "OP_DUP OP_HASH160 3cc9ede1da2d7351aaebaf6a25d2657e0b05a716 OP_EQUALVERIFY OP_CHECKSIG",
          |            "hex": "76a9143cc9ede1da2d7351aaebaf6a25d2657e0b05a71688ac",
          |            "reqSigs": 1,
          |            "type": "pubkeyhash",
          |            "addresses": [
          |              "XgEGH3y7RfeKEdn2hkYEvBnrnmGBr7zvjL"
          |            ]
          |          }
          |        },
          |        {
          |          "value": 1171874.98281250,
          |          "valueSat": 117187498281250,
          |          "n": 2,
          |          "scriptPubKey": {
          |            "asm": "OP_DUP OP_HASH160 3cc9ede1da2d7351aaebaf6a25d2657e0b05a716 OP_EQUALVERIFY OP_CHECKSIG",
          |            "hex": "76a9143cc9ede1da2d7351aaebaf6a25d2657e0b05a71688ac",
          |            "reqSigs": 1,
          |            "type": "pubkeyhash",
          |            "addresses": [
          |              "XgEGH3y7RfeKEdn2hkYEvBnrnmGBr7zvjL"
          |            ]
          |          }
          |        }
          |      ],
          |      "blockhash": "b72dd1655408e9307ef5874be20422ee71029333283e2360975bc6073bdb2b81",
          |      "height": 809,
          |      "confirmations": 1950,
          |      "time": 1520318120,
          |      "blocktime": 1520318120
          |    },
          |    "error": null,
          |    "id": null
          |}
        """.stripMargin.trim

      val json = Json.parse(responseBody)

      when(response.status).thenReturn(200)
      when(response.json).thenReturn(json)
      when(request.post[String](anyString())(any())).thenReturn(Future.successful(response))

      whenReady(service.getTransaction(txid)) { result =>
        result.isGood mustEqual true

        val tx = result.get
        tx.id.string mustEqual "0834641a7d30d8a2d2b451617599670445ee94ed7736e146c13be260c576c641"
        tx.vin.value.txid.string mustEqual "585cec5009c8ca19e83e33d282a6a8de65eb2ca007b54d6572167703768967d9"
        tx.vout.size mustEqual 3
      }
    }

    "handle transaction not found" in {
      val responseBody = """{"result":null,"error":{"code":-5,"message":"No information available about transaction"},"id":null}"""
      val json = Json.parse(responseBody)
      when(response.status).thenReturn(200)
      when(response.json).thenReturn(json)
      when(request.post[String](anyString())(any())).thenReturn(Future.successful(response))

      whenReady(service.getTransaction(txid)) { result =>
        result mustEqual Bad(TransactionNotFoundError).accumulating
      }
    }

    "handle error with message" in {
      val responseBody = """{"result":null,"error":{"code":-32600,"message":"Params must be an array"},"id":null}"""
      val json = Json.parse(responseBody)

      when(response.status).thenReturn(200)
      when(response.json).thenReturn(json)
      when(request.post[String](anyString())(any())).thenReturn(Future.successful(response))

      whenReady(service.getTransaction(txid)) { result =>
        val error = XSNMessageError("Params must be an array")
        result mustEqual Bad(error).accumulating
      }
    }

    "handle non successful status" in {
      when(response.status).thenReturn(403)
      when(response.json).thenReturn(JsNull)
      when(request.post[String](anyString())(any())).thenReturn(Future.successful(response))

      whenReady(service.getTransaction(txid)) { result =>
        result mustEqual Bad(XSNUnexpectedResponseError).accumulating
      }
    }

    "handle unexpected error" in {
      val responseBody = """{"result":null,"error":{},"id":null}"""
      val json = Json.parse(responseBody)

      when(response.status).thenReturn(200)
      when(response.json).thenReturn(json)
      when(request.post[String](anyString())(any())).thenReturn(Future.successful(response))

      whenReady(service.getTransaction(txid)) { result =>
        result mustEqual Bad(XSNUnexpectedResponseError).accumulating
      }
    }
  }
}
