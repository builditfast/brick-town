package ai.freeapp.bricktown.invite

import ai.freeapp.bricktown.town.TownLoader
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Inviting a partner (6g). */
val inviteModule: Module = module {
    viewModel { InviteViewModel(get(), refresh = get<TownLoader>()::refresh) }
}
