package com.benjamin.inventario.domain

fun matchesProductSearch(name:String,code:String,query:String):Boolean =
    query.isBlank() || name.contains(query.trim(),ignoreCase=true) || code.contains(query.trim(),ignoreCase=true)

fun isLowStock(stock:Long,minimum:Long):Boolean = stock<=minimum

fun isValidMultiProductIssue(lines:List<Pair<Long,Long>>,available:Map<Long,Long>):Boolean =
    lines.isNotEmpty() && lines.all{it.second>0L} && lines.map{it.first}.distinct().size==lines.size &&
        lines.all{(productId,quantity)->(available[productId]?:-1L)>=quantity}
