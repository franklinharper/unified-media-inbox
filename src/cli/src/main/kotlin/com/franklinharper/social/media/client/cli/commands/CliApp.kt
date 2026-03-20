package com.franklinharper.social.media.client.cli.commands

interface CliApp {
    suspend fun run(args: List<String>): CliResult
}

sealed interface CliResult {
    data class Success(val output: String = "") : CliResult
    data class Failure(val message: String) : CliResult
}
