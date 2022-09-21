package com.josuegalre.bank.actors

import akka.actor.Props
import akka.persistence.PersistentActor

import scala.util.{Failure, Success, Try}

object PersistentBankAccount {

  // state
  case class BankAccount(id: String, user: String, currency: String, balance: Double)

  object Command {
    // commands
    case class CreateBankAccount(user: String, currency: String, initialBalance: Double)

    case class UpdateBalance(id: String, currency: String, amount: Double /*can be negative*/)

    case class GetBankAccount(id: String)
  }

  // events
  case class BankAccountCreated(bankAccount: BankAccount)

  case class BalanceUpdated(amount: Double)

  // responses
  object Response {
    case class BankAccountCreatedResponse(id: String)

    case class BankAccountBalanceUpdatedResponse(maybeBankAccount: Try[BankAccount])

    case class GetBankAccountResponse(maybeBankAccount: Option[BankAccount])
  }

  def props(id: String): Props = Props(new PersistentBankAccount(id))

}

class PersistentBankAccount(id: String) extends PersistentActor {

  import PersistentBankAccount._
  import Command._
  import Response._

  override def persistenceId: String = id

  var state: BankAccount = BankAccount(id, "", "", 0.0)

  override def receiveCommand: Receive = {
    case CreateBankAccount(user, currency, initialBalance) =>
      val id = state.id

      persist(BankAccountCreated(BankAccount(id, user, currency, initialBalance))) { _ =>
        state = state.copy(user = user, currency = currency, balance = initialBalance)
        sender() ! BankAccountCreatedResponse(id)
      }

    case UpdateBalance(_, _, amount) =>
      val newBalance = state.balance + amount

      // check her for withdrawal
      if (newBalance < 0) // illegal
        sender() ! BankAccountBalanceUpdatedResponse(Failure(new RuntimeException("Cannot withdraw more than available.")))
      else
        persist(BalanceUpdated(amount)) { _ =>
          state = state.copy(balance = newBalance)
          sender() ! BankAccountBalanceUpdatedResponse(Success(state))
        }

    case GetBankAccount(_) =>
      sender() ! GetBankAccountResponse(Some(state))
  }

  override def receiveRecover: Receive = {
    case BankAccountCreated(bankAccount) =>
      state = bankAccount

    case BalanceUpdated(amount) =>
      state = state.copy(balance = state.balance + amount)
  }
}


