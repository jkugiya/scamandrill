package com.joypeg.scamandrill.client

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import com.joypeg.scamandrill.utils.SimpleLogger

/**
 * This trait abstract on top of spray the handling of all request / response to the mandrill API. Its
 * executeQuery fuction is the one use by both the async client and the blocking one (wrapper).
 */
trait ScamandrillSendReceive extends SimpleLogger {

  type Entity = Either[Throwable, RequestEntity]

  implicit val system: ActorSystem
  implicit val materializer = ActorMaterializer()

  import system.dispatcher


  /**
   * Fire a request to Mandrill API and try to parse the response. Because it return a Future[S], the
   * unmarshalling of the response body is done via a partially applied function on the Future transformation.
   * Uses spray-can internally to fire the request and unmarshall the response, with spray-json to do that.
   * @param endpoint - the Mandrill API endpoint for the operation, for example '/messages/send.json'
   * @param reqBodyF - the body of the post request already marshalled as json
   * @param handler - this is the unmarshaller fuction of the response body, partially applied function
   * @tparam S - the type of the expected body response once unmarshalled
   * @return - a future of the expected type S
   * @note as from the api documentation, all requests are POST, and You can consider any non-200 HTTP
   *       response code an error - the returned data will contain more detailed information
   */
  def executeQuery[S ](endpoint: String, reqBodyF: Future[RequestEntity])(handler:(HttpResponse => Future[S])): Future[S] = {

    //TODO: reqbody <: MandrillResponse and S :< MandrillRequest
    val resp = reqBodyF.flatMap{reqBody =>
      val request = HttpRequest(
      method = HttpMethods.POST,
      uri = Uri("https://mandrillapp.com/api/1.0"+endpoint),
      entity = reqBody
      )
      Http().singleRequest(request).flatMap { resp =>
        if(resp.status.isSuccess()) handler(resp)
        else Future.failed(new UnsuccessfulResponseException(resp))
      }
    }
    resp
  }

  /**
   * Asks all the underlying actors to close (waiting for 1 second)
   * and then shut down the system. Because the blocking client is
   * basically a wrapper of the async one, both the async and blocking
   * client are supposed to call this method when they are not required
   * or the application using them exit.
   */
  def shutdown(): Unit = {
    logger.info("asking all actor to close")
    Await.ready(Http().shutdownAllConnectionPools(), 1 second)
    system.terminate()
    logger.info("actor system shut down")
  }
}
