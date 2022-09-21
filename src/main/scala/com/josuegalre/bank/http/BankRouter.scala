package com.josuegalre.bank.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import cats.data.Validated
import cats.implicits._
import com.josuegalre.bank.actors.PersistentBankAccount.Command._
import com.josuegalre.bank.actors.PersistentBankAccount.Response._
import com.josuegalre.bank.http.Validation._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class BankAccountCreationRequest(user: String, currency: String, balance: Double) {
  def toCommand: CreateBankAccount = CreateBankAccount(user, currency, balance)
}

object BankAccountCreationRequest {
  implicit val validator: Validator[BankAccountCreationRequest] = (request: BankAccountCreationRequest) => {
    val userValidation = validateRequired(request.user, "user")
    val currencyValidation = validateRequired(request.currency, "currency")
    val balanceValidation = validateMinimum(request.balance, 0, "balance")
      .combine(validateMinimumAbs(request.balance, 0.01, "balance"))

    (userValidation, currencyValidation, balanceValidation).mapN(BankAccountCreationRequest.apply)
  }
}

case class BankAccountUpdateRequest(currency: String, amount: Double) {
  def toCommand(id: String): UpdateBalance = UpdateBalance(id, currency, amount)
}

object BankAccountUpdateRequest {
  implicit val validator: Validator[BankAccountUpdateRequest] = (request: BankAccountUpdateRequest) => {
    val currencyValidation = validateRequired(request.currency, "currency")
    val amountValidation = validateMinimumAbs(request.amount, 0.01, "amount")

    (currencyValidation, amountValidation).mapN(BankAccountUpdateRequest.apply)
  }
}

case class FailureResponse(reason: String)

class BankRouter(bank: ActorRef)(implicit system: ActorSystem) {

  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(5.seconds)

  def createBankAccount(bankAccountCreationRequest: BankAccountCreationRequest): Future[BankAccountCreatedResponse] =
    (bank ? bankAccountCreationRequest.toCommand).mapTo[BankAccountCreatedResponse]

  def getBankAccount(id: String): Future[GetBankAccountResponse] =
    (bank ? GetBankAccount(id)).mapTo[GetBankAccountResponse]

  def updateAccountBalance(id: String, request: BankAccountUpdateRequest): Future[BankAccountBalanceUpdatedResponse] =
    (bank ? request.toCommand(id)).mapTo[BankAccountBalanceUpdatedResponse]

  def validateRequest[R: Validator](request: R)(routeIfValid: Route): Route = {
    validateEntity(request) match {
      case Validated.Valid(_) => routeIfValid
      case Validated.Invalid(failures) =>
        complete(StatusCodes.BadRequest, FailureResponse(failures.toList.map(_.errorMessage).mkString(", ")))
    }
  }

  val routes: Route =
    pathPrefix("bank") {
      concat(
        pathEndOrSingleSlash {
          post {
            entity(as[BankAccountCreationRequest]) { request =>
              validateRequest(request) {
                onSuccess(createBankAccount(request)) {
                  case BankAccountCreatedResponse(id) =>
                    respondWithHeader(Location(s"/bank/$id")) {
                      complete(StatusCodes.Created)
                    }
                }
              }
            }
          }
        },
        path(Segment) { id =>
          concat(
            get {
              onSuccess(getBankAccount(id)) {
                case GetBankAccountResponse(Some(account)) =>
                  complete(account)

                case GetBankAccountResponse(None) =>
                  complete(StatusCodes.NotFound, FailureResponse(s"Bank account $id cannot be found"))
              }
            },
            put {
              entity(as[BankAccountUpdateRequest]) { request =>
                // TODO: Add validation!
                validateRequest(request) {
                  onSuccess(updateAccountBalance(id, request)) {
                    case BankAccountBalanceUpdatedResponse(Success(account)) =>
                      complete(account)

                    case BankAccountBalanceUpdatedResponse(Failure(exception)) =>
                      complete(StatusCodes.BadRequest, FailureResponse(exception.getMessage))
                  }
                }
              }
            }
          )
        },
      )
    }
}
