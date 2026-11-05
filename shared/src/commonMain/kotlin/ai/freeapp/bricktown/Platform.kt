package ai.freeapp.bricktown

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform