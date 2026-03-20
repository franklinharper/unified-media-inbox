package com.franklinharper.social.media.client.cli

import com.franklinharper.social.media.client.cli.commands.CliApp
import com.franklinharper.social.media.client.cli.commands.CliResult
import com.franklinharper.social.media.client.cli.commands.DefaultCliApp
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    val app: CliApp = DefaultCliApp()
    val result = runBlocking { app.run(args.toList()) }
    when (result) {
        is CliResult.Success -> {
            if (result.output.isNotBlank()) {
                println(result.output)
            }
        }
        is CliResult.Failure -> {
            System.err.println(result.message)
            kotlin.system.exitProcess(1)
        }
    }
}
