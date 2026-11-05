package ai.freeapp.bricktown.join

import ai.freeapp.bricktown.town.TownRepository
import ai.freeapp.bricktown.town.TownResult
import ai.freeapp.bricktown.wire.InvitePreview
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The repository's two invite calls, as the join ViewModel sees them. */
internal class RepositoryInviteGateway(private val repository: TownRepository) : InviteGateway {
    override suspend fun preview(code: String): TownResult<InvitePreview> = repository.previewInvite(code)

    override suspend fun accept(code: String, colorIndex: Int, initial: String): TownResult<Unit> =
        when (val result = repository.acceptInvite(code, colorIndex, initial)) {
            is TownResult.Ok -> TownResult.Ok(Unit)
            is TownResult.Failed -> result
        }
}

/** Joining a town with an invite code (spec 5.5, task 1026). The screen passes the code it was opened with. */
val joinModule: Module = module {
    factory<InviteGateway> { RepositoryInviteGateway(get()) }
    viewModel { (code: String) -> JoinViewModel(code, get(), get()) }
}
