package sh.weller.feedsng.feed.impl.database.impl

import io.r2dbc.spi.Row

inline fun <reified T> Row.getReified(columnName: String): T = this.get(columnName, T::class.java)!!
inline fun <reified T> Row.getReifiedOrNull(columnName: String): T? = this.get(columnName, T::class.java)

fun Row.getInt(columnName: String): Int = this.get(columnName, Integer::class.java)!!.toInt()
fun Row.getIntOrNull(columnName: String): Int? = this.get(columnName, Integer::class.java)?.toInt()

