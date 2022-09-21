package com.josuegalre.bank.actors

import akka.actor.ActorRef
import akka.persistence.PersistentActor

import java.util.UUID
import scala.util.Failure

object Bank {
  // events
  case class BankAccountCreated(id: String)

  // state
  case class BankState(accounts: Map[String, ActorRef])
}


class Bank extends PersistentActor {

  import Bank._
  // commands
  import PersistentBankAccount.Command._
  import PersistentBankAccount.Response._

  var state: BankState = BankState(Map())

  override def persistenceId: String = "persistent-bank"

  override def receiveCommand: Receive = {
    case createCommand@CreateBankAccount(_, _, _) =>
      val id = UUID.randomUUID().toString
      val newBankAccount = context.actorOf(PersistentBankAccount.props(id), id)

      persist(BankAccountCreated(id)) { _ =>
        state = state.copy(state.accounts + (id -> newBankAccount))
        newBankAccount.forward(createCommand)
      }

    case updateCommand@UpdateBalance(id, _, _) =>
      state.accounts.get(id) match {
        case Some(account) =>
          account.forward(updateCommand)

        case None =>
          sender() ! BankAccountBalanceUpdatedResponse(Failure(new RuntimeException("Bank account cannot be found")))
      }

    case getCommand@GetBankAccount(id) =>
      state.accounts.get(id) match {
        case Some(account) =>
          account.forward(getCommand)

        case None =>
          sender() ! GetBankAccountResponse(None)
      }

  }

  override def receiveRecover: Receive = {
    case BankAccountCreated(id) =>
      val account = context.child(id)
        .getOrElse(context.actorOf(PersistentBankAccount.props(id), id))

      state = state.copy(state.accounts + (id -> account))
  }
}

//
//object BankPlayground {
//  import PersistentBankAccount.Command._
//  import PersistentBankAccount.Response._
//
//  def main(args: Array[String]): Unit = {
//    implicit val system: ActorSystem = ActorSystem("BankPlayground")
//    implicit val timeout: Timeout = Timeout(2.seconds)
//    implicit val scheduler: ExecutionContext = system.dispatcher
//
//    val bank = system.actorOf(Props[Bank], "bank")
//
//    class SimpleActor extends Actor with ActorLogging {
//      override def receive: Receive = {
//        case BankAccountCreatedResponse(id) =>
//          log.info(s"successfully created bank account $id")
//
//        case GetBankAccountResponse(maybeBankAccount) =>
//          log.info(s"Account details: $maybeBankAccount")
//      }
//    }
//
//    val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")
//
//    (bank ? CreateBankAccount("Bryan", "USD", 10)).pipeTo(simpleActor)
////    (bank ? GetBankAccount("86f34194-e33c-4126-a42e-131f7541a1c9")).pipeTo(simpleActor)
//  }
//}