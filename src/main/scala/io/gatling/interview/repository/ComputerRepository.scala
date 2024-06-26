package io.gatling.interview.repository

import cats.effect._
import cats.implicits._

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import io.gatling.interview.model.Computer

import java.nio.file.{Files, Path, Paths}

object ComputerRepository {
  val DefaultComputersFilePath: Path = Paths.get("computers.json")

  private implicit val computersCodec: JsonValueCodec[List[Computer]] = JsonCodecMaker.make

  def apply[F[_]: Async](path: Path = DefaultComputersFilePath): ComputerRepository[F] =
    new ComputerRepository[F](path)
}

class ComputerRepository[F[_]: Async](filePath: Path) {
  import ComputerRepository._

  def fetchAll(): F[List[Computer]] = Resource
    .fromAutoCloseable(Async[F].delay(Files.newInputStream(filePath)))
    .use { stream =>
      Async[F].delay(readFromStream[List[Computer]](stream))
    }

  def fetch(id: Long): F[Computer] = {
    fetchAll()
      .map { _.find(_.id == id) }
      .flatMap {
        case Some(computer) => Async[F].pure(computer)
        case None =>
          Async[F].raiseError(new NoSuchElementException(s"Computer with id $id not found"))
      }
  }

  def insert(computer: Computer): F[Computer] = {
    for {
      currentList <-
        fetchAll()

      defaultId = 1.toLong
      newId =
        currentList
          .map { _.id }
          .maxOption
          .fold(defaultId) { id => id + 1 }

      newComputer = computer.copy(id = newId)
      updatedComputers = currentList :+ newComputer

      _ <-
        Resource
          .fromAutoCloseable(Async[F].delay(Files.newOutputStream(filePath)))
          .use { stream =>
            Async[F].delay(writeToStream(updatedComputers, stream))
          }
    } yield (newComputer)

  }
}
