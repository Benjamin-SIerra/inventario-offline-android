package com.benjamin.inventario.domain

import java.math.BigInteger

fun isWholeNumberInput(value: String): Boolean = value.all(Char::isDigit)

fun parseWholeNumber(value: String): Long? =
    if (value.isEmpty() || !isWholeNumberInput(value)) null else value.toLongOrNull()

fun roundedDailyConsumption(demand: Long, lookbackDays: Int): Long {
    require(demand >= 0L) { "La demanda no puede ser negativa" }
    require(lookbackDays > 0) { "El periodo debe ser mayor que cero" }
    return ceilRatio(BigInteger.valueOf(demand), BigInteger.valueOf(lookbackDays.toLong()))
}

fun roundedDemandForecast(demand: Long, lookbackDays: Int, forecastDays: Int): Long {
    require(demand >= 0L) { "La demanda no puede ser negativa" }
    require(lookbackDays > 0 && forecastDays >= 0) { "El periodo de pronóstico es inválido" }
    val numerator = BigInteger.valueOf(demand).multiply(BigInteger.valueOf(forecastDays.toLong()))
    return ceilRatio(numerator, BigInteger.valueOf(lookbackDays.toLong()))
}

fun suggestedOrderQuantity(stock: Long, target: Long, forecast: Long): Long {
    require(stock >= 0L && target >= 0L && forecast >= 0L) { "Las cantidades no pueden ser negativas" }
    val result = BigInteger.valueOf(target)
        .subtract(BigInteger.valueOf(stock))
        .add(BigInteger.valueOf(forecast))
        .max(BigInteger.ZERO)
    return result.min(BigInteger.valueOf(Long.MAX_VALUE)).toLong()
}

private fun ceilRatio(numerator: BigInteger, denominator: BigInteger): Long =
    numerator.add(denominator).subtract(BigInteger.ONE)
        .divide(denominator)
        .min(BigInteger.valueOf(Long.MAX_VALUE))
        .toLong()
