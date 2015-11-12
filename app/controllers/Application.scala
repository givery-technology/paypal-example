package controllers

import play.api._
import play.api.mvc._
import play.api.Play.current
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import java.net.URLDecoder
import play.api.cache._
import javax.inject.Inject
import org.joda.time.DateTime

class Application @Inject() (cache: CacheApi) extends Controller {

  val API_KEY  = Play.configuration.getString("paypal.apikey").get
  val PASSWORD = Play.configuration.getString("paypal.password").get
  val USERNAME = Play.configuration.getString("paypal.username").get
  val ENDPOINT = "https://api-3t.sandbox.paypal.com/nvp"
  val VERSION  = "124"
//  https://www.sandbox.paypal.com/jp/cgi-bin/webscr?cmd=_express-checkout&token=EC-XXXXXXXXXX

//curl -s --insecure  https://api-3t.sandbox.paypal.com/nvp  -d  "USER=platfo_1255077030_biz_api1.gmail.com&PWD=1255077037&SIGNATURE=Abg0gYcQyxQvnf2HDJkKtA-p6pqhA1k-KTYE0Gcy1diujFio4io5Vqjf&METHOD=SetExpressCheckout&VERSION=78&PAYMENTREQUEST_0_PAYMENTACTION=SALE&PAYMENTREQUEST_0_AMT=19&PAYMENTREQUEST_0_CURRENCYCODE=USD&cancelUrl=http://www.example.com/cancel.html&returnUrl=http://www.example.com/success.html"
// SIGNATURE=Abg0gYcQyxQvnf2HDJkKtA-p6pqhA1k-KTYE0Gcy1diujFio4io5Vqjf&METHOD=SetExpressCheckout&VERSION=78&
// PAYMENTREQUEST_0_PAYMENTACTION=SALE&PAYMENTREQUEST_0_AMT=19&PAYMENTREQUEST_0_CURRENCYCODE=USD
// &cancelUrl=http://www.example.com/cancel.html&returnUrl=http://www.example.com/success.html"

  def index = Action {
    Ok(views.html.index("index"))
  }

  def setExpressCheckout = Action.async {
    val params = Map(
      "USER" -> USERNAME,
      "PWD" -> PASSWORD,
      "SIGNATURE" -> API_KEY,
      "VERSION" -> VERSION,
      "METHOD" -> "SetExpressCheckout",
      "PAYMENTREQUEST_0_PAYMENTACTION" -> "SALE",
      "PAYMENTREQUEST_0_AMT" -> "1000",
      "PAYMENTREQUEST_0_CURRENCYCODE" -> "USD",
      "RETURNURL" -> "http://localhost:9000/success",
      "CANCELURL" -> "http://localhost:9000/failure",
      "LOGOIMG" -> "https://app.code-check.io/assets/images/cc_logo_b.png",
      "BRANDNAME" -> "codecheck",
      "REQCONFIRMSHIPPING" -> "0",
      "NOSHIPPING" -> "1",
      "L_PAYMENTREQUEST_0_NAME0" -> "Plan1",
      "L_PAYMENTREQUEST_0_AMT0" -> "1000"
    ).map{ case (k, v) => (k, Seq(v))}
    WS.url(ENDPOINT).post(params).map { response =>
      val map = parse(response.body)
      println("********** SetExpressCheckout *********")
      printMap(map)
      val token = map("TOKEN")
      Redirect("https://www.sandbox.paypal.com/jp/cgi-bin/webscr?cmd=_express-checkout&token=" + token)
    }
  }

  def setExpressCheckoutForRecurring = Action.async {
    val params = Map(
      "USER" -> USERNAME,
      "PWD" -> PASSWORD,
      "SIGNATURE" -> API_KEY,
      "VERSION" -> VERSION,
      "METHOD" -> "SetExpressCheckout",
      "PAYMENTREQUEST_0_PAYMENTACTION" -> "SALE",
      "PAYMENTREQUEST_0_AMT" -> "0",
      "PAYMENTREQUEST_0_CURRENCYCODE" -> "USD",
      "RETURNURL" -> "http://localhost:9000/success",
      "CANCELURL" -> "http://localhost:9000/failure",
      "LOGOIMG" -> "https://app.code-check.io/assets/images/cc_logo_b.png",
      "BRANDNAME" -> "codecheck",
      "REQCONFIRMSHIPPING" -> "0",
      "NOSHIPPING" -> "1",
      "L_BILLINGTYPE0" -> "RecurringPayments",
      "L_BILLINGAGREEMENTDESCRIPTION0" -> "Standard plan"
    ).map{ case (k, v) => (k, Seq(v))}
    WS.url(ENDPOINT).post(params).map { response =>
      val map = parse(response.body)
      println("********** SetExpressCheckout *********")
      printMap(map)
      val token = map("TOKEN")
      Redirect("https://www.sandbox.paypal.com/jp/cgi-bin/webscr?cmd=_express-checkout&token=" + token)
    }
  }

  def success = Action.async { request =>
    (for {
      token <- request.getQueryString("token")
    } yield {
      val payerId = request.getQueryString("PayerID").getOrElse("None")
      println("********** SetExpressCheckout - success *********")
      println("token=" + token)
      println("PayerID=" + payerId)

      var params = Map(
        "USER" -> USERNAME,
        "PWD" -> PASSWORD,
        "SIGNATURE" -> API_KEY,
        "VERSION" -> VERSION,
        "METHOD" -> "GetExpressCheckoutDetails",
        "TOKEN" -> token
      ).map{ case (k, v) => (k, Seq(v))}
      WS.url(ENDPOINT).post(params).map { response =>
        val map = parse(response.body)
        println("********** GetExpressCheckoutDetails *********")
        cache.set(token, map)
        printMap(map)
        Redirect("/confirm/" + token)
      }
    }).getOrElse(Future.successful(BadRequest("BadRequest")))
  }

  def failure = Action { request =>
    (for {
      token <- request.getQueryString("token")
    } yield {
      println("********** SetExpressCheckout - failure *********")
      println("token=" + token)
      Redirect("/")
    }).getOrElse(BadRequest("BadRequest"))
  }

  def confirm(token: String) = Action {
    val map = cache.getOrElse[Map[String, String]](token) {
      Map.empty
    }
    println("********** confirm *********")
    printMap(map)
    Ok(views.html.index("confirm", map))
  }

  def doExpressCheckoutPayment(token: String) = Action.async {
    val map = cache.getOrElse[Map[String, String]](token) {
      Map.empty
    }
    println("********** doExpressCheckoutPayment - before *********")
    printMap(map)

    val params = Map(
      "USER" -> USERNAME,
      "PWD" -> PASSWORD,
      "SIGNATURE" -> API_KEY,
      "VERSION" -> VERSION,
      "METHOD" -> "DoExpressCheckoutPayment",
      "TOKEN" -> map("TOKEN"),
      "PAYERID" -> map("PAYERID"),
      "PAYMENTREQUEST_0_PAYMENTACTION" -> "SALE",
      "PAYMENTREQUEST_0_AMT" -> "1000"
    ).map{ case (k, v) => (k, Seq(v))}
    WS.url(ENDPOINT).post(params).map { response =>
      val map = parse(response.body)
      println("********** doExpressCheckoutPayment - after *********")
      printMap(map)
      cache.set(token, map)
      Redirect("/submitted/" + token)
    }
  }

  def createRecurringPaymentsProfile(token: String) = Action.async {
    val map = cache.getOrElse[Map[String, String]](token) {
      Map.empty
    }
    println("********** createRecurringPaymentsProfile - before *********")
    printMap(map)

    val params = Map(
      "USER" -> USERNAME,
      "PWD" -> PASSWORD,
      "SIGNATURE" -> API_KEY,
      "VERSION" -> VERSION,
      "METHOD" -> "CreateRecurringPaymentsProfile",
      "TOKEN" -> map("TOKEN"),
      "PAYERID" -> map("PAYERID"),
      "EMAIL" -> map("EMAIL"),
      "PROFILESTARTDATE" -> DateTime.now.toString("yyyy-MM-dd'T'HH:mm:ssZ"),
      "DESC" -> "Standard plan",
      "AUTOBILLOUTAMT" -> "AddToNextBilling",
      "BILLINGPERIOD" -> "Month",
      "BILLINGFREQUENCY" -> "12",
      "AMT" -> "1000"
    ).map{ case (k, v) => (k, Seq(v))}
    WS.url(ENDPOINT).post(params).map { response =>
      val map = parse(response.body)
      println("********** createRecurringPaymentsProfile - after *********")
      printMap(map)
      cache.set(token, map)
      Redirect("/submitted/" + token)
    }
  }

  def submitted(token: String) = Action {
    val map = cache.getOrElse[Map[String, String]](token) {
      Map.empty
    }
    println("********** submitted *********")
    printMap(map)
    Ok(views.html.index("submitted", map))
  }

  private def printMap(map: Map[String, String]) = {
    map.foreach { case (k, v) =>
      println(k + "=" + v)
    }
  }
  private def parse(body: String): Map[String, String] = {
    body.split("&").map { v =>
      val kv = v.split("=")
      (kv(0), URLDecoder.decode(kv(1), "utf-8"))
    }.toMap
  }

}
