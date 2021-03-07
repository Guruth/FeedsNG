package sh.weller.feedsng

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FeedsNgApplication

fun main(args: Array<String>) {
    runApplication<FeedsNgApplication>(*args)
}
