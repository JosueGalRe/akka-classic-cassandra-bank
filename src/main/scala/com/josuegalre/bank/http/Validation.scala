package com.josuegalre.bank.http

import cats.data.ValidatedNel
import cats.implicits._

object Validation {

  trait Required[A] extends (A => Boolean)

  trait Minimum[A] extends ((A, Double) => Boolean)

  trait MinimumAbs[A] extends ((A, Double) => Boolean)

  implicit val requiredString: Required[String] = _.nonEmpty
  implicit val minimumInt: Minimum[Int] = _ >= _
  implicit val minimumDouble: Minimum[Double] = _ >= _
  implicit val minimumIntAbs: MinimumAbs[Int] = Math.abs(_) >= _
  implicit val minimumDoubleAbs: MinimumAbs[Double] = Math.abs(_) >= _

  def required[A](value: A)(implicit req: Required[A]): Boolean = req(value)

  def minimum[A](value: A, threshold: Double)(implicit min: Minimum[A]): Boolean = min(value, threshold)

  def minimumAbs[A](value: A, threshold: Double)(implicit minAbs: MinimumAbs[A]): Boolean = minAbs(value, threshold)

  trait ValidationFailure {
    def errorMessage: String
  }

  type ValidationResult[A] = ValidatedNel[ValidationFailure, A]

  case class EmptyField(fieldName: String) extends ValidationFailure {
    override def errorMessage: String = s"$fieldName is empty"
  }

  case class NegativeValue(fieldName: String) extends ValidationFailure {
    override def errorMessage: String = s"$fieldName is negative"
  }

  case class BelowMinimumValue(fieldName: String, min: Double) extends ValidationFailure {
    override def errorMessage: String = s"$fieldName is below the minimum threshold $min"
  }

  def validateMinimum[A: Minimum](value: A, threshold: Double, fieldName: String): ValidationResult[A] = {
    if (minimum(value, threshold)) value.validNel
    else if (threshold == 0) NegativeValue(fieldName).invalidNel
    else BelowMinimumValue(fieldName, threshold).invalidNel
  }

  def validateMinimumAbs[A: MinimumAbs](value: A, threshold: Double, fieldName: String): ValidationResult[A] = {
    if (minimumAbs(value, threshold)) value.validNel
    else BelowMinimumValue(fieldName, threshold).invalidNel
  }

  def validateRequired[A: Required](value: A, fieldName: String): ValidationResult[A] = {
    if (required(value)) value.validNel
    else EmptyField(fieldName).invalidNel
  }

  trait Validator[A] {
    def validate(value: A): ValidationResult[A]
  }

  def validateEntity[A](value: A)(implicit validator: Validator[A]): ValidationResult[A] = {
    validator.validate(value)
  }
}
