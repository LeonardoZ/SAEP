package models.actors.dataimport

import javax.inject.Inject

import akka.actor.{Actor, Props}
import akka.event.LoggingReceive
import models.actors.dataimport.ManagerActor._
import models.entity.{DataImport, Task}
import models.service.TaskService
import play.api.Logger
import play.api.libs.concurrent.InjectedActorSupport

import scala.concurrent.Future

object ManagerActor {

  def props = Props[ManagerActor]

  case class DataRemovalOrder(yearMonth: String, userEmail: String)

  case class DataImportOrder(path: String, userEmail: String)

  case object DataRemoved

  case object DataNotRemoved

  case class StartDataImport(task: Task, path: String)

  case class FileAlreadyImported(task: Task, dataImport: DataImport)

  case class DataImportDone(task: Task)

}

class ManagerActor @Inject()(val dataImportFactory: DataImportActor.Factory,
                             val valuesFactory: ValuesManagerActor.Factory,
                             val dataRemovalFactory: DataRemovalActor.Factory,
                             val taskService: TaskService) extends Actor with InjectedActorSupport {
  import ManagerActor._
  import scala.concurrent.ExecutionContext.Implicits.global

  override def preStart(): Unit = {
    Logger.debug("Starting SAEP Data Import...")
  }

  override def receive = LoggingReceive {
    case DataImportOrder(path, userEmail) => {

      val dataImportActor = injectedChild(dataImportFactory(), "data-import-actor$" + System.nanoTime())

      generateTask(userEmail,"Importar aquivo", "Importação sendo analisada") map {
        case Some(task) => dataImportActor ! DataImportActor.CheckFileAlreadyImported(self, task, path)
        case None => None
      }
    }

    case DataRemovalOrder(yearMonth, userEmail) => {
      val dataRemovalActor = injectedChild(dataRemovalFactory(), "data-removal-actor$" + System.nanoTime())

      generateTask(userEmail,"Deletar dados", s"Iniciada remoção dos dados de $yearMonth") map {
        case Some(task) => dataRemovalActor ! DataRemovalActor.RemoveData(self, yearMonth, task)
        case None => None
      }
    }

    case DataRemoved => {

    }

    case DataNotRemoved => {

    }

    case StartDataImport(task, path) => {
      val valuesManagerActor = injectedChild(valuesFactory(), "values-manager-actor-$" + System.nanoTime())
      valuesManagerActor ! ValuesManagerActor.ReadValuesFromFile(self, task, path)
    }

    case FileAlreadyImported(task, dataImport) => {
      taskService.updateTaskSuccess(task, "Arquivo já importado no sistema.")
      Logger.debug("Nothing to do.")
    }

    case DataImportDone(task) => {
      taskService.updateTaskSuccess(task, "Arquivo importado com sucesso")
    }
  }

  def generateTask(userEmail: String, description: String, message: String): Future[Option[Task]] = {
    taskService.createTask(description, message, userEmail)
  }

}